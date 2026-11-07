import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import * as VideoThumbnails from 'expo-video-thumbnails';
import { File } from 'expo-file-system';
import { encryptBytes, encryptFile } from '../crypto/blob';
import { randomId } from '../crypto/random';
import { subKey, type SlotId } from '../crypto/keys';
import { classifyImage } from '../ml/classifier';
import { insertItem, type VaultItem } from '../db/database';
import { blobFile } from './storage';

export interface ImportProgress {
  index: number;
  total: number;
  name: string;
  fraction: number;
}

export async function pickMedia(): Promise<ImagePicker.ImagePickerAsset[]> {
  const res = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ['images', 'videos'],
    allowsMultipleSelection: true,
    exif: false, // never read EXIF/location into JS
    quality: 1,
    videoExportPreset: ImagePicker.VideoExportPreset.Passthrough,
  });
  return res.canceled ? [] : res.assets;
}

async function makeThumb(asset: ImagePicker.ImagePickerAsset): Promise<string> {
  const src =
    asset.type === 'video'
      ? (await VideoThumbnails.getThumbnailAsync(asset.uri, { time: 1000, quality: 0.7 })).uri
      : asset.uri;
  // Re-encoding through the manipulator strips all EXIF / GPS metadata.
  const t = await ImageManipulator.manipulateAsync(src, [{ resize: { width: 360 } }], {
    compress: 0.7,
    format: ImageManipulator.SaveFormat.JPEG,
  });
  return t.uri;
}

export async function importAssets(
  slot: SlotId,
  dek: Uint8Array,
  assets: ImagePicker.ImagePickerAsset[],
  blurThreshold: number,
  onProgress: (p: ImportProgress) => void,
): Promise<number> {
  const fileKey = await subKey(dek, 'vaultx/files');
  let ok = 0;
  for (let i = 0; i < assets.length; i++) {
    const a = assets[i];
    const name = a.fileName ?? `item-${i}`;
    const tmp: File[] = [];
    try {
      const thumbUri = await makeThumb(a);
      const thumbFile = new File(thumbUri);
      tmp.push(thumbFile);

      const cls = await classifyImage(thumbUri, blurThreshold);

      const id = randomId();
      const blobName = `${randomId()}.vault`;
      const thumbName = `${randomId()}.vault`;
      const src = new File(a.uri);
      tmp.push(src);
      const size = await encryptFile(fileKey, src, blobFile(slot, blobName), (f) =>
        onProgress({ index: i, total: assets.length, name, fraction: f }),
      );
      blobFile(slot, thumbName).write(encryptBytes(fileKey, await thumbFile.bytes()));

      const ext = (name.split('.').pop() ?? (a.type === 'video' ? 'mp4' : 'jpg')).toLowerCase();
      const item: VaultItem = {
        id,
        encrypted_blob_path: blobName,
        encrypted_thumb_path: thumbName,
        original_name: name,
        original_file_type: ext,
        mime_type: a.mimeType ?? (a.type === 'video' ? 'video/mp4' : 'image/jpeg'),
        file_size_bytes: size,
        width: a.width ?? null,
        height: a.height ?? null,
        duration_ms: a.duration ?? null,
        nsfw_score: cls?.nsfwScore ?? 0,
        category_tag: cls?.category ?? 'unscanned',
        tags_json: JSON.stringify(cls?.scores ?? {}),
        album_id: null,
        is_favorite: 0,
        is_decoy: 0,
        original_taken_at: null,
        created_at: new Date().toISOString(),
      };
      await insertItem(item);
      ok++;
    } catch (e) {
      console.warn('[import] failed', name, e);
    } finally {
      // Delete the picker's plaintext cache copies immediately.
      for (const f of tmp) {
        try {
          if (f.exists && f.uri.includes('/cache/')) f.delete();
        } catch {}
      }
    }
  }
  fileKey.fill(0);
  return ok;
}
