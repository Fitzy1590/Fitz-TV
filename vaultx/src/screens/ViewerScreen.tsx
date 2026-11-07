import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Image, StyleSheet, Text, View } from 'react-native';
import { useVideoPlayer, VideoView } from 'expo-video';
import type { File } from 'expo-file-system';
import { updateItem, type VaultItem } from '../db/database';
import { deleteItem, loadImage, loadVideo, releaseVideo } from '../vault/media';
import { useSession } from '../state/session';
import { Button } from '../ui/components';
import { colors, font, topInset } from '../ui/theme';

export function ViewerScreen({
  item,
  siblings,
  onClose,
  onNavigate,
}: {
  item: VaultItem;
  siblings: VaultItem[];
  onClose: () => void;
  onNavigate: (item: VaultItem) => void;
}) {
  const { slot, fileKey } = useSession();
  const [fav, setFav] = useState(item.is_favorite === 1);
  const idx = siblings.findIndex((i) => i.id === item.id);
  const isVideo = item.mime_type.startsWith('video');

  async function toggleFav() {
    const v = !fav;
    setFav(v);
    await updateItem(item.id, { is_favorite: v ? 1 : 0 });
  }

  function confirmDelete() {
    Alert.alert('Delete this item?', 'It will be removed from the vault for good.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          if (slot) await deleteItem(slot, item);
          onClose();
        },
      },
    ]);
  }

  return (
    <View style={s.wrap}>
      <View style={s.top}>
        <Button label="Back" kind="ghost" onPress={onClose} />
        <Text style={[font.small, { flex: 1, textAlign: 'center' }]} numberOfLines={1}>
          {idx + 1} / {siblings.length}
        </Text>
        <Button label={fav ? '★ Saved' : '☆ Favourite'} kind="ghost" onPress={toggleFav} />
      </View>

      <View style={s.stage}>
        {slot && fileKey ? (
          isVideo ? (
            <VideoPane key={item.id} load={() => loadVideo(slot, fileKey, item)} />
          ) : (
            <ImagePane key={item.id} load={() => loadImage(slot, fileKey, item)} />
          )
        ) : null}
      </View>

      <View style={s.bottom}>
        <Button label="‹ Prev" kind="ghost" disabled={idx <= 0} onPress={() => onNavigate(siblings[idx - 1])} />
        <Button label="Delete" kind="danger" onPress={confirmDelete} />
        <Button label="Next ›" kind="ghost" disabled={idx >= siblings.length - 1} onPress={() => onNavigate(siblings[idx + 1])} />
      </View>
      <Text style={[font.small, { textAlign: 'center', paddingBottom: 16 }]} numberOfLines={1}>
        {item.original_name} · {(item.file_size_bytes / 1048576).toFixed(1)} MB · {labelFor(item.category_tag)}
      </Text>
    </View>
  );
}

function labelFor(tag: VaultItem['category_tag']) {
  return { safe: 'Safe', suggestive: 'Suggestive', explicit: 'Explicit', unscanned: 'Unsorted' }[tag];
}

function ImagePane({ load }: { load: () => Promise<string> }) {
  const [uri, setUri] = useState<string | null>(null);
  const [err, setErr] = useState(false);
  useEffect(() => {
    let live = true;
    load()
      .then((u) => live && setUri(u))
      .catch(() => live && setErr(true));
    return () => {
      live = false;
      setUri(null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  if (err) return <Text style={font.small}>This file couldn't be decrypted. It may be damaged.</Text>;
  if (!uri) return <ActivityIndicator color={colors.accent} />;
  return <Image source={{ uri }} style={{ width: '100%', height: '100%' }} resizeMode="contain" />;
}

function VideoPane({ load }: { load: () => Promise<File> }) {
  const [file, setFile] = useState<File | null>(null);
  const [err, setErr] = useState(false);
  useEffect(() => {
    let live = true;
    let f: File | null = null;
    load()
      .then((x) => {
        f = x;
        if (live) setFile(x);
        else releaseVideo(x);
      })
      .catch(() => live && setErr(true));
    return () => {
      live = false;
      releaseVideo(f);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  if (err) return <Text style={font.small}>This video couldn't be decrypted. It may be damaged.</Text>;
  if (!file) return <Text style={font.small}>Decrypting video…</Text>;
  return <Player uri={file.uri} />;
}

function Player({ uri }: { uri: string }) {
  const player = useVideoPlayer(uri, (p) => {
    p.loop = false;
    p.play();
  });
  return <VideoView player={player} style={{ width: '100%', height: '100%' }} contentFit="contain" nativeControls allowsPictureInPicture={false} />;
}

const s = StyleSheet.create({
  wrap: { flex: 1, backgroundColor: '#000', paddingTop: topInset },
  top: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 8, gap: 4 },
  stage: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  bottom: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 8, paddingTop: 8 },
});
