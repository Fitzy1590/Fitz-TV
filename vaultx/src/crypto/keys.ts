/**
 * Key management.
 *
 * Every install has exactly two "key slots". Each slot wraps its own random
 * 256-bit data-encryption key (DEK) with a key derived from a PIN via Argon2id.
 * One slot is the real vault, the other is the decoy vault. If the user never
 * configures a decoy PIN, slot B is filled with a key wrapped under a random,
 * unknowable PIN, so storage always looks the same and nothing on disk says
 * which slot is "real".
 *
 * Unlocking always runs Argon2id for BOTH slots so the time taken does not
 * reveal which PIN was entered.
 *
 * Wrapped slots live in expo-secure-store, which on Android is backed by the
 * hardware Android Keystore — so the wrapped blobs are themselves encrypted by
 * a non-exportable device key.
 */
import * as SecureStore from 'expo-secure-store';
import { argon2idAsync } from '@noble/hashes/argon2.js';
import { gcm } from '@noble/ciphers/aes.js';
import { randomBytes, toHex, fromHex, wipe } from './random';

export type SlotId = 'A' | 'B';

const SLOT_KEY = (s: SlotId) => `vx.slot.${s}`;
const BIO_KEY = (s: SlotId) => `vx.bio.${s}`;
const ATTEMPTS_KEY = 'vx.attempts';

/** OWASP-recommended floor for Argon2id: 19 MiB, t=2, p=1. */
const ARGON = { t: 2, m: 19 * 1024, p: 1, dkLen: 32 } as const;

const STORE_OPTS: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
};

interface WrappedSlot {
  v: 1;
  salt: string; // hex, 16 bytes
  nonce: string; // hex, 12 bytes
  ct: string; // hex, wrapped DEK + GCM tag
}

async function deriveKek(pin: string, salt: Uint8Array): Promise<Uint8Array> {
  return argon2idAsync(new TextEncoder().encode(pin), salt, ARGON);
}

async function wrap(pin: string, dek: Uint8Array): Promise<WrappedSlot> {
  const salt = randomBytes(16);
  const nonce = randomBytes(12);
  const kek = await deriveKek(pin, salt);
  const ct = gcm(kek, nonce, new TextEncoder().encode('vaultx-slot-v1')).encrypt(dek);
  wipe(kek);
  return { v: 1, salt: toHex(salt), nonce: toHex(nonce), ct: toHex(ct) };
}

async function tryUnwrap(pin: string, slot: WrappedSlot): Promise<Uint8Array | null> {
  const kek = await deriveKek(pin, fromHex(slot.salt));
  try {
    return gcm(kek, fromHex(slot.nonce), new TextEncoder().encode('vaultx-slot-v1')).decrypt(
      fromHex(slot.ct),
    );
  } catch {
    return null; // authentication failure => wrong PIN for this slot
  } finally {
    wipe(kek);
  }
}

async function readSlot(s: SlotId): Promise<WrappedSlot | null> {
  const raw = await SecureStore.getItemAsync(SLOT_KEY(s), STORE_OPTS);
  return raw ? (JSON.parse(raw) as WrappedSlot) : null;
}

export async function isInitialised(): Promise<boolean> {
  return (await readSlot('A')) !== null;
}

/** First-run setup. `decoyPin` may be omitted; slot B is then unreachable filler. */
export async function initialiseVaults(realPin: string, decoyPin?: string) {
  if (decoyPin && decoyPin === realPin) throw new Error('Decoy PIN must differ from your real PIN.');
  const dekA = randomBytes(32);
  const dekB = randomBytes(32);
  const slotA = await wrap(realPin, dekA);
  const slotB = await wrap(decoyPin ?? toHex(randomBytes(32)), dekB);
  await SecureStore.setItemAsync(SLOT_KEY('A'), JSON.stringify(slotA), STORE_OPTS);
  await SecureStore.setItemAsync(SLOT_KEY('B'), JSON.stringify(slotB), STORE_OPTS);
  wipe(dekA);
  wipe(dekB);
}

export interface Unlocked {
  slot: SlotId;
  dek: Uint8Array;
}

