/**
 * On-device NSFW classifier using ONNX Runtime. Nothing leaves the phone.
 *
 * Expects a 5-class image classifier (drawings, hentai, neutral, porn, sexy),
 * e.g. the MIT-licensed GantMan/nsfw_model MobileNetV2 exported to ONNX.
 * The model is imported once from Settings (or pushed by scripts/fetch-model.sh)
 * into app-private storage at <documents>/models/nsfw.onnx.
 * If the model is absent, items are tagged 'unscanned' and the app still works.
 */
import { InferenceSession, Tensor } from 'onnxruntime-react-native';
import { File, Directory, Paths } from 'expo-file-system';
import * as ImageManipulator from 'expo-image-manipulator';
import * as jpeg from 'jpeg-js';
import type { CategoryTag } from '../db/database';

const INPUT = 224;
const LABELS = ['drawings', 'hentai', 'neutral', 'porn', 'sexy'] as const;
type Label = (typeof LABELS)[number];

export interface Classification {
  scores: Record<Label, number>;
  nsfwScore: number;
  category: CategoryTag;
}

let session: InferenceSession | null = null;
let unavailable = false;

export function modelFile(): File {
  return new File(Paths.document, 'models', 'nsfw.onnx');
}

export function hasModel(): boolean {
  return modelFile().exists;
}

/** Copies a user-picked .onnx file into app-private storage and resets the session. */
export async function installModel(pickedUri: string) {
  const dir = new Directory(Paths.document, 'models');
  if (!dir.exists) dir.create({ intermediates: true });
  const dst = modelFile();
  if (dst.exists) dst.delete();
  await new File(pickedUri).copy(dst);
  session = null;
  unavailable = false;
}

async function getSession(): Promise<InferenceSession | null> {
  if (session || unavailable) return session;
  try {
    const f = modelFile();
    if (!f.exists) throw new Error('no model imported');
    session = await InferenceSession.create(f.uri, {
      executionProviders: ['nnapi', 'cpu'],
    });
  } catch (e) {
    console.warn('[classifier] model not available, skipping scans', e);
    unavailable = true;
  }
  return session;
}

function base64ToBytes(b64: string): Uint8Array {
  const bin = globalThis.atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function softmaxIfNeeded(v: Float32Array): number[] {
  const arr = Array.from(v);
  const sum = arr.reduce((a, b) => a + b, 0);
  if (arr.every((x) => x >= 0 && x <= 1) && Math.abs(sum - 1) < 0.05) return arr;
  const m = Math.max(...arr);
  const e = arr.map((x) => Math.exp(x - m));
  const s = e.reduce((a, b) => a + b, 0);
  return e.map((x) => x / s);
}

export function categorise(scores: Record<Label, number>, threshold: number): { nsfwScore: number; category: CategoryTag } {
  const explicit = scores.porn + scores.hentai;
  const nsfwScore = Math.min(1, explicit + scores.sexy);
  let category: CategoryTag = 'safe';
  if (explicit >= threshold) category = 'explicit';
  else if (nsfwScore >= threshold) category = 'suggestive';
  return { nsfwScore, category };
}

/** Classifies a plaintext image or video-thumbnail URI (app-private cache only). */
export async function classifyImage(uri: string, threshold = 0.7): Promise<Classification | null> {
  const s = await getSession();
  if (!s) return null;
  const small = await ImageManipulator.manipulateAsync(uri, [{ resize: { width: INPUT, height: INPUT } }], {
    base64: true,
    format: ImageManipulator.SaveFormat.JPEG,
    compress: 0.9,
  });
  const { data } = jpeg.decode(base64ToBytes(small.base64!), { useTArray: true, formatAsRGBA: true });
  // NHWC float32 in [0,1], matching the Keras export of nsfw_model.
  const input = new Float32Array(INPUT * INPUT * 3);
  for (let p = 0, q = 0; p < data.length; p += 4) {
    input[q++] = data[p] / 255;
    input[q++] = data[p + 1] / 255;
    input[q++] = data[p + 2] / 255;
  }
  const feeds = { [s.inputNames[0]]: new Tensor('float32', input, [1, INPUT, INPUT, 3]) };
  const out = await s.run(feeds);
  const probs = softmaxIfNeeded(out[s.outputNames[0]].data as Float32Array);
  const scores = Object.fromEntries(LABELS.map((l, i) => [l, probs[i] ?? 0])) as Record<Label, number>;
  return { scores, ...categorise(scores, threshold) };
}
