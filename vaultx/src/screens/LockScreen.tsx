import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import { LockoutError, unlockWithBiometric, unlockWithPin } from '../crypto/keys';
import { useSession } from '../state/session';
import { PinDots, PinPad } from '../ui/PinPad';
import { Button } from '../ui/components';
import { colors, font, topInset } from '../ui/theme';

const MAX = 8;

export function LockScreen() {
  const unlock = useSession((s) => s.unlock);
  const [entry, setEntry] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [bioAvailable, setBioAvailable] = useState(false);

  useEffect(() => {
    (async () => {
      const ok = (await LocalAuthentication.hasHardwareAsync()) && (await LocalAuthentication.isEnrolledAsync());
      setBioAvailable(ok);
    })();
  }, []);

  async function tryPin() {
    if (entry.length < 6 || busy) return;
    setBusy(true);
    setError(null);
    try {
      const u = await unlockWithPin(entry);
      if (u) await unlock(u.slot, u.dek);
      else setError('Wrong PIN.');
    } catch (e) {
      setError(e instanceof LockoutError ? e.message : 'Could not unlock. Try again.');
    } finally {
      setEntry('');
      setBusy(false);
    }
  }

  async function tryBio() {
    setError(null);
    const u = await unlockWithBiometric();
    if (u) await unlock(u.slot, u.dek);
    else setError('Fingerprint unlock is off or was cancelled. Use your PIN.');
  }

  return (
    <View style={s.wrap}>
      <Text style={[font.title, { textAlign: 'center' }]}>VaultX</Text>
      <Text style={[font.small, { textAlign: 'center' }]}>{busy ? 'Checking…' : 'Enter your PIN'}</Text>
      <PinDots length={entry.length} max={MAX} />
      <PinPad
        disabled={busy}
        onDigit={(d) => entry.length < MAX && setEntry(entry + d)}
        onDelete={() => setEntry(entry.slice(0, -1))}
        onBiometric={bioAvailable ? tryBio : undefined}
      />
      <Button label="Unlock" onPress={tryPin} busy={busy} disabled={entry.length < 6} style={{ marginTop: 24, alignSelf: 'stretch' }} />
      {error ? <Text style={s.error}>{error}</Text> : null}
    </View>
  );
}

const s = StyleSheet.create({
  wrap: { flex: 1, backgroundColor: colors.bg, padding: 20, paddingTop: topInset + 48, gap: 6 },
  error: { color: colors.danger, fontSize: 14, textAlign: 'center', marginTop: 14 },
});
