import { File } from 'expo-file-system';
import { decryptBytes, decryptFileToMemory, decryptFileToScratch } from '../crypto/blob';
import { randomId } from '../crypto/random';
import type { SlotId } from '../crypto/keys';
import { deleteItemRow, type VaultItem } from '../db/database';
import { dataUri } from '../ui/base64';
import { blobFile, scratchDir } from './storage';

// In-memory thumbnail cache for the current session only. Cleared on lock.
const thumbCache = new Map<string, string>();

export function clearThumbCache() {
  thumbCache.clear();
}

export async function loadThumb(slot: SlotId, key: Uint8Array, item: VaultItem): Promise<string | null> {
  if (!item.encrypted_thumb_path) return null;
  const hit = thumbCache.get(item.id);
  if (hit) return hit;
  const blob = await blobFile(slot, item.encrypted_thumb_path).bytes();
  const uri = dataUri('image/jpeg', decryptBytes(key, blob));
  thumbCache.set(item.id, uri);
  return uri;
}

export async function loadImage(slot: SlotId, key: Uint8Array, item: VaultItem): Promise<string> {
  const bytes = await decryptFileToMemory(key, blobFile(slot, item.encrypted_blob_path));
  return dataUri(item.mime_type || 'image/jpeg', bytes);
}

/** Returns a plaintext scratch file for the video player. Delete it with `releaseVideo`. */
export async function loadVideo(slot: SlotId, key: Uint8Array, item: VaultItem): Promise<File> {
  const dst = new File(scratchDir(), `${randomId()}.${item.original_file_type || 'mp4'}`);
  return decryptFileToScratch(key, blobFile(slot, item.encrypted_blob_path), dst);
}

export function releaseVideo(f: File | null) {
  try {
    if (f?.exists) f.delete();
  } catch {}
}

export async function deleteItem(slot: SlotId, item: VaultItem) {
  for (const name of [item.encrypted_blob_path, item.encrypted_thumb_path]) {
    if (!name) continue;
    try {
      const f = blobFile(slot, name);
      if (f.exists) f.delete();
    } catch {}
  }
  thumbCache.delete(item.id);
  await deleteItemRow(item.id);
}
