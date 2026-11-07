/**
 * Chunked AES-256-GCM file format (".vault").
 *
 *   header (16 bytes):  "VLX1" | chunkSize u32 BE | baseNonce (8 random bytes)
 *   chunks:             ciphertext (<= chunkSize) + 16-byte GCM tag, repeated
 *
 * Nonce for chunk i = baseNonce || u32BE(i). AAD = header || finalFlag(1 byte),
 * so chunks cannot be reordered, dropped, or truncated without detection.
 * Chunking lets us decrypt a range of a large video without loading it all.
 */
import { gcm } from '@noble/ciphers/aes.js';
import { File, FileMode } from 'expo-file-system';
import { randomBytes } from './random';

const MAGIC = [0x56, 0x4c, 0x58, 0x31]; // "VLX1"
export const HEADER_LEN = 16;
export const TAG_LEN = 16;
export const DEFAULT_CHUNK = 1024 * 1024;

function nonceFor(base: Uint8Array, i: number): Uint8Array {
  const n = new Uint8Array(12);
  n.set(base, 0);
  new DataView(n.buffer).setUint32(8, i, false);
  return n;
}

function aad(header: Uint8Array, final: boolean): Uint8Array {
  const a = new Uint8Array(HEADER_LEN + 1);
  a.set(header, 0);
  a[HEADER_LEN] = final ? 1 : 0;
  return a;
}

function makeHeader(chunkSize: number): Uint8Array {
  const h = new Uint8Array(HEADER_LEN);
  h.set(MAGIC, 0);
  new DataView(h.buffer).setUint32(4, chunkSize, false);
  h.set(randomBytes(8), 8);
  return h;
}

function parseHeader(h: Uint8Array): { chunkSize: number; base: Uint8Array } {
  for (let i = 0; i < 4; i++) if (h[i] !== MAGIC[i]) throw new Error('Not a VaultX file');
  return { chunkSize: new DataView(h.buffer, h.byteOffset).getUint32(4, false), base: h.slice(8, 16) };
}

/** Encrypts `src` into `dst`, streaming one chunk at a time. */
export async function encryptFile(
  key: Uint8Array,
  src: File,
  dst: File,
  onProgress?: (fraction: number) => void,
  chunkSize = DEFAULT_CHUNK,
): Promise<number> {
  const total = src.size ?? 0;
  const header = makeHeader(chunkSize);
  const base = header.slice(8, 16);
  if (dst.exists) dst.delete();
  dst.create();
  const out = dst.open(FileMode.ReadWrite);
  const inp = src.open(FileMode.ReadOnly);
  let read = 0;
  try {
    out.writeBytesSync(header);
    // Read one chunk ahead so we know which chunk is final without trusting
    // the reported size. An empty file still gets one empty, final chunk.
    let i = 0;
    let cur = inp.readBytesSync(chunkSize);
    while (true) {
      const next = cur.length === chunkSize ? inp.readBytesSync(chunkSize) : new Uint8Array(0);
      const final = next.length === 0;
      read += cur.length;
      out.writeBytesSync(gcm(key, nonceFor(base, i), aad(header, final)).encrypt(cur));
      i++;
      onProgress?.(total ? Math.min(1, read / total) : 1);
      if (final) break;
      cur = next;
    }
  } finally {
    inp.close();
    out.close();
  }
  return read;
}

/** Encrypts an in-memory buffer (thumbnails, small metadata). */
export function encryptBytes(key: Uint8Array, plain: Uint8Array): Uint8Array {
  const header = makeHeader(Math.max(plain.length, 1));
  const ct = gcm(key, nonceFor(header.slice(8, 16), 0), aad(header, true)).encrypt(plain);
  const out = new Uint8Array(HEADER_LEN + ct.length);
  out.set(header, 0);
  out.set(ct, HEADER_LEN);
  return out;
}

export function decryptBytes(key: Uint8Array, blob: Uint8Array): Uint8Array {
  const header = blob.slice(0, HEADER_LEN);
  const { base } = parseHeader(header);
  return gcm(key, nonceFor(base, 0), aad(header, true)).decrypt(blob.slice(HEADER_LEN));
}

/** Decrypts a whole .vault file into memory. Use for images. */
export async function decryptFileToMemory(key: Uint8Array, src: File): Promise<Uint8Array> {
  const parts: Uint8Array[] = [];
  let len = 0;
  for await (const p of decryptChunks(key, src)) {
    parts.push(p);
    len += p.length;
  }
  const out = new Uint8Array(len);
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}

/** Yields decrypted chunks in order; throws if any chunk was tampered with. */
export async function* decryptChunks(key: Uint8Array, src: File): AsyncGenerator<Uint8Array> {
  const size = src.size ?? 0;
  const h = src.open(FileMode.ReadOnly);
  try {
    const header = h.readBytesSync(HEADER_LEN);
    const { chunkSize, base } = parseHeader(header);
    let pos = HEADER_LEN;
    let i = 0;
    while (pos < size) {
      const ct = h.readBytesSync(chunkSize + TAG_LEN);
      pos += ct.length;
      const final = pos >= size;
      yield gcm(key, nonceFor(base, i), aad(header, final)).decrypt(ct);
      i++;
    }
  } finally {
    h.close();
  }
}

/**
 * Decrypts to a short-lived plaintext file in app-private cache for the video
 * player. The caller MUST delete it when playback ends; `wipeScratch()` in
 * security/session.ts also deletes everything there on lock/background.
 */
export async function decryptFileToScratch(key: Uint8Array, src: File, dst: File): Promise<File> {
  if (dst.exists) dst.delete();
  dst.create();
  const out = dst.open(FileMode.ReadWrite);
  try {
    for await (const p of decryptChunks(key, src)) out.writeBytesSync(p);
  } finally {
    out.close();
  }
  return dst;
}
