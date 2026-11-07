import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors } from './theme';

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'bio', '0', 'del'] as const;

export function PinDots({ length, max = 8 }: { length: number; max?: number }) {
  const n = Math.max(6, Math.min(max, length));
  return (
    <View style={s.dots} accessibilityLabel={`${length} digits entered`}>
      {Array.from({ length: n }).map((_, i) => (
        <View key={i} style={[s.dot, i < length && s.dotOn]} />
      ))}
    </View>
  );
}

export function PinPad({
  onDigit,
  onDelete,
  onBiometric,
  disabled,
}: {
  onDigit: (d: string) => void;
  onDelete: () => void;
  onBiometric?: () => void;
  disabled?: boolean;
}) {
  return (
    <View style={s.grid}>
      {KEYS.map((k) => {
        if (k === 'bio' && !onBiometric) return <View key={k} style={s.key} />;
        const label = k === 'del' ? '⌫' : k === 'bio' ? 'Fingerprint' : k;
        return (
          <Pressable
            key={k}
            disabled={disabled}
            accessibilityRole="button"
            accessibilityLabel={k === 'del' ? 'Delete digit' : label}
            onPress={() => (k === 'del' ? onDelete() : k === 'bio' ? onBiometric?.() : onDigit(k))}
            style={({ pressed }) => [s.key, s.keyFace, pressed && { backgroundColor: colors.panelHi }, disabled && { opacity: 0.4 }]}
          >
            <Text style={[s.keyText, k === 'bio' && { fontSize: 13 }]}>{label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const s = StyleSheet.create({
  dots: { flexDirection: 'row', gap: 14, justifyContent: 'center', marginVertical: 28 },
  dot: { width: 14, height: 14, borderRadius: 7, borderWidth: 1.5, borderColor: colors.muted },
  dotOn: { backgroundColor: colors.accent, borderColor: colors.accent },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 14, maxWidth: 320, alignSelf: 'center' },
  key: { width: 84, height: 64 },
  keyFace: { borderRadius: 16, backgroundColor: colors.panel, alignItems: 'center', justifyContent: 'center' },
  keyText: { color: colors.ink, fontSize: 24, fontWeight: '500' },
});
