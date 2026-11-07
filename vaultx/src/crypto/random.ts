import * as ExpoCrypto from 'expo-crypto';

export function randomBytes(n: number): Uint8Array {
  return ExpoCrypto.getRandomBytes(n);
}

export function randomId(): string {
  return ExpoCrypto.randomUUID();
}

export function toHex(b: Uint8Array): string {
  let s = '';
  for (let i = 0; i < b.length; i++) s += b[i].toString(16).padStart(2, '0');
  return s;
}

export function fromHex(h: string): Uint8Array {
  const out = new Uint8Array(h.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(h.substr(i * 2, 2), 16);
  return out;
}

/** Best-effort zeroisation of key material held in JS memory. */
export function wipe(b: Uint8Array | null | undefined) {
  if (b) b.fill(0);
}

/** Constant-time comparison for equal-length byte arrays. */
export function equalBytes(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a[i] ^ b[i];
  return d === 0;
}