/** Tries both slots, always doing the full work for each. */
export async function unlockWithPin(pin: string): Promise<Unlocked | null> {
  await enforceBackoff();
  const [a, b] = await Promise.all([readSlot('A'), readSlot('B')]);
  if (!a || !b) throw new Error('Vault is not set up.');
  const ra = await tryUnwrap(pin, a);
  const rb = await tryUnwrap(pin, b);
  if (ra) {
    wipe(rb);
    await resetAttempts();
    return { slot: 'A', dek: ra };
  }
  if (rb) {
    await resetAttempts();
    return { slot: 'B', dek: rb };
  }
  await recordFailedAttempt();
  return null;
}

/** Re-wraps one slot's DEK under a new PIN (called while that slot is unlocked). */
export async function changePin(slot: SlotId, dek: Uint8Array, newPin: string) {
  const w = await wrap(newPin, dek);
  await SecureStore.setItemAsync(SLOT_KEY(slot), JSON.stringify(w), STORE_OPTS);
}

/** Replace slot B with a fresh empty decoy vault reachable by `pin`. */
export async function setDecoyPin(pin: string) {
  const dek = randomBytes(32);
  await changePin('B', dek, pin);
  wipe(dek);
}

// ---------- Biometrics ----------
// The DEK is stored a second time in SecureStore with requireAuthentication,
// which binds it to a Keystore key that only releases after a fresh
// fingerprint/face match. It is only ever saved for the slot the user enabled
// it on, so biometrics can never reveal that a second slot exists.

export async function enableBiometric(slot: SlotId, dek: Uint8Array) {
  await SecureStore.setItemAsync(BIO_KEY(slot), toHex(dek), {
    ...STORE_OPTS,
    requireAuthentication: true,
    authenticationPrompt: 'Unlock VaultX',
  });
}

export async function disableBiometric(slot: SlotId) {
  await SecureStore.deleteItemAsync(BIO_KEY(slot), STORE_OPTS);
}

export async function unlockWithBiometric(): Promise<Unlocked | null> {
  for (const slot of ['A', 'B'] as SlotId[]) {
    try {
      const hex = await SecureStore.getItemAsync(BIO_KEY(slot), {
        ...STORE_OPTS,
        requireAuthentication: true,
        authenticationPrompt: 'Unlock VaultX',
      });
      if (hex) return { slot, dek: fromHex(hex) };
    } catch {
      return null; // user cancelled or biometric failed
    }
  }
  return null;
}

// ---------- Brute-force throttling ----------

interface Attempts {
  count: number;
  lockedUntil: number;
}

async function readAttempts(): Promise<Attempts> {
  const raw = await SecureStore.getItemAsync(ATTEMPTS_KEY, STORE_OPTS);
  return raw ? JSON.parse(raw) : { count: 0, lockedUntil: 0 };
}

async function enforceBackoff() {
  const a = await readAttempts();
  const wait = a.lockedUntil - Date.now();
  if (wait > 0) throw new LockoutError(wait);
}

async function recordFailedAttempt() {
  const a = await readAttempts();
  a.count += 1;
  // 5 free tries, then 30s, 60s, 120s ... capped at 1 hour.
  if (a.count >= 5) a.lockedUntil = Date.now() + Math.min(3_600_000, 30_000 * 2 ** (a.count - 5));
  await SecureStore.setItemAsync(ATTEMPTS_KEY, JSON.stringify(a), STORE_OPTS);
}

async function resetAttempts() {
  await SecureStore.setItemAsync(ATTEMPTS_KEY, JSON.stringify({ count: 0, lockedUntil: 0 }), STORE_OPTS);
}

export class LockoutError extends Error {
  constructor(public readonly waitMs: number) {
    super(`Too many attempts. Try again in ${Math.ceil(waitMs / 1000)}s.`);
  }
}

/** Sub-key derivation so the DB key and file keys are never the same bytes. */
export async function subKey(dek: Uint8Array, label: string): Promise<Uint8Array> {
  const { hkdf } = await import('@noble/hashes/hkdf.js');
  const { sha256 } = await import('@noble/hashes/sha2.js');
  return hkdf(sha256, dek, undefined, new TextEncoder().encode(label), 32);
}
