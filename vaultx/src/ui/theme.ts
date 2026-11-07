import { Platform, StatusBar } from 'react-native';

export const colors = {
  bg: '#121820',
  panel: '#1a222c',
  panelHi: '#232d39',
  line: '#2b3644',
  ink: '#e6ebf0',
  muted: '#97a3b0',
  accent: '#e0b45c',
  accentInk: '#1b1407',
  danger: '#f08a8a',
  ok: '#5fd39a',
};

export const topInset = Platform.OS === 'android' ? (StatusBar.currentHeight ?? 24) : 44;

export const font = {
  title: { fontSize: 26, fontWeight: '700' as const, color: colors.ink },
  h2: { fontSize: 17, fontWeight: '600' as const, color: colors.ink },
  body: { fontSize: 15, color: colors.ink },
  small: { fontSize: 13, color: colors.muted },
};
