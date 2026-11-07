import { create } from 'zustand';
import type { SlotId } from '../crypto/keys';
import { subKey } from '../crypto/keys';
import { closeVaultDb, getConfig, openVaultDb, type VaultConfig } from '../db/database';
import { wipeScratch } from '../vault/storage';

interface SessionState {
  slot: SlotId | null;
  dek: Uint8Array | null;
  fileKey: Uint8Array | null;
  config: VaultConfig | null;
  unlock: (slot: SlotId, dek: Uint8Array) => Promise<void>;
  lock: () => Promise<void>;
  refreshConfig: () => Promise<void>;
}

export const useSession = create<SessionState>((set, get) => ({
  slot: null,
  dek: null,
  fileKey: null,
  config: null,

  async unlock(slot, dek) {
    await openVaultDb(slot, await subKey(dek, 'vaultx/db'));
    const fileKey = await subKey(dek, 'vaultx/files');
    set({ slot, dek, fileKey, config: await getConfig() });
  },

  async lock() {
    const { dek, fileKey } = get();
    dek?.fill(0);
    fileKey?.fill(0);
    set({ slot: null, dek: null, fileKey: null, config: null });
    await closeVaultDb();
    wipeScratch();
  },

  async refreshConfig() {
    set({ config: await getConfig() });
  },
}));
