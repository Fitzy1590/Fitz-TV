/**
 * Each key slot gets its own SQLCipher database file, keyed with a sub-key of
 * that slot's DEK. The decoy vault is therefore a completely separate,
 * independently encrypted database — there is no `is_decoy` flag anywhere
 * that an examiner could query. (The spec's `is_decoy` column is kept for
 * schema compatibility but is always 0.)
 */
import * as SQLite from 'expo-sqlite';
import { toHex } from '../crypto/random';
import type { SlotId } from '../crypto/keys';

export type CategoryTag = 'safe' | 'suggestive' | 'explicit' | 'unscanned';

export interface VaultItem {
  id: string;
  encrypted_blob_path: string;
  encrypted_thumb_path: string | null;
  original_name: string;
  original_file_type: string;
  mime_type: string;
  file_size_bytes: number;
  width: number | null;
  height: number | null;
  duration_ms: number | null;
  nsfw_score: number;
  category_tag: CategoryTag;
  tags_json: string; // raw classifier scores {label: prob}
  album_id: string | null;
  is_favorite: number;
  is_decoy: number;
  original_taken_at: string | null;
  created_at: string;
}

export interface VaultConfig {
  biometric_enabled: number;
  auto_blur_threshold: number;
  auto_lock_seconds: number;
  blur_all_thumbnails: number;
  lock_on_background: number;
}

const SCHEMA = `
CREATE TABLE IF NOT EXISTS vault_config (
  id TEXT PRIMARY KEY,
  biometric_enabled INTEGER DEFAULT 0,
  auto_blur_threshold REAL DEFAULT 0.70,
  auto_lock_seconds INTEGER DEFAULT 30,
  blur_all_thumbnails INTEGER DEFAULT 1,
  lock_on_background INTEGER DEFAULT 1
);
INSERT OR IGNORE INTO vault_config (id) VALUES ('main');

CREATE TABLE IF NOT EXISTS albums (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vault_items (
  id TEXT PRIMARY KEY,
  encrypted_blob_path TEXT NOT NULL,
  encrypted_thumb_path TEXT,
  original_name TEXT NOT NULL,
  original_file_type TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  file_size_bytes INTEGER NOT NULL,
  width INTEGER,
  height INTEGER,
  duration_ms INTEGER,
  nsfw_score REAL DEFAULT 0.0,
  category_tag TEXT DEFAULT 'unscanned',
  tags_json TEXT DEFAULT '{}',
  album_id TEXT REFERENCES albums(id) ON DELETE SET NULL,
  is_favorite INTEGER DEFAULT 0,
  is_decoy INTEGER DEFAULT 0,
  original_taken_at TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_items_cat ON vault_items(category_tag);
CREATE INDEX IF NOT EXISTS idx_items_created ON vault_items(created_at DESC);
`;

let db: SQLite.SQLiteDatabase | null = null;

export async function openVaultDb(slot: SlotId, dbKey: Uint8Array) {
  await closeVaultDb();
  const d = await SQLite.openDatabaseAsync(`vault_${slot}.db`);
  // Raw-key form skips SQLCipher's own (weaker) PBKDF2 since we already did Argon2id.
  await d.execAsync(`PRAGMA key = "x'${toHex(dbKey)}'";`);
  await d.execAsync('PRAGMA foreign_keys = ON; PRAGMA secure_delete = ON;');
  await d.execAsync(SCHEMA);
  db = d;
  return d;
}

export async function closeVaultDb() {
  if (db) {
    try {
      await db.closeAsync();
    } catch {}
    db = null;
  }
}

function get() {
  if (!db) throw new Error('Vault is locked');
  return db;
}

export async function insertItem(item: VaultItem) {
  const cols = Object.keys(item);
  await get().runAsync(
    `INSERT INTO vault_items (${cols.join(',')}) VALUES (${cols.map(() => '?').join(',')})`,
    Object.values(item) as SQLite.SQLiteBindValue[],
  );
}

export interface ItemQuery {
  category?: CategoryTag | 'all';
  favoritesOnly?: boolean;
  albumId?: string | null;
  search?: string;
}

export async function listItems(q: ItemQuery = {}): Promise<VaultItem[]> {
  const where: string[] = [];
  const args: SQLite.SQLiteBindValue[] = [];
  if (q.category && q.category !== 'all') {
    where.push('category_tag = ?');
    args.push(q.category);
  }
  if (q.favoritesOnly) where.push('is_favorite = 1');
  if (q.albumId) {
    where.push('album_id = ?');
    args.push(q.albumId);
  }
  if (q.search) {
    where.push('original_name LIKE ?');
    args.push(`%${q.search}%`);
  }
  const sql = `SELECT * FROM vault_items ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY created_at DESC`;
  return get().getAllAsync<VaultItem>(sql, args);
}

export async function updateItem(id: string, patch: Partial<VaultItem>) {
  const keys = Object.keys(patch);
  if (!keys.length) return;
  await get().runAsync(
    `UPDATE vault_items SET ${keys.map((k) => `${k} = ?`).join(', ')} WHERE id = ?`,
    [...(Object.values(patch) as SQLite.SQLiteBindValue[]), id],
  );
}

export async function deleteItemRow(id: string) {
  await get().runAsync('DELETE FROM vault_items WHERE id = ?', [id]);
}

export async function getConfig(): Promise<VaultConfig> {
  return (await get().getFirstAsync<VaultConfig>("SELECT * FROM vault_config WHERE id = 'main'"))!;
}

export async function setConfig(patch: Partial<VaultConfig>) {
  const keys = Object.keys(patch);
  await get().runAsync(
    `UPDATE vault_config SET ${keys.map((k) => `${k} = ?`).join(', ')} WHERE id = 'main'`,
    Object.values(patch) as SQLite.SQLiteBindValue[],
  );
}

export async function stats() {
  return get().getAllAsync<{ category_tag: CategoryTag; n: number; bytes: number }>(
    'SELECT category_tag, COUNT(*) AS n, SUM(file_size_bytes) AS bytes FROM vault_items GROUP BY category_tag',
  );
}
