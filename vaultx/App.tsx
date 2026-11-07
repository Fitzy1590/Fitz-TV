import React, { useEffect, useState } from 'react';
import { ActivityIndicator, BackHandler, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { isInitialised } from './src/crypto/keys';
import type { VaultItem } from './src/db/database';
import { usePrivacyGuard } from './src/security/guard';
import { useSession } from './src/state/session';
import { wipeScratch } from './src/vault/storage';
import { clearThumbCache } from './src/vault/media';
import { SetupScreen } from './src/screens/SetupScreen';
import { LockScreen } from './src/screens/LockScreen';
import { GalleryScreen } from './src/screens/GalleryScreen';
import { ViewerScreen } from './src/screens/ViewerScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { colors } from './src/ui/theme';

type Route = { name: 'gallery' } | { name: 'viewer'; item: VaultItem; siblings: VaultItem[] } | { name: 'settings' };

export default function App() {
  const slot = useSession((s) => s.slot);
  const [ready, setReady] = useState<boolean | null>(null);
  const [route, setRoute] = useState<Route>({ name: 'gallery' });
  const [galleryKey, setGalleryKey] = useState(0);
  const guard = usePrivacyGuard();

  useEffect(() => {
    wipeScratch(); // clear anything left over from a crash
    isInitialised().then(setReady);
  }, []);

  // Reset navigation and the thumbnail cache every time the vault locks.
  useEffect(() => {
    if (!slot) {
      clearThumbCache();
      setRoute({ name: 'gallery' });
    }
  }, [slot]);

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (slot && route.name !== 'gallery') {
        goGallery();
        return true;
      }
      return false;
    });
    return () => sub.remove();
  }, [slot, route]);

  function goGallery() {
    setRoute({ name: 'gallery' });
    setGalleryKey((k) => k + 1); // re-query after edits/deletes
  }

  let screen: React.ReactNode;
  if (ready === null) screen = <ActivityIndicator style={{ flex: 1 }} color={colors.accent} />;
  else if (!ready) screen = <SetupScreen onDone={() => setReady(true)} />;
  else if (!slot) screen = <LockScreen />;
  else if (route.name === 'viewer')
    screen = (
      <ViewerScreen
        key={route.item.id}
        item={route.item}
        siblings={route.siblings}
        onClose={goGallery}
        onNavigate={(item) => setRoute({ name: 'viewer', item, siblings: route.siblings })}
      />
    );
  else if (route.name === 'settings') screen = <SettingsScreen onClose={goGallery} />;
  else
    screen = (
      <GalleryScreen
        key={galleryKey}
        onOpen={(item, siblings) => setRoute({ name: 'viewer', item, siblings })}
        onSettings={() => setRoute({ name: 'settings' })}
      />
    );

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }} onStartShouldSetResponderCapture={guard.onTouch}>
      <StatusBar style="light" />
      {screen}
    </View>
  );
}
