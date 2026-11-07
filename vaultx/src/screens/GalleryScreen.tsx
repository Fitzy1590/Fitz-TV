import React, { useCallback, useEffect, useState } from 'react';
import { FlatList, Image, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { listItems, type CategoryTag, type VaultItem } from '../db/database';
import { importAssets, pickMedia, type ImportProgress } from '../vault/importer';
import { loadThumb } from '../vault/media';
import { useSession } from '../state/session';
import { Button, Chip } from '../ui/components';
import { colors, font, topInset } from '../ui/theme';

type Filter = CategoryTag | 'all' | 'favorites';
const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'favorites', label: 'Favourites' },
  { key: 'explicit', label: 'Explicit' },
  { key: 'suggestive', label: 'Suggestive' },
  { key: 'safe', label: 'Safe' },
  { key: 'unscanned', label: 'Unsorted' },
];

const COLS = 3;
const GAP = 3;

export function GalleryScreen({ onOpen, onSettings }: { onOpen: (item: VaultItem, all: VaultItem[]) => void; onSettings: () => void }) {
  const { slot, dek, fileKey, config, lock } = useSession();
  const [items, setItems] = useState<VaultItem[]>([]);
  const [filter, setFilter] = useState<Filter>('all');
  const [progress, setProgress] = useState<ImportProgress | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const { width } = useWindowDimensions();
  const size = Math.floor((width - GAP * (COLS + 1)) / COLS);

  const refresh = useCallback(async () => {
    setItems(
      await listItems({
        category: filter === 'favorites' ? 'all' : filter,
        favoritesOnly: filter === 'favorites',
      }),
    );
  }, [filter]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function onImport() {
    if (!slot || !dek) return;
    setNotice(null);
    const assets = await pickMedia();
    if (!assets.length) return;
    setProgress({ index: 0, total: assets.length, name: '', fraction: 0 });
    const n = await importAssets(slot, dek, assets, config?.auto_blur_threshold ?? 0.7, setProgress);
    setProgress(null);
    setNotice(
      n === assets.length
        ? `Added ${n} ${n === 1 ? 'item' : 'items'}. The originals are still in your phone's gallery; delete them there if you want them gone.`
        : `Added ${n} of ${assets.length}. Some files couldn't be read.`,
    );
    refresh();
  }

  const blurAll = (config?.blur_all_thumbnails ?? 1) === 1;
  const threshold = config?.auto_blur_threshold ?? 0.7;

  return (
    <View style={s.wrap}>
      <View style={s.header}>
        <Text style={font.title}>Vault</Text>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <Button label="Settings" kind="ghost" onPress={onSettings} />
          <Button label="Lock" kind="ghost" onPress={lock} />
        </View>
      </View>

      <FlatList
        horizontal
        data={FILTERS}
        keyExtractor={(f) => f.key}
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ gap: 8, paddingHorizontal: 16, paddingBottom: 12 }}
        style={{ flexGrow: 0 }}
        renderItem={({ item: f }) => <Chip label={f.label} active={filter === f.key} onPress={() => setFilter(f.key)} />}
      />

      {progress ? (
        <View style={s.banner}>
          <Text style={font.body}>
            Encrypting {progress.index + 1} of {progress.total}
          </Text>
          <View style={s.bar}>
            <View style={[s.barFill, { width: `${Math.round(((progress.index + progress.fraction) / progress.total) * 100)}%` }]} />
          </View>
        </View>
      ) : notice ? (
        <Pressable style={s.banner} onPress={() => setNotice(null)}>
          <Text style={font.small}>{notice}</Text>
        </Pressable>
      ) : null}

      <FlatList
        data={items}
        keyExtractor={(i) => i.id}
        numColumns={COLS}
        contentContainerStyle={{ padding: GAP, gap: GAP, paddingBottom: 120 }}
        columnWrapperStyle={{ gap: GAP }}
        ListEmptyComponent={
          <View style={s.empty}>
            <Text style={font.h2}>{filter === 'all' ? 'Your vault is empty' : 'Nothing here'}</Text>
            <Text style={[font.small, { textAlign: 'center' }]}>
              {filter === 'all' ? 'Tap Add to encrypt photos and videos from your gallery.' : 'Try another filter.'}
            </Text>
          </View>
        }
        renderItem={({ item }) => (
          <Thumb
            item={item}
            size={size}
            blurred={blurAll || item.nsfw_score >= threshold}
            onPress={() => onOpen(item, items)}
            loader={() => (slot && fileKey ? loadThumb(slot, fileKey, item) : Promise.resolve(null))}
          />
        )}
      />

      <View style={s.fab}>
        <Button label={progress ? 'Adding…' : '+  Add'} onPress={onImport} busy={!!progress} />
      </View>
    </View>
  );
}

function Thumb({
  item,
  size,
  blurred,
  onPress,
  loader,
}: {
  item: VaultItem;
  size: number;
  blurred: boolean;
  onPress: () => void;
  loader: () => Promise<string | null>;
}) {
  const [uri, setUri] = useState<string | null>(null);
  const [peek, setPeek] = useState(false);

  useEffect(() => {
    let live = true;
    loader()
      .then((u) => live && setUri(u))
      .catch(() => {});
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.id]);

  return (
    <Pressable
      onPress={onPress}
      onLongPress={() => setPeek(true)}
      onPressOut={() => setPeek(false)}
      delayLongPress={250}
      accessibilityLabel={item.mime_type.startsWith('video') ? 'Video' : 'Photo'}
      style={{ width: size, height: size, backgroundColor: colors.panel, borderRadius: 4, overflow: 'hidden' }}
    >
      {uri ? <Image source={{ uri }} style={{ width: size, height: size }} blurRadius={blurred && !peek ? 40 : 0} /> : null}
      {item.mime_type.startsWith('video') ? (
        <Text style={s.badge}>{item.duration_ms ? formatDuration(item.duration_ms) : 'Video'}</Text>
      ) : null}
      {item.is_favorite ? <Text style={[s.badge, { left: 6, right: undefined }]}>★</Text> : null}
    </Pressable>
  );
}

function formatDuration(ms: number) {
  const t = Math.round(ms / 1000);
  return `${Math.floor(t / 60)}:${String(t % 60).padStart(2, '0')}`;
}

const s = StyleSheet.create({
  wrap: { flex: 1, backgroundColor: colors.bg, paddingTop: topInset + 8 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 16, paddingBottom: 12 },
  banner: { marginHorizontal: 16, marginBottom: 10, padding: 12, borderRadius: 12, backgroundColor: colors.panel, gap: 8 },
  bar: { height: 6, borderRadius: 3, backgroundColor: colors.line, overflow: 'hidden' },
  barFill: { height: 6, backgroundColor: colors.accent },
  empty: { alignItems: 'center', gap: 6, paddingTop: 80, paddingHorizontal: 32 },
  badge: { position: 'absolute', bottom: 6, right: 6, color: colors.ink, fontSize: 11, fontWeight: '600', backgroundColor: 'rgba(0,0,0,0.55)', paddingHorizontal: 5, paddingVertical: 1, borderRadius: 4 },
  fab: { position: 'absolute', right: 16, bottom: 28 },
});
