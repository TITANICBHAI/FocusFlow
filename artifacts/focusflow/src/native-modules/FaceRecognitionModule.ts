/**
 * FaceRecognitionModule — Old Architecture (NativeModules bridge)
 *
 * Bridges to the Kotlin FaceRecognitionModule which:
 *   - Runs TensorFlow Lite + FaceNet on-device to produce face embeddings
 *   - Stores embeddings encrypted in SharedPreferences (never raw photos)
 *   - Exposes enroll / verify / clear / isEnrolled methods
 *
 * Verification result codes:
 *   'match'      — enrolled person confirmed → allow access
 *   'no_match'   — different face detected   → do NOT block (not the restricted person)
 *   'no_face'    — no face detected           → may need brightness boost, then re-try
 *   'low_light'  — liveness check uncertain   → brightness boost recommended
 *   'error'      — native error               → treat as no_face
 *
 * Kotlin: android-native/app/.../modules/FaceRecognitionModule.kt
 * Registered via: FocusDayPackage → createNativeModules()
 */

import { NativeModules, Platform } from 'react-native';
import { logger } from '@/services/startupLogger';

const FaceRecognitionNative =
  Platform.OS === 'android' ? NativeModules.FaceRecognition : null;

if (Platform.OS === 'android' && !FaceRecognitionNative) {
  void logger.warn(
    'FaceRecognitionModule',
    'NativeModules.FaceRecognition not found — face lock requires an EAS build with the native module included.',
  );
}

export type FaceVerifyResult = 'match' | 'no_match' | 'no_face' | 'low_light' | 'error';

async function callNative<T>(method: string, fn: () => Promise<T>): Promise<T | undefined> {
  try {
    return await fn();
  } catch (e) {
    void logger.error('FaceRecognitionModule', `${method} threw: ${String(e)}`);
    return undefined;
  }
}

function hasMethod(name: string): boolean {
  return !!FaceRecognitionNative && typeof FaceRecognitionNative[name] === 'function';
}

export const FaceRecognitionModule = {
  /**
   * Returns true if the native FaceRecognition module is available (EAS build).
   * Always false in Expo Go or web.
   */
  isAvailable(): boolean {
    return FaceRecognitionNative != null;
  },

  /**
   * Enrolls a face from a JPEG base64 string (no data-URI prefix).
   * Should be called once per capture during multi-sample enrollment.
   * The native side accumulates embeddings across multiple calls and
   * averages them when finalizeEnrollment() is called.
   *
   * Returns true if the frame contained a usable face.
   */
  async enrollFrame(jpegBase64: string): Promise<boolean> {
    if (!hasMethod('enrollFrame')) return false;
    const result = await callNative('enrollFrame', () =>
      FaceRecognitionNative.enrollFrame(jpegBase64) as Promise<boolean>,
    );
    return result ?? false;
  },

  /**
   * Finalizes multi-sample enrollment — averages all accumulated embeddings
   * and stores the result encrypted in SharedPreferences.
   * Clears the staging buffer whether or not it succeeds.
   *
   * Returns true on success.
   */
  async finalizeEnrollment(): Promise<boolean> {
    if (!hasMethod('finalizeEnrollment')) return false;
    const result = await callNative('finalizeEnrollment', () =>
      FaceRecognitionNative.finalizeEnrollment() as Promise<boolean>,
    );
    return result ?? false;
  },

  /**
   * Aborts an in-progress enrollment, discarding any staged embeddings.
   */
  async cancelEnrollment(): Promise<void> {
    if (!hasMethod('cancelEnrollment')) return;
    await callNative('cancelEnrollment', () => FaceRecognitionNative.cancelEnrollment());
  },

  /**
   * Verifies a JPEG frame against the enrolled embedding.
   *
   * Returns one of:
   *   'match'    — this is the enrolled person
   *   'no_match' — a different face is present (do NOT block)
   *   'no_face'  — no face detected in frame (may be dark/face-down)
   *   'low_light'— face detected but confidence is low due to lighting
   *   'error'    — native error (treat as no_face)
   */
  async verifyFrame(jpegBase64: string): Promise<FaceVerifyResult> {
    if (!hasMethod('verifyFrame')) return 'error';
    const result = await callNative('verifyFrame', () =>
      FaceRecognitionNative.verifyFrame(jpegBase64) as Promise<string>,
    );
    const valid: FaceVerifyResult[] = ['match', 'no_match', 'no_face', 'low_light', 'error'];
    return valid.includes(result as FaceVerifyResult)
      ? (result as FaceVerifyResult)
      : 'error';
  },

  /**
   * Returns true if a face has been enrolled and the embedding is stored.
   */
  async isEnrolled(): Promise<boolean> {
    if (!hasMethod('isEnrolled')) return false;
    const result = await callNative('isEnrolled', () =>
      FaceRecognitionNative.isEnrolled() as Promise<boolean>,
    );
    return result ?? false;
  },

  /**
   * Permanently deletes all stored face embeddings and resets the enrollment.
   * This is irreversible — the user will need to re-enroll.
   */
  async clearFaceData(): Promise<void> {
    if (!hasMethod('clearFaceData')) return;
    await callNative('clearFaceData', () => FaceRecognitionNative.clearFaceData());
  },

  /**
   * Sets the maximum front-camera brightness (0.0–1.0 float) that the native
   * side uses when it raises brightness before a low-light re-check.
   * Defaults to 1.0 (maximum). Call on face-lock enable or setting change.
   */
  async setBrightnessBoostLevel(level: number): Promise<void> {
    if (!hasMethod('setBrightnessBoostLevel')) return;
    const clamped = Math.min(1.0, Math.max(0.0, level));
    await callNative('setBrightnessBoostLevel', () =>
      FaceRecognitionNative.setBrightnessBoostLevel(clamped),
    );
  },

  /**
   * Persists the face-lock configuration (enabled, interval minutes) to
   * SharedPreferences so the background FaceCheckService can enforce it
   * even when the JS engine is not running.
   */
  async setFaceLockConfig(enabled: boolean, intervalMinutes: number): Promise<void> {
    if (!hasMethod('setFaceLockConfig')) return;
    await callNative('setFaceLockConfig', () =>
      FaceRecognitionNative.setFaceLockConfig(enabled, Math.max(1, intervalMinutes)),
    );
  },
};
