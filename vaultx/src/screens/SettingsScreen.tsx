import React, { useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import * as LocalAuthentication from 'expo-local-authentication';
import * as SQLite from 'expo-sqlite';
import { Directory, Paths } from 'expo-file-system';
import { changePin, disableBiometric, enableBiometric, setDecoyPin } from '../crypto/keys';
import { setConfig, stats, type VaultConfig } from '../db/database';
import { hasModel, installModel } from '../ml/classifier';
import { useSession } from '../state/session';
import { PinDots, PinPad } from '../ui/PinPad';
import { Button, Chip, Row, Section, Toggle } from '../ui/components';
import { colors, font, topInset } from '../ui/theme';

const LOCK_OPTIONS = [
  { v: 15, l: '15 s' },
  { v: 30, l: '30 s' },
  { v: 60, l: '1 min' },
  { v: 300, l: '5 min' },
  { v: 0, l: 'Never' },
];
const BLUR_OPTIONS = [
  { v: 0.5, l: 'Strict' },
  { v: 0.7, l: 'Balanced' },
  { v: 0.9, l: 'Relaxed' },
];

type PinFlow = null | { kind: 'change' | 'decoy'; step: 'enter' | 'confirm'; first: string };

export function SettingsScreen({ onClose }: { onClose: () => void }) {
  const { slot, dek, config, refreshConfig } = useSession();
  const [model, setModel] = useState(hasModel());
  const [bioHw, setBioHw] = useState(false);
  const [summary, setSummary] = useState('');
  const [flow, setFlow] = useState<PinFlow>(null);
  const [entry, setEntry] = useState('');
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      setBioHw((await LocalAuthentication.hasHardwareAsync()) && (await LocalAuthentication.isEnrolledAsync()));
      const rows = await stats();
      const n = rows.reduce((a, r) => a + r.n, 0);
      const mb = rows.reduce((a, r) => a + (r.bytes ?? 0), 0) / 1048576;
      setSummary(`${n} ${n === 1 ? 'item' : 'items'} · ${mb.toFixed(1)} MB encrypted`);
    })();
  }, []);

  if (!config || !slot || !dek) return null;

  async function patch(p: Partial<VaultConfig>) {
    await setConfig(p);
    await refreshConfig();
  }

  async function toggleBio(on: boolean) {
    if (!slot || !dek) return;
    try {
      if (on) await enableBiometric(slot, dek);
      else await disableBiometric(slot);
      await patch({ biometric_enabled: on ? 1 : 0 });
    } catch {
      setMsg('Fingerprint setup was cancelled.');
    }
  }

  async function importModel() {
    const r = await DocumentPicker.getDocumentAsync({ type: '*/*', copyToCacheDirectory: true });
    if (r.canceled) return;
    const a = r.assets[0];
    if (!a.name.toLowerCase().endsWith('.onnx')) return setMsg('Pick a .onnx model file.');
    await installModel(a.uri);
    setModel(true);
    setMsg('Model installed. New imports will be sorted automatically.');
  }

  async function submitPin() {
    if (!flow || !slot || !dek) return;
    if (entry.length < 6) return;
    if (flow.step === 'enter') {
      setFlow({ ...flow, step: 'confirm', first: entry });
      setEntry('');
      return;
    }
    if (entry !== flow.first) {
      setFlow({ ...flow, step: 'enter', first: '' });
      setEntry('');
      return setMsg("PINs didn't match. Try again.");
    }
    const pin = entry;
    setEntry('');
    setFlow(null);
    if (flow.kind === 'change') {
      await changePin(slot, dek, pin);
      setMsg('PIN changed.');
    } else {
      Alert.alert('Replace the decoy vault?', 'Anything currently in the decoy vault will be erased.', [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Replace',
          style: 'destructive',
          onPress: async () => {
            try {
              await SQLite.deleteDatabaseAsync('vault_B.db');
            } catch {}
            const d = new Directory(Paths.document, 'vault_B');
            if (d.exists) d.delete();
            await setDecoyPin(pin);
            setMsg('Decoy PIN set.');
          },
        },
      ]);
    }
  }

  if (flow) {
    return (
      <View style={[s.wrap, { paddingHorizontal: 20 }]}>
        <Text style={font.title}>
          {flow.kind === 'change' ? 'New PIN' : 'Decoy PIN'}
          {flow.step === 'confirm' ? ' — confirm' : ''}
        </Text>
        <PinDots length={entry.length} />
        <PinPad onDigit={(d) => entry.length < 8 && setEntry(entry + d)} onDelete={() => setEntry(entry.slice(0, -1))} />
        <View style={{ gap: 10, marginTop: 24 }}>
          <Button label="Continue" onPress={submitPin} disabled={entry.length < 6} />
          <Button label="Cancel" kind="ghost" onPress={() => (setFlow(null), setEntry(''))} />
        </View>
      </View>
    );
  }

  return (
    <ScrollView style={s.wrap} contentContainerStyle={s.content}>
      <View style={s.header}>
        <Text style={font.title}>Settings</Text>
        <Button label="Done" kind="ghost" onPress={onClose} />
      </View>
      <Text style={font.small}>{summary}</Text>
      {msg ? <Text style={[font.small, { color: colors.accent }]}>{msg}</Text> : null}

      <Section title="Locking">
        <Row title="Lock when I leave the app" detail="Locks as soon as you switch apps or turn off the screen.">
          <Toggle value={config.lock_on_background === 1} onChange={(v) => patch({ lock_on_background: v ? 1 : 0 })} />
        </Row>
        <Row title="Lock after no activity" />
        <View style={s.chips}>
          {LOCK_OPTIONS.map((o) => (
            <Chip key={o.v} label={o.l} active={config.auto_lock_seconds === o.v} onPress={() => patch({ auto_lock_seconds: o.v })} />
          ))}
        </View>
        {bioHw ? (
          <Row title="Fingerprint unlock" detail="Only unlocks the vault you're in now.">
            <Toggle value={config.biometric_enabled === 1} onChange={toggleBio} />
          </Row>
        ) : null}
      </Section>

      <Section title="Privacy">
        <Row title="Blur every thumbnail" detail="Press and hold a thumbnail to peek.">
          <Toggle value={config.blur_all_thumbnails === 1} onChange={(v) => patch({ blur_all_thumbnails: v ? 1 : 0 })} />
        </Row>
        <Row title="Auto-blur sensitivity" detail="Used when blur-every-thumbnail is off, and for sorting new imports." />
        <View style={s.chips}>
          {BLUR_OPTIONS.map((o) => (
            <Chip key={o.v} label={o.l} active={Math.abs(config.auto_blur_threshold - o.v) < 0.01} onPress={() => patch({ auto_blur_threshold: o.v })} />
          ))}
        </View>
      </Section>

      <Section title="Sorting model">
        <Row
          title={model ? 'Model installed' : 'No model installed'}
          detail={model ? 'Runs on this phone only.' : 'Without a model, new items are left unsorted.'}
        >
          <Button label={model ? 'Replace' : 'Import .onnx'} kind="ghost" onPress={importModel} />
        </Row>
      </Section>

      <Section title="PIN">
        <Row title="Change PIN">
          <Button label="Change" kind="ghost" onPress={() => setFlow({ kind: 'change', step: 'enter', first: '' })} />
        </Row>
        {slot === 'A' ? (
          <Row title="Decoy PIN" detail="Opens a separate vault. Setting it erases the current decoy vault.">
            <Button label="Set" kind="ghost" onPress={() => setFlow({ kind: 'decoy', step: 'enter', first: '' })} />
          </Row>
        ) : null}
      </Section>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  wrap: { flex: 1, backgroundColor: colors.bg, paddingTop: topInset + 8 },
  content: { paddingHorizontal: 16, paddingBottom: 48, gap: 20 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, padding: 14 },
});
