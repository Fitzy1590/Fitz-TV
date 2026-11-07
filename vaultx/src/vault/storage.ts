import { Directory, File, Paths } from 'expo-file-system';
import type { SlotId } from '../crypto/keys';

/** <documents>/vault_<slot>/  — encrypted blobs, named by random UUID only. */
export function vaultDir(slot: SlotId): Directory {
  const d = new Directory(Paths.document, `vault_${slot}`);
  if (!d.exists) d.create({ intermediates: true });
  return d;
}

/** <cache>/vx_scratch/ — the ONLY place plaintext ever touches disk. Wiped on lock. */
export function scratchDir(): Directory {
  const d = new Directory(Paths.cache, 'vx_scratch');
  if (!d.exists) d.create({ intermediates: true });
  return d;
}

export function blobFile(slot: SlotId, name: string): File {
  return new File(vaultDir(slot), name);
}

export function wipeScratch() {
  try {
    const d = new Directory(Paths.cache, 'vx_scratch');
    if (d.exists) d.delete();
  } catch (e) {
    console.warn('[storage] scratch wipe failed', e);
  }
  // expo-image-picker copies picks into cache/ImagePicker; remove those too.
  try {
    const p = new Directory(Paths.cache, 'ImagePicker');
    if (p.exists) p.delete();
  } catch {}
}
