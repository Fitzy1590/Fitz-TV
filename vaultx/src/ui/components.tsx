import React from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Switch, Text, View, type ViewStyle } from 'react-native';
import { colors, font } from './theme';

export function Button({
  label,
  onPress,
  kind = 'primary',
  disabled,
  busy,
  style,
}: {
  label: string;
  onPress: () => void;
  kind?: 'primary' | 'ghost' | 'danger';
  disabled?: boolean;
  busy?: boolean;
  style?: ViewStyle;
}) {
  const bg = kind === 'primary' ? colors.accent : 'transparent';
  const fg = kind === 'primary' ? colors.accentInk : kind === 'danger' ? colors.danger : colors.ink;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled || busy}
      style={({ pressed }) => [
        s.btn,
        { backgroundColor: bg, borderColor: kind === 'primary' ? bg : colors.line, opacity: disabled ? 0.4 : pressed ? 0.8 : 1 },
        style,
      ]}
    >
      {busy ? <ActivityIndicator color={fg} /> : <Text style={[s.btnText, { color: fg }]}>{label}</Text>}
    </Pressable>
  );
}

export function Chip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      style={[s.chip, { backgroundColor: active ? colors.accent : colors.panel, borderColor: active ? colors.accent : colors.line }]}
    >
      <Text style={{ color: active ? colors.accentInk : colors.ink, fontSize: 13, fontWeight: '500' }}>{label}</Text>
    </Pressable>
  );
}

export function Row({ title, detail, children }: { title: string; detail?: string; children?: React.ReactNode }) {
  return (
    <View style={s.row}>
      <View style={{ flex: 1, paddingRight: 12 }}>
        <Text style={font.body}>{title}</Text>
        {detail ? <Text style={font.small}>{detail}</Text> : null}
      </View>
      {children}
    </View>
  );
}

export function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return <Switch value={value} onValueChange={onChange} trackColor={{ true: colors.accent, false: colors.line }} thumbColor={colors.ink} />;
}

export function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={{ gap: 2 }}>
      <Text style={[font.small, { textTransform: 'uppercase', letterSpacing: 1, marginBottom: 6 }]}>{title}</Text>
      <View style={s.section}>{children}</View>
    </View>
  );
}

const s = StyleSheet.create({
  btn: { minHeight: 48, paddingHorizontal: 18, borderRadius: 12, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  btnText: { fontSize: 16, fontWeight: '600' },
  chip: { paddingHorizontal: 12, paddingVertical: 7, borderRadius: 999, borderWidth: 1 },
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, paddingHorizontal: 14, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  section: { backgroundColor: colors.panel, borderRadius: 14, overflow: 'hidden' },
});
