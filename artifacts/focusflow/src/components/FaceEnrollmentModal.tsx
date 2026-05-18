/**
 * FaceEnrollmentModal.tsx
 *
 * Full-screen wizard for enrolling the user's face into Face Lock.
 * Guides through 5 captures (centre + slight left/right/up/down tilt)
 * to build a robust multi-angle embedding average.
 *
 * Flow:
 *   1. Intro screen — explains what will happen and requests camera permission
 *   2. Capture screen — live viewfinder with 5-step capture guide
 *      Each step shows a directional prompt and a capture button.
 *      After each capture, the frame is sent to FaceRecognitionModule.enrollFrame().
 *      Only frames where a face was detected count; bad frames can be retried.
 *   3. Finalising screen — calls FaceRecognitionModule.finalizeEnrollment()
 *   4. Done or Error screen
 *
 * Does NOT modify overlay appearance.
 */

import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { CameraView, CameraType, useCameraPermissions } from 'expo-camera';
import { FaceRecognitionModule } from '@/native-modules/FaceRecognitionModule';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { useTheme } from '@/hooks/useTheme';

const TOTAL_SAMPLES = 5;

const STEP_PROMPTS: { icon: string; label: string; hint: string }[] = [
  { icon: 'scan-outline',       label: 'Look straight ahead',    hint: 'Hold your phone at eye level and look directly at the camera' },
  { icon: 'arrow-back-outline', label: 'Turn slightly left',     hint: 'Rotate your head a little to the left while keeping eyes on camera' },
  { icon: 'arrow-forward-outline', label: 'Turn slightly right', hint: 'Rotate your head a little to the right while keeping eyes on camera' },
  { icon: 'arrow-up-outline',   label: 'Tilt slightly up',       hint: 'Raise your chin slightly and keep looking at the camera' },
  { icon: 'arrow-down-outline', label: 'Tilt slightly down',     hint: 'Lower your chin slightly and keep looking at the camera' },
];

type Screen = 'intro' | 'capture' | 'finalising' | 'done' | 'error';

interface Props {
  visible: boolean;
  onDone: () => void;
  onCancel: () => void;
}

