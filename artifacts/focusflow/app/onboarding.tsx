/**
 * OnboardingScreen — one permission per step:
 *   Step 1 — Draw over other apps (Overlay)
 *   Step 2 — App usage access
 *   Step 3 — Accessibility service (with extra calm reassurance)
 *
 * Battery optimisation is auto-fired in _layout.tsx bootstrap — no card needed.
 * Notifications are requested silently on mount.
 * Optional permissions (VPN, Device Admin, Media) are surfaced in Settings later.
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  AppState,
  Linking,
  Platform,
  Animated,
  Dimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import { useApp } from '@/context/AppContext';
import { requestPermissions } from '@/services/notificationService';
import { UsageStatsModule } from '@/native-modules/UsageStatsModule';
import { ForegroundLaunchModule } from '@/native-modules/ForegroundLaunchModule';
import { RestrictedSettingsBanner } from '@/components/RestrictedSettingsBanner';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

type PermStatus = 'granted' | 'denied' | 'unknown';
type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

interface PermStep {
  id: string;
  icon: IoniconName;
  iconColor?: string;
  label: string;
  title: string;
  whatItDoes: string;
  whatItDoesNot?: string;
  androidWarningNote?: string;
  deepLinkLabel: string;
  showRestrictedBanner?: boolean;
}

const STEPS: PermStep[] = [
  {
    id: 'overlay',
    icon: 'layers-outline',
    label: 'Step 1 of 3',
    title: 'Show the\nblock screen',
    whatItDoes:
      'When you open a blocked app, FocusFlow needs to place the block screen on top of it instantly — before you even see the app.',
    whatItDoesNot:
      'It cannot see what\'s inside any app. It just covers the screen with a plain block page.',
    deepLinkLabel: 'Allow drawing over apps',
  },
  {
    id: 'usage',
    icon: 'analytics-outline',
    label: 'Step 2 of 3',
    title: 'Know which\napp is open',
    whatItDoes:
      'FocusFlow needs to know when a blocked app moves to the foreground, so it can act immediately.',
    whatItDoesNot:
      'It only reads the app name — nothing else. No content, no messages, no keystrokes. Just "which app is open right now."',
    deepLinkLabel: 'Enable usage access',
  },
  {
    id: 'accessibility',
    icon: 'shield-checkmark-outline',
    iconColor: '#34C759',
    label: 'Step 3 of 3',
    title: 'Catch blocked\napps instantly',
    whatItDoes:
      'Android\'s Accessibility feature lets FocusFlow react the moment a blocked app opens — so blocks happen before you can interact with the app.',
    whatItDoesNot:
      'It does not read your messages, passwords, or screen content. It watches for one thing only: when a blocked app becomes active.',
    androidWarningNote:
      'Android will show a message saying "this app can monitor your actions." That\'s Android\'s standard label for all accessibility apps — it\'s the same warning shown for password managers and every other blocker. It does not mean FocusFlow reads your screen.',
    deepLinkLabel: 'Enable accessibility service',
    showRestrictedBanner: true,
  },
];

async function checkStatus(id: string): Promise<PermStatus> {
  try {
    switch (id) {
      case 'usage': {
        const ok = await UsageStatsModule.hasPermission();
        return ok ? 'granted' : 'denied';
      }
      case 'accessibility': {
        const ok = await UsageStatsModule.hasAccessibilityPermission();
        return ok ? 'granted' : 'denied';
      }
      case 'overlay': {
        const ok = await ForegroundLaunchModule.hasOverlayPermission();
        return ok ? 'granted' : 'denied';
      }
      default:
        return 'denied';
    }
  } catch {
    return 'denied';
  }
}

async function openSettings(id: string) {
  if (id === 'usage') {
    if (Platform.OS === 'android') {
      await Linking.sendIntent('android.settings.USAGE_ACCESS_SETTINGS');
    }
  } else if (id === 'accessibility') {
    await UsageStatsModule.openAccessibilitySettings();
  } else if (id === 'overlay') {
    await ForegroundLaunchModule.requestOverlayPermission();
  }
}

export default function OnboardingScreen() {
  const { state, updateSettings } = useApp();
  const [stepIndex, setStepIndex] = useState(0);
  const [statuses, setStatuses] = useState<Record<string, PermStatus>>({});
  const [actionLoading, setActionLoading] = useState(false);
  const appStateRef = useRef(AppState.currentState);
  const fadeAnim = useRef(new Animated.Value(1)).current;
  const slideAnim = useRef(new Animated.Value(0)).current;

  const currentStep = STEPS[stepIndex];
  const isGranted = (statuses[currentStep?.id] ?? 'unknown') === 'granted';
  const isLastStep = stepIndex === STEPS.length - 1;

  const checkAll = useCallback(async () => {
    const result: Record<string, PermStatus> = {};
    await Promise.all(
      STEPS.map(async (p) => {
        result[p.id] = await checkStatus(p.id);
      }),
    );
    setStatuses(result);
  }, []);

  useEffect(() => { void checkAll(); }, [checkAll]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', async (next) => {
      if (appStateRef.current.match(/inactive|background/) && next === 'active') {
        await checkAll();
      }
      appStateRef.current = next;
    });
    return () => sub.remove();
  }, [checkAll]);

  const animateToStep = (next: number) => {
    Animated.parallel([
      Animated.timing(fadeAnim, { toValue: 0, duration: 180, useNativeDriver: true }),
      Animated.timing(slideAnim, { toValue: -30, duration: 180, useNativeDriver: true }),
    ]).start(() => {
      setStepIndex(next);
      slideAnim.setValue(30);
      Animated.parallel([
        Animated.timing(fadeAnim, { toValue: 1, duration: 220, useNativeDriver: true }),
        Animated.timing(slideAnim, { toValue: 0, duration: 220, useNativeDriver: true }),
      ]).start();
    });
  };

  const handleGrant = async () => {
    if (isGranted) return;
    setActionLoading(true);
    try {
      await openSettings(currentStep.id);
    } catch {
      try { await Linking.openSettings(); } catch { /* ignore */ }
    } finally {
      setActionLoading(false);
    }
  };

  const handleContinue = async () => {
    if (isLastStep) {
      await handleFinish();
    } else {
      animateToStep(stepIndex + 1);
    }
  };

  const handleSkip = () => {
    if (isLastStep) {
      void handleFinish();
    } else {
      animateToStep(stepIndex + 1);
    }
  };

  const handleFinish = async () => {
    try {
      await updateSettings({ ...state.settings, onboardingComplete: true });
    } catch { /* non-blocking */ }
    await Promise.allSettled([
      SharedPrefsModule.putString('user_consented_background_service', 'true'),
      SharedPrefsModule.putString('onboarding_complete', 'true'),
    ]);
    router.replace('/(tabs)/focus');
  };

  if (!currentStep) return null;

  const grantedCount = STEPS.filter((s) => (statuses[s.id] ?? 'unknown') === 'granted').length;

  return (
    <SafeAreaView style={styles.safe}>
      {/* Progress dots */}
      <View style={styles.progressBar}>
        {STEPS.map((_, i) => {
          const stepGranted = (statuses[STEPS[i].id] ?? 'unknown') === 'granted';
          return (
            <View
              key={i}
              style={[
                styles.progressSegment,
                i === stepIndex && styles.progressSegmentActive,
                stepGranted && styles.progressSegmentDone,
              ]}
            />
          );
        })}
      </View>

      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        <Animated.View
          style={[
            styles.stepContent,
            { opacity: fadeAnim, transform: [{ translateY: slideAnim }] },
          ]}
        >
          {/* Icon */}
          <View style={[
            styles.iconWrap,
            currentStep.iconColor ? { backgroundColor: currentStep.iconColor + '18' } : null,
          ]}>
            {isGranted
              ? <Ionicons name="checkmark-circle" size={48} color={COLORS.green} />
              : <Ionicons name={currentStep.icon} size={48} color={currentStep.iconColor ?? COLORS.primary} />
            }
          </View>

          {/* Label + Title */}
          <Text style={styles.stepLabel}>{currentStep.label}</Text>
          <Text style={styles.stepTitle}>{currentStep.title}</Text>

          {/* Granted badge */}
          {isGranted && (
            <View style={styles.grantedBadge}>
              <Ionicons name="checkmark-circle" size={15} color={COLORS.green} />
              <Text style={styles.grantedBadgeText}>Permission granted</Text>
            </View>
          )}

          {/* What it does */}
          {!isGranted && (
            <View style={styles.section}>
              <Text style={styles.sectionLabel}>What this does</Text>
              <Text style={styles.sectionBody}>{currentStep.whatItDoes}</Text>
            </View>
          )}

          {/* What it does NOT do */}
          {!isGranted && currentStep.whatItDoesNot && (
            <View style={[styles.section, styles.sectionSafe]}>
              <View style={styles.sectionSafeRow}>
                <Ionicons name="lock-closed-outline" size={14} color={COLORS.green} />
                <Text style={styles.sectionSafeLabel}>What it does NOT do</Text>
              </View>
              <Text style={styles.sectionSafeBody}>{currentStep.whatItDoesNot}</Text>
            </View>
          )}

          {/* Android warning note (Accessibility only) */}
          {!isGranted && currentStep.androidWarningNote && (
            <View style={styles.warningNote}>
              <View style={styles.warningNoteHeader}>
                <Ionicons name="information-circle-outline" size={16} color={COLORS.primary} />
                <Text style={styles.warningNoteTitle}>Heads up about Android's warning</Text>
              </View>
              <Text style={styles.warningNoteBody}>{currentStep.androidWarningNote}</Text>
            </View>
          )}

          {/* Restricted settings banner (Accessibility only) */}
          {!isGranted && currentStep.showRestrictedBanner && (
            <RestrictedSettingsBanner />
          )}

          {/* Grant button */}
          {!isGranted && (
            <TouchableOpacity
              style={styles.grantBtn}
              onPress={handleGrant}
              disabled={actionLoading}
              activeOpacity={0.85}
            >
              {actionLoading
                ? <ActivityIndicator color="#fff" size="small" />
                : (
                  <>
                    <Text style={styles.grantBtnText}>{currentStep.deepLinkLabel}</Text>
                    <Ionicons name="arrow-forward" size={16} color="#fff" style={{ marginLeft: 6 }} />
                  </>
                )
              }
            </TouchableOpacity>
          )}

          {/* Continue / next */}
          <TouchableOpacity
            style={[styles.continueBtn, isGranted && styles.continueBtnProminent]}
            onPress={handleContinue}
            activeOpacity={0.85}
          >
            <Text style={[styles.continueBtnText, isGranted && styles.continueBtnTextProminent]}>
              {isGranted
                ? (isLastStep ? 'Start blocking' : 'Next →')
                : (isLastStep ? 'Finish setup' : 'Continue anyway')}
            </Text>
          </TouchableOpacity>

          {/* Progress summary */}
          <Text style={styles.progressNote}>
            {grantedCount === STEPS.length
              ? 'All permissions granted — you\'re all set.'
              : grantedCount === 0
              ? 'Permissions can also be enabled later in Settings.'
              : `${grantedCount} of ${STEPS.length} granted — blocking will work for the ones enabled.`}
          </Text>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: COLORS.card,
  },

  progressBar: {
    flexDirection: 'row',
    gap: 6,
    paddingHorizontal: SPACING.lg ?? 24,
    paddingTop: SPACING.sm ?? 10,
    paddingBottom: SPACING.xs ?? 6,
  },
  progressSegment: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    backgroundColor: COLORS.border,
  },
  progressSegmentActive: {
    backgroundColor: COLORS.primary,
  },
  progressSegmentDone: {
    backgroundColor: COLORS.green,
  },

  scroll: {
    padding: SPACING.lg ?? 24,
    gap: SPACING.md ?? 16,
    paddingBottom: 48,
  },

  stepContent: {
    gap: SPACING.md ?? 16,
  },

  iconWrap: {
    width: 84,
    height: 84,
    borderRadius: RADIUS.xl ?? 22,
    backgroundColor: COLORS.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },

  stepLabel: {
    fontSize: FONT.xs ?? 11,
    fontWeight: '700',
    color: COLORS.primary,
    letterSpacing: 1.1,
    textTransform: 'uppercase',
  },
  stepTitle: {
    fontSize: FONT.xxl ?? 26,
    fontWeight: '900',
    color: COLORS.text,
    lineHeight: 34,
    letterSpacing: -0.4,
  },

  grantedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: RADIUS.lg ?? 14,
    backgroundColor: COLORS.greenLight,
    alignSelf: 'flex-start',
  },
  grantedBadgeText: {
    fontSize: FONT.sm ?? 13,
    fontWeight: '700',
    color: COLORS.green,
  },

  section: {
    borderRadius: RADIUS.lg ?? 14,
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surface,
    padding: SPACING.md ?? 14,
    gap: 6,
  },
  sectionLabel: {
    fontSize: FONT.xs ?? 11,
    fontWeight: '700',
    color: COLORS.muted,
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  sectionBody: {
    fontSize: FONT.md ?? 15,
    lineHeight: 23,
    color: COLORS.text,
  },

  sectionSafe: {
    borderColor: COLORS.greenLight,
    backgroundColor: COLORS.greenLight,
  },
  sectionSafeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  sectionSafeLabel: {
    fontSize: FONT.xs ?? 11,
    fontWeight: '700',
    color: COLORS.green,
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  sectionSafeBody: {
    fontSize: FONT.md ?? 15,
    lineHeight: 23,
    color: COLORS.text,
  },

  warningNote: {
    borderRadius: RADIUS.lg ?? 14,
    borderWidth: 1,
    borderColor: COLORS.primaryLight,
    backgroundColor: COLORS.primaryLight,
    padding: SPACING.md ?? 14,
    gap: 8,
  },
  warningNoteHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  warningNoteTitle: {
    fontSize: FONT.sm ?? 13,
    fontWeight: '700',
    color: COLORS.primary,
  },
  warningNoteBody: {
    fontSize: FONT.sm ?? 13,
    lineHeight: 20,
    color: COLORS.textSecondary,
  },

  grantBtn: {
    borderRadius: RADIUS.lg ?? 14,
    paddingVertical: 15,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    backgroundColor: COLORS.primary,
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 5,
  },
  grantBtnText: {
    color: '#fff',
    fontSize: FONT.md ?? 15,
    fontWeight: '700',
  },

  continueBtn: {
    borderRadius: RADIUS.lg ?? 14,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surface,
  },
  continueBtnProminent: {
    backgroundColor: COLORS.primary,
    borderColor: COLORS.primary,
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 5,
  },
  continueBtnText: {
    fontSize: FONT.md ?? 15,
    fontWeight: '600',
    color: COLORS.textSecondary,
  },
  continueBtnTextProminent: {
    color: '#fff',
    fontWeight: '700',
  },

  progressNote: {
    fontSize: FONT.xs ?? 12,
    textAlign: 'center',
    color: COLORS.muted,
    lineHeight: 18,
    paddingHorizontal: SPACING.md ?? 16,
  },
});
