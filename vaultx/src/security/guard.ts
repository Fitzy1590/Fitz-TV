/**
 * App-wide privacy guard:
 *  - FLAG_SECURE (blocks screenshots, screen recording, and blanks the recents thumbnail)
 *  - lock instantly when the app is backgrounded (configurable)
 *  - lock after N seconds of no touches
 *  - wipe the plaintext scratch dir on every lock
 */
import { useEffect, useRef } from 'react';
import { AppState, type GestureResponderEvent } from 'react-native';
import * as ScreenCapture from 'expo-screen-capture';
import { useSession } from '../state/session';

export function usePrivacyGuard() {
  const { slot, config, lock } = useSession();
  const lastTouch = useRef(Date.now());

  useEffect(() => {
    ScreenCapture.preventScreenCaptureAsync('vaultx').catch(() => {});
    ScreenCapture.enableAppSwitcherProtectionAsync(1).catch(() => {});
  }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => {
      if (s !== 'active' && slot && (config?.lock_on_background ?? 1)) lock();
    });
    return () => sub.remove();
  }, [slot, config, lock]);

  useEffect(() => {
    if (!slot) return;
    const limit = (config?.auto_lock_seconds ?? 30) * 1000;
    if (limit <= 0) return;
    const t = setInterval(() => {
      if (Date.now() - lastTouch.current > limit) lock();
    }, 1000);
    return () => clearInterval(t);
  }, [slot, config, lock]);

  return {
    onTouch: (_e?: GestureResponderEvent) => {
      lastTouch.current = Date.now();
      return false;
    },
  };
}
