# VaultX

A private, offline media vault for Android. Photos and videos you add are encrypted on the phone, sorted on the phone, and never uploaded anywhere. The app makes no network requests.

## Features

- **PIN unlock** (6–8 digits) with fingerprint unlock as an option.
- **Decoy vault**: a second PIN opens a separate vault. Nothing stored on the phone shows which vault is the real one, and both PINs take the same time to check.
- **Brute-force lockout**: 5 free attempts, then waits that double each time (30 s, 1 min, 2 min … up to 1 h).
- **Encryption at rest**: every file is encrypted in 1 MiB chunks, each one sealed so tampering or truncation is detected. Filenames, sizes, tags and settings sit in an encrypted database, one per vault.
- **Metadata stripping**: thumbnails are re-encoded, so location and camera data are removed. EXIF is never read into the app.
- **On-device sorting**: an optional ONNX model tags items as safe, suggestive or explicit. Without a model, items are left unsorted.
- **Blur**: thumbnails are blurred by default. Press and hold to peek, or blur only items above a sensitivity level.
- **Screen privacy**: screenshots and screen recording are blocked, and the app is hidden in the app switcher.
- **Auto-lock**: locks when you leave the app and after a chosen idle time. Every lock wipes key material from memory and deletes any temporary decrypted files.

## How it works

| Piece | File |
| --- | --- |
| PIN key slots, decoy vault, lockout, fingerprint | `src/crypto/keys.ts` |
| Chunked file encryption format | `src/crypto/blob.ts` |
| Encrypted database and schema | `src/db/database.ts` |
| Import pipeline | `src/vault/importer.ts` |
| Decrypting media for viewing; delete | `src/vault/media.ts` |
| Sorting model | `src/ml/classifier.ts` |
| Auto-lock and screenshot blocking | `src/security/guard.ts` |
| Screens | `src/screens/*` |

### Key slots

There are always two slots, A and B. Each slot wraps its own random 256-bit key with a key derived from a PIN using Argon2id (19 MiB, t=2). If you don't set a decoy PIN, slot B is wrapped under a random PIN nobody knows, so storage looks the same either way. The wrapped slots are stored in Android Keystore-backed secure storage. Separate keys for the database and for files are derived from each slot's key with HKDF.

## Build and install

You need Node 20+ and either an Expo account (cloud build) or Android Studio (local build). The app uses native modules, so **Expo Go won't run it**.

```bash
cd vaultx
npm install

# Cloud build (simplest): produces an .apk you can sideload
npx eas-cli build -p android --profile preview

# Or a local build with Android Studio and the SDK installed
npx expo prebuild -p android
npx expo run:android --variant release
```

For an installable `.apk` from EAS, add this `eas.json`:

```json
{ "build": { "preview": { "android": { "buildType": "apk" } } } }
```

## Sorting model (optional)

The sorter expects a 5-class image model (`drawings, hentai, neutral, porn, sexy`) that takes a 224×224 RGB input in NHWC layout with values in [0,1]. The MIT-licensed MobileNetV2 from `GantMan/nsfw_model`, converted to ONNX with `tf2onnx`, fits this. Copy the `.onnx` file to the phone, then in VaultX go to **Settings → Sorting model → Import .onnx**.

## Known limitations

- **Encryption speed**: AES and Argon2 run in JavaScript. Unlocking takes a few seconds, and large videos encrypt at a few MB/s. Switching to native crypto, such as `react-native-quick-crypto`, would speed this up.
- **Video playback**: a video is decrypted to a temporary file in the app's private cache while it plays. That file is deleted when you close the viewer and on every lock. True in-memory streaming would need a native player data source.
- **Originals stay in your gallery**: imported files are copied, not moved. Delete the originals yourself if you want them gone.
- **Decoy PIN setting**: the option to set a decoy PIN appears only in the real vault's Settings.
- **No recovery**: there is no PIN reset. Forget your PIN and the vault is gone. Uninstalling the app also deletes it, and app data backup is disabled on purpose.
