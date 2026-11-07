import React, { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { initialiseVaults, unlockWithPin } from '../crypto/keys';
import { useSession } from '../state/session';
import { PinDots, PinPad } from '../ui/PinPad';
import { Button } from '../ui/components';
import { colors, font, topInset } from '../ui/theme';

type Step = 'intro' | 'pin' | 'confirm' | 'decoyAsk' | 'decoy' | 'decoyConfirm' | 'working';

const MIN = 6;
const MAX = 8;

export function SetupScreen({ onDone }: { onDone: () => void }) {
  const unlock = useSession((s) => s.unlock);
  const [step, setStep] = useState<Step>('intro');
  const [pin, setPin] = useState('');
  const [realPin, setRealPin] = useState('');
  const [decoyPin, setDecoyPin] = useState('');
  const [entry, setEntry] = useState('');
  const [error, setError] = useState<string | null>(null);

  const titles: Record<Step, string> = {
    intro: 'Set up VaultX',
    pin: 'Choose your PIN',
    confirm: 'Confirm your PIN',
    decoyAsk: 'Add a decoy vault?',
    decoy: 'Choose a decoy PIN',
    decoyConfirm: 'Confirm decoy PIN',
    working: 'Creating your vault',
  };

  async function finish(real: string, decoy?: string) {
    setStep('working');
    try {
      await initialiseVaults(real, decoy);
      const u = await unlockWithPin(real);
      if (!u) throw new Error('Could not open the new vault.');
      await unlock(u.slot, u.dek);
      onDone();
    } catch (e) {
      setError((e as Error).message);
      setStep('pin');
      setEntry('');
    }
  }

  function next() {
    setError(null);
    if (entry.length < MIN) return setError(`Use at least ${MIN} digits.`);
    if (step === 'pin') {
      setPin(entry);
      setEntry('');
      setStep('confirm');
    } else if (step === 'confirm') {
      if (entry !== pin) {
        setEntry('');
        setStep('pin');
        return setError("PINs didn't match. Start again.");
      }
      setRealPin(entry);
      setEntry('');
      setStep('decoyAsk');
    } else if (step === 'decoy') {
      if (entry === realPin) {
        setEntry('');
        return setError('The decoy PIN must be different from your real PIN.');
      }
      setDecoyPin(entry);
      setEntry('');
      setStep('decoyConfirm');
    } else if (step === 'decoyConfirm') {
      if (entry !== decoyPin) {
        setEntry('');
        setStep('decoy');
        return setError("Decoy PINs didn't match. Start again.");
      }
      finish(realPin, entry);
    }
  }

  const entering = step === 'pin' || step === 'confirm' || step === 'decoy' || step === 'decoyConfirm';

  return (
    <ScrollView contentContainerStyle={s.wrap} keyboardShouldPersistTaps="handled">
      <Text style={font.title}>{titles[step]}</Text>

      {step === 'intro' && (
        <View style={{ gap: 14 }}>
          <Text style={s.p}>Everything you add is encrypted on this phone. Nothing is uploaded.</Text>
          <Text style={s.p}>
            Your PIN is the only way in. There is no reset, so if you forget it the vault can't be recovered.
          </Text>
          <Button label="Choose a PIN" onPress={() => setStep('pin')} />
        </View>
      )}

      {step === 'decoyAsk' && (
        <View style={{ gap: 14 }}>
          <Text style={s.p}>
            A decoy PIN opens a second, separate vault. If someone makes you unlock the app, enter the decoy PIN
            and they see only what you put there.
          </Text>
          <Button label="Set a decoy PIN" onPress={() => setStep('decoy')} />
          <Button label="Skip for now" kind="ghost" onPress={() => finish(realPin)} />
        </View>
      )}

      {step === 'working' && <Text style={s.p}>Deriving keys. This takes a few seconds.</Text>}

      {entering && (
        <View>
          <Text style={s.p}>
            {MIN}–{MAX} digits.
          </Text>
          <PinDots length={entry.length} max={MAX} />
          <PinPad
            onDigit={(d) => entry.length < MAX && setEntry(entry + d)}
            onDelete={() => setEntry(entry.slice(0, -1))}
          />
          <Button label="Continue" onPress={next} disabled={entry.length < MIN} style={{ marginTop: 24 }} />
        </View>
      )}

      {error ? <Text style={s.error}>{error}</Text> : null}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  wrap: { flexGrow: 1, backgroundColor: colors.bg, padding: 20, paddingTop: topInset + 32, gap: 18 },
  p: { ...font.body, color: colors.muted, lineHeight: 22 },
  error: { color: colors.danger, fontSize: 14, textAlign: 'center' },
});