export function FaceEnrollmentModal({ visible, onDone, onCancel }: Props) {
  const { theme, isDark } = useTheme();
  const [permission, requestPermission] = useCameraPermissions();
  const [screen, setScreen] = useState<Screen>('intro');
  const [capturedCount, setCapturedCount] = useState(0);
  const [capturing, setCapturing] = useState(false);
  const [lastCaptureFailed, setLastCaptureFailed] = useState(false);
  const cameraRef = useRef<CameraView | null>(null);

  const currentStep = Math.min(capturedCount, TOTAL_SAMPLES - 1);
  const progress = capturedCount / TOTAL_SAMPLES;

  useEffect(() => {
    if (!visible) {
      setScreen('intro');
      setCapturedCount(0);
      setCapturing(false);
      setLastCaptureFailed(false);
    }
  }, [visible]);

  const handleCancel = useCallback(async () => {
    if (screen === 'capture') {
      await FaceRecognitionModule.cancelEnrollment().catch(() => {});
    }
    onCancel();
  }, [screen, onCancel]);

  const handleStartCapture = useCallback(async () => {
    if (!permission?.granted) {
      const result = await requestPermission();
      if (!result.granted) {
        Alert.alert(
          'Camera permission needed',
          'Face Lock needs access to your front camera to enroll your face. Please grant camera permission in Settings.',
          [{ text: 'OK' }],
        );
        return;
      }
    }
    setScreen('capture');
    setCapturedCount(0);
  }, [permission, requestPermission]);

  const handleCapture = useCallback(async () => {
    if (!cameraRef.current || capturing) return;
    setCapturing(true);
    setLastCaptureFailed(false);
    try {
      const photo = await cameraRef.current.takePictureAsync({
        base64: true,
        quality: 0.7,
        skipProcessing: true,
      });
      const base64 = photo?.base64;
      if (!base64) throw new Error('No image data');

      const faceFound = await FaceRecognitionModule.enrollFrame(base64);
      if (faceFound) {
        const newCount = capturedCount + 1;
        setCapturedCount(newCount);
        if (newCount >= TOTAL_SAMPLES) {
          setScreen('finalising');
          const ok = await FaceRecognitionModule.finalizeEnrollment();
          setScreen(ok ? 'done' : 'error');
        }
      } else {
        setLastCaptureFailed(true);
      }
    } catch {
      setLastCaptureFailed(true);
    } finally {
      setCapturing(false);
    }
  }, [capturing, capturedCount]);

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="fullScreen" onRequestClose={handleCancel}>
      <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top', 'bottom']}>

        {/* ── Header ───────────────────────────────────────────────────── */}
        <View style={[styles.header, { borderBottomColor: theme.border }]}>
          <TouchableOpacity onPress={handleCancel} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
            <Ionicons name="close" size={24} color={theme.text} />
          </TouchableOpacity>
          <Text style={[styles.headerTitle, { color: theme.text }]}>Enroll Your Face</Text>
          <View style={{ width: 24 }} />
        </View>

        {/* ── INTRO ────────────────────────────────────────────────────── */}
        {screen === 'intro' && (
          <View style={styles.centreContent}>
            <View style={[styles.iconCircle, { backgroundColor: COLORS.primary + '18' }]}>
              <Ionicons name="scan" size={48} color={COLORS.primary} />
            </View>
            <Text style={[styles.bigTitle, { color: theme.text }]}>Set up Face Lock</Text>
            <Text style={[styles.bodyText, { color: theme.muted }]}>
              We'll capture 5 quick photos at different angles to build a secure face profile. Everything stays on-device — your photos are never stored or sent anywhere.
            </Text>

            <View style={[styles.infoCard, { backgroundColor: theme.card, borderColor: theme.border }]}>
              {[
                { icon: 'shield-checkmark-outline', text: 'Processed on-device only — no cloud upload' },
                { icon: 'eye-off-outline',           text: 'Raw photos discarded after processing' },
                { icon: 'lock-closed-outline',       text: 'Embeddings encrypted in secure storage' },
              ].map((item, i) => (
                <View key={i} style={styles.infoRow}>
                  <Ionicons name={item.icon as never} size={16} color={COLORS.primary} />
                  <Text style={[styles.infoText, { color: theme.text }]}>{item.text}</Text>
                </View>
              ))}
            </View>

            <TouchableOpacity
              style={[styles.primaryBtn, { backgroundColor: COLORS.primary }]}
              onPress={handleStartCapture}
              activeOpacity={0.8}
            >
              <Ionicons name="camera-outline" size={18} color="#fff" />
              <Text style={styles.primaryBtnText}>Start Enrollment</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ── CAPTURE ──────────────────────────────────────────────────── */}
        {screen === 'capture' && (
          <View style={styles.captureContainer}>
            {/* Progress bar */}
            <View style={[styles.progressBar, { backgroundColor: theme.border }]}>
              <View
                style={[
                  styles.progressFill,
                  { backgroundColor: COLORS.primary, width: `${progress * 100}%` },
                ]}
              />
            </View>
            <Text style={[styles.progressLabel, { color: theme.muted }]}>
              {capturedCount} of {TOTAL_SAMPLES} captured
            </Text>

            {/* Step prompt */}
            <View style={[styles.stepPrompt, { backgroundColor: COLORS.primary + '14', borderColor: COLORS.primary + '33' }]}>
              <Ionicons name={STEP_PROMPTS[currentStep].icon as never} size={20} color={COLORS.primary} />
              <View style={{ flex: 1 }}>
                <Text style={[styles.stepLabel, { color: theme.text }]}>
                  {STEP_PROMPTS[currentStep].label}
                </Text>
                <Text style={[styles.stepHint, { color: theme.muted }]}>
                  {STEP_PROMPTS[currentStep].hint}
                </Text>
              </View>
            </View>

            {/* Camera viewfinder */}
            <View style={styles.viewfinderWrap}>
              <CameraView
                ref={cameraRef}
                style={styles.camera}
                facing={'front' as CameraType}
              />
              {/* Face oval guide */}
              <View style={styles.ovalGuide} pointerEvents="none" />
              {/* Failure feedback */}
              {lastCaptureFailed && (
                <View style={[styles.failBanner, { backgroundColor: COLORS.red + 'CC' }]}>
                  <Ionicons name="warning-outline" size={14} color="#fff" />
                  <Text style={styles.failText}>No face detected — move to better light and try again</Text>
                </View>
              )}
            </View>

            {/* Capture button */}
            <TouchableOpacity
              style={[styles.captureBtn, { borderColor: COLORS.primary, opacity: capturing ? 0.6 : 1 }]}
              onPress={handleCapture}
              disabled={capturing}
              activeOpacity={0.8}
            >
              {capturing ? (
                <ActivityIndicator color={COLORS.primary} size="small" />
              ) : (
                <View style={[styles.captureBtnInner, { backgroundColor: COLORS.primary }]} />
              )}
            </TouchableOpacity>
            <Text style={[styles.captureTip, { color: theme.muted }]}>
              Tap the button when your face is centred in the oval
            </Text>
          </View>
        )}

        {/* ── FINALISING ───────────────────────────────────────────────── */}
        {screen === 'finalising' && (
          <View style={styles.centreContent}>
            <ActivityIndicator size="large" color={COLORS.primary} />
            <Text style={[styles.bigTitle, { color: theme.text, marginTop: SPACING.lg }]}>
              Building your face profile…
            </Text>
            <Text style={[styles.bodyText, { color: theme.muted }]}>
              Processing and encrypting your face embeddings on-device. This takes just a moment.
            </Text>
          </View>
        )}

        {/* ── DONE ─────────────────────────────────────────────────────── */}
        {screen === 'done' && (
          <View style={styles.centreContent}>
            <View style={[styles.iconCircle, { backgroundColor: COLORS.green + '18' }]}>
              <Ionicons name="checkmark-circle" size={52} color={COLORS.green} />
            </View>
            <Text style={[styles.bigTitle, { color: theme.text }]}>Face Lock Enrolled</Text>
            <Text style={[styles.bodyText, { color: theme.muted }]}>
              Your face profile has been saved securely on this device. Face Lock will now check periodically and apply your configured restrictions when it recognises you.
            </Text>
            <TouchableOpacity
              style={[styles.primaryBtn, { backgroundColor: COLORS.green }]}
              onPress={onDone}
              activeOpacity={0.8}
            >
              <Text style={styles.primaryBtnText}>Done</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ── ERROR ────────────────────────────────────────────────────── */}
        {screen === 'error' && (
          <View style={styles.centreContent}>
            <View style={[styles.iconCircle, { backgroundColor: COLORS.red + '18' }]}>
              <Ionicons name="alert-circle" size={52} color={COLORS.red} />
            </View>
            <Text style={[styles.bigTitle, { color: theme.text }]}>Enrollment Failed</Text>
            <Text style={[styles.bodyText, { color: theme.muted }]}>
              Couldn't save your face profile. This usually means the native Face Lock module isn't available in this build. Try again or contact support.
            </Text>
            <TouchableOpacity
              style={[styles.primaryBtn, { backgroundColor: COLORS.primary }]}
              onPress={() => { setScreen('intro'); setCapturedCount(0); }}
              activeOpacity={0.8}
            >
              <Text style={styles.primaryBtnText}>Try Again</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={onCancel} style={{ marginTop: SPACING.sm }}>
              <Text style={[styles.cancelLink, { color: theme.muted }]}>Cancel</Text>
            </TouchableOpacity>
          </View>
        )}

      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm + 2,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerTitle: { fontSize: FONT.md, fontWeight: '700' },

  centreContent: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: SPACING.xl,
    gap: SPACING.md,
  },
  iconCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bigTitle: { fontSize: FONT.xl, fontWeight: '800', textAlign: 'center' },
  bodyText: { fontSize: FONT.sm, lineHeight: 20, textAlign: 'center' },

  infoCard: {
    width: '100%',
    borderRadius: RADIUS.lg,
    borderWidth: StyleSheet.hairlineWidth,
    padding: SPACING.md,
    gap: SPACING.sm,
  },
  infoRow: { flexDirection: 'row', alignItems: 'flex-start', gap: SPACING.sm },
  infoText: { flex: 1, fontSize: FONT.xs, lineHeight: 17 },

  primaryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
    paddingVertical: SPACING.md,
    paddingHorizontal: SPACING.xl,
    borderRadius: RADIUS.full,
    marginTop: SPACING.sm,
  },
  primaryBtnText: { color: '#fff', fontSize: FONT.sm, fontWeight: '700' },
  cancelLink: { fontSize: FONT.sm },

  captureContainer: { flex: 1, paddingHorizontal: SPACING.md, paddingTop: SPACING.sm, gap: SPACING.sm },
  progressBar: { height: 4, borderRadius: 2, overflow: 'hidden' },
  progressFill: { height: 4, borderRadius: 2 },
  progressLabel: { fontSize: FONT.xs, textAlign: 'center' },

  stepPrompt: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: SPACING.sm,
    padding: SPACING.md,
    borderRadius: RADIUS.md,
    borderWidth: 1,
  },
  stepLabel: { fontSize: FONT.sm, fontWeight: '700' },
  stepHint: { fontSize: FONT.xs, lineHeight: 16, marginTop: 2 },

  viewfinderWrap: {
    flex: 1,
    borderRadius: RADIUS.lg,
    overflow: 'hidden',
    position: 'relative',
  },
  camera: { flex: 1 },
  ovalGuide: {
    position: 'absolute',
    top: '10%',
    left: '15%',
    right: '15%',
    bottom: '10%',
    borderRadius: 999,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.6)',
    borderStyle: 'dashed',
  },
  failBanner: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.xs,
    padding: SPACING.sm,
  },
  failText: { color: '#fff', fontSize: FONT.xs, flex: 1 },

  captureBtn: {
    alignSelf: 'center',
    width: 68,
    height: 68,
    borderRadius: 34,
    borderWidth: 3,
    alignItems: 'center',
    justifyContent: 'center',
  },
  captureBtnInner: {
    width: 52,
    height: 52,
    borderRadius: 26,
  },
  captureTip: { fontSize: FONT.xs, textAlign: 'center', marginBottom: SPACING.sm },
});
