/**
 * OnboardingScreen — two steps:
 *   Step 1 — Three essential permissions (Usage Access, Overlay, Accessibility)
 *             Notifications are requested silently on mount.
 *   Step 2 — Ready to go finish screen
 *
 * Battery optimisation is auto-fired in _layout.tsx bootstrap — no card needed.
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

interface PermItem {
  id: string;
  icon: IoniconName;
  title: string;
  friendlyDescription: string;
  detailDescription: string;
  deepLinkLabel: string;
}

// Order matches the forced-check order used throughout the app:
// Overlay → Usage → Accessibility
const CORE_PERMISSIONS: PermItem[] = [
  {
    id: 'overlay',
    icon: 'layers-outline',
    title: 'Draw over other apps',
    friendlyDescription: 'Lets FocusFlow place the block screen on top of a blocked app.',
    detailDescription:
      'When you open a blocked app, FocusFlow needs to cover it with the block screen immediately. Without this, the block screen cannot appear and the app stays open.',
    deepLinkLabel: 'Enable Draw Over Apps',
  },
  {
    id: 'usage',
    icon: 'analytics-outline',
    title: 'App usage access',
    friendlyDescription: 'Tells FocusFlow which app you just switched to, so it can block it.',
    detailDescription:
      'Without this, FocusFlow has no way to know which app is open — blocks will silently fail. It only reads the app name in the foreground. No content, no keystrokes, no data inside apps.',
    deepLinkLabel: 'Open Usage Settings',
  },
  {
    id: 'accessibility',
    icon: 'shield-checkmark-outline',
    title: 'Accessibility service',
    friendlyDescription: 'The engine that redirects you the instant you open a blocked app.',
    detailDescription:
      'Android will show a warning saying this app "can monitor your actions." This is standard wording for all accessibility services — it does not mean FocusFlow reads your screen or data.\n\nFocusFlow only ever checks one thing: which app is in the foreground. It never reads messages, passwords, keystrokes, or any content inside apps. This is the same method used by every serious app blocker on Android.',
    deepLinkLabel: 'Open Accessibility Settings',
  },
];

const FINISH_TIPS: { icon: IoniconName; text: string }[] = [
  { icon: 'calendar-outline',    text: 'Schedule tab — add tasks and start focus sessions' },
  { icon: 'ban-outline',         text: 'Side menu — Standalone Block to block any app instantly' },
  { icon: 'stats-chart-outline', text: 'Stats tab — track your streaks and session history' },
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

export default function OnboardingScreen() {
  const { state, updateSettings } = useApp();
  const [step, setStep] = useState<1 | 2>(1);
  const scrollRef = useRef<ScrollView>(null);
  const [statuses, setStatuses] = useState<Record<string, PermStatus>>({});
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const appStateRef = useRef(AppState.currentState);
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(fadeAnim, { toValue: 1, duration: 400, useNativeDriver: true }).start();
  }, [step]);

  const checkAll = useCallback(async () => {
    const result: Record<string, PermStatus> = {};
    await Promise.all(
      CORE_PERMISSIONS.map(async (p) => {
        result[p.id] = await checkStatus(p.id);
      }),
    );
    setStatuses(result);
  }, []);

  useEffect(() => { void checkAll(); }, [checkAll]);
  useEffect(() => { requestPermissions().catch(() => {}); }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', async (next) => {
      if (appStateRef.current.match(/inactive|background/) && next === 'active') {
        await checkAll();
      }
      appStateRef.current = next;
    });
    return () => sub.remove();
  }, [checkAll]);

  const handleGrant = async (perm: PermItem) => {
    if (statuses[perm.id] === 'granted') return;
    setActionLoading(perm.id);
    try {
      if (perm.id === 'usage') {
        if (Platform.OS === 'android') {
          await Linking.sendIntent('android.settings.USAGE_ACCESS_SETTINGS');
        }
      } else if (perm.id === 'accessibility') {
        await UsageStatsModule.openAccessibilitySettings();
      } else if (perm.id === 'overlay') {
        await ForegroundLaunchModule.requestOverlayPermission();
      }
    } catch {
      try { await Linking.openSettings(); } catch { /* ignore */ }
    } finally {
      setActionLoading(null);
    }
  };

  const handleFinish = async () => {
    try {
      await updateSettings({ ...state.settings, onboardingComplete: true });
    } catch { /* non-blocking */ }
    // Dual-write critical onboarding flags to SharedPrefs so they survive
    // AsyncStorage/SQLite wipes (e.g. reinstall without uninstall).
    await Promise.allSettled([
      SharedPrefsModule.putString('user_consented_background_service', 'true'),
      SharedPrefsModule.putString('onboarding_complete', 'true'),
    ]);
    router.replace('/(tabs)/focus');
  };

  const advanceStep = () => {
    fadeAnim.setValue(0);
    setStep(2);
    setTimeout(() => scrollRef.current?.scrollTo({ y: 0, animated: false }), 50);
  };

  const grantedCount = CORE_PERMISSIONS.filter((p) => statuses[p.id] === 'granted').length;
  const allGranted = grantedCount === CORE_PERMISSIONS.length;

  return (
    <SafeAreaView style={styles.safe}>
      {/* ── Step indicator ── */}
      <View style={styles.stepBar}>
        <View style={styles.stepConnector} />
        {([1, 2] as const).map((n) => (
          <View key={n} style={styles.stepItem}>
            <View
              style={[
                styles.stepDot,
                step === n && styles.stepDotActive,
                step > n  && styles.stepDotDone,
                step < n  && styles.stepDotFuture,
              ]}
            >
              {step > n
                ? <Ionicons name="checkmark" size={12} color="#fff" />
                : <Text style={[styles.stepDotText, { color: step === n ? '#fff' : COLORS.muted }]}>{n}</Text>
              }
            </View>
            <Text style={[styles.stepLabel, { color: step === n ? COLORS.text : COLORS.muted }]}>
              {n === 1 ? 'Permissions' : 'Done'}
            </Text>
          </View>
        ))}
      </View>

      <ScrollView
        ref={scrollRef}
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {/* ── Step 1: Grant permissions ── */}
        {step === 1 && (
          <Animated.View style={{ opacity: fadeAnim }}>
            <View style={styles.header}>
              <View style={styles.headerIconWrap}>
                <Ionicons name="shield-checkmark-outline" size={30} color={COLORS.primary} />
              </View>
              <Text style={styles.headerTitle}>Set up blocking</Text>
              <Text style={styles.headerSub}>
                FocusFlow needs three Android permissions to enforce blocks.
                Tap each card, grant access, then return here.
              </Text>
            </View>

            {/* Privacy trust note */}
            <View style={styles.trustNote}>
              <Ionicons name="lock-closed-outline" size={14} color={COLORS.green} />
              <Text style={styles.trustNoteText}>
                FocusFlow only reads <Text style={styles.trustNoteBold}>which app is open</Text> — never your messages, passwords, or screen content. Everything stays on your device.
              </Text>
            </View>

            <View style={{ marginBottom: SPACING.sm }}>
              <RestrictedSettingsBanner />
            </View>

            {/* Progress bar */}
            <View style={styles.progressRow}>
              <Text style={styles.progressLabel}>
                {allGranted
                  ? 'All permissions granted'
                  : grantedCount === 0
                  ? 'Tap a card below to get started'
                  : `${grantedCount} of ${CORE_PERMISSIONS.length} granted`}
              </Text>
              <View style={styles.progressBarBg}>
                <View
                  style={[
                    styles.progressBarFill,
                    {
                      width: `${(grantedCount / CORE_PERMISSIONS.length) * 100}%` as any,
                      backgroundColor: allGranted ? COLORS.green : COLORS.primary,
                    },
                  ]}
                />
              </View>
            </View>

            {/* Permission cards */}
            {CORE_PERMISSIONS.map((perm) => {
              const isGranted = (statuses[perm.id] ?? 'unknown') === 'granted';
              const isExpanded = expandedId === perm.id;
              const isLoading = actionLoading === perm.id;

              return (
                <TouchableOpacity
                  key={perm.id}
                  style={[styles.permCard, isGranted && styles.permCardGranted]}
                  onPress={() => setExpandedId(isExpanded ? null : perm.id)}
                  activeOpacity={0.75}
                >
                  <View style={styles.permCardRow}>
                    <View style={[styles.permIconWrap, isGranted && styles.permIconWrapGranted]}>
                      <Ionicons
                        name={perm.icon}
                        size={20}
                        color={isGranted ? COLORS.green : COLORS.primary}
                      />
                    </View>
                    <View style={styles.permCardText}>
                      <Text style={styles.permTitle}>{perm.title}</Text>
                      <Text style={styles.permDesc}>{perm.friendlyDescription}</Text>
                    </View>
                    {isGranted
                      ? <Ionicons name="checkmark-circle" size={22} color={COLORS.green} />
                      : <Ionicons name={isExpanded ? 'chevron-up' : 'chevron-down'} size={16} color={COLORS.muted} />
                    }
                  </View>

                  {isExpanded && !isGranted && (
                    <View style={styles.expandedBody}>
                      <Text style={styles.expandedDetail}>{perm.detailDescription}</Text>
                      <TouchableOpacity
                        style={styles.grantBtn}
                        onPress={() => handleGrant(perm)}
                        disabled={isLoading}
                        activeOpacity={0.85}
                      >
                        {isLoading
                          ? <ActivityIndicator color="#fff" size="small" />
                          : <Text style={styles.grantBtnText}>{perm.deepLinkLabel} →</Text>
                        }
                      </TouchableOpacity>
                    </View>
                  )}
                </TouchableOpacity>
              );
            })}

            {!allGranted && (
              <View style={styles.infoNote}>
                <Ionicons name="information-circle-outline" size={15} color={COLORS.primary} />
                <Text style={styles.infoNoteText}>
                  You can grant these later in{' '}
                  <Text style={{ color: COLORS.primary, fontWeight: '700' }}>Settings → Permissions</Text>.
                  {' '}Blocking will not work until they are enabled.
                </Text>
              </View>
            )}

            <TouchableOpacity
              style={[styles.primaryBtn, !allGranted && styles.primaryBtnDim]}
              onPress={advanceStep}
              activeOpacity={0.85}
            >
              <Text style={styles.primaryBtnText}>
                {allGranted ? 'Continue' : 'Continue anyway'}
              </Text>
            </TouchableOpacity>
          </Animated.View>
        )}

        {/* ── Step 2: All set ── */}
        {step === 2 && (
          <Animated.View style={[styles.finishContainer, { opacity: fadeAnim }]}>
            <View style={styles.finishIconWrap}>
              <Ionicons name="lock-closed" size={36} color={COLORS.primary} />
            </View>
            <Text style={styles.finishTitle}>You're ready to block.</Text>
            <Text style={styles.finishSub}>Here's what you can do right now:</Text>

            <View style={styles.tipsList}>
              {FINISH_TIPS.map((tip, i) => (
                <View key={i} style={styles.tipRow}>
                  <View style={styles.tipIconWrap}>
                    <Ionicons name={tip.icon} size={17} color={COLORS.primary} />
                  </View>
                  <Text style={styles.tipText}>{tip.text}</Text>
                </View>
              ))}
            </View>

            <TouchableOpacity style={styles.primaryBtn} onPress={handleFinish} activeOpacity={0.85}>
              <Text style={styles.primaryBtnText}>Start blocking</Text>
            </TouchableOpacity>

            <Text style={styles.footerNote}>
              Permissions and preferences can be changed anytime in Settings.
            </Text>
          </Animated.View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: COLORS.card,
  },

  /* Step bar */
  stepBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.xl ?? 40,
    paddingVertical: SPACING.sm ?? 10,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
    backgroundColor: COLORS.card,
    position: 'relative',
  },
  stepConnector: {
    position: 'absolute',
    height: 1,
    left: '25%',
    right: '25%',
    top: '50%',
    backgroundColor: COLORS.border,
    zIndex: 0,
  },
  stepItem: { alignItems: 'center', gap: 4, zIndex: 1 },
  stepDot: {
    width: 28, height: 28, borderRadius: 14,
    alignItems: 'center', justifyContent: 'center',
  },
  stepDotActive: { backgroundColor: COLORS.primary },
  stepDotDone:   { backgroundColor: COLORS.green },
  stepDotFuture: { backgroundColor: COLORS.border },
  stepDotText:   { fontSize: FONT.xs ?? 11, fontWeight: '700' },
  stepLabel:     { fontSize: FONT.xs ?? 11, fontWeight: '600' },

  /* Scroll content */
  scroll: {
    padding: SPACING.md ?? 16,
    gap: SPACING.md ?? 14,
    paddingBottom: 48,
  },

  /* Header */
  header: { alignItems: 'center', gap: 8, marginBottom: 4 },
  headerIconWrap: {
    width: 64, height: 64, borderRadius: RADIUS.lg ?? 16,
    backgroundColor: COLORS.primaryLight,
    alignItems: 'center', justifyContent: 'center',
    marginBottom: 4,
  },
  headerTitle: {
    fontSize: FONT.xl ?? 22, fontWeight: '900',
    textAlign: 'center', color: COLORS.text,
  },
  headerSub: {
    fontSize: FONT.sm ?? 13, textAlign: 'center',
    lineHeight: 21, maxWidth: 300,
    alignSelf: 'center', color: COLORS.textSecondary,
  },

  /* Progress */
  progressRow: {
    borderRadius: RADIUS.md ?? 10,
    borderWidth: 1, borderColor: COLORS.border,
    padding: SPACING.md ?? 14,
    gap: 8,
    backgroundColor: COLORS.surface,
  },
  progressLabel: { fontSize: FONT.sm ?? 13, fontWeight: '600', color: COLORS.text },
  progressBarBg: {
    height: 5, borderRadius: 3, overflow: 'hidden',
    backgroundColor: COLORS.border,
  },
  progressBarFill: { height: '100%', borderRadius: 3 },

  /* Permission cards */
  permCard: {
    borderRadius: RADIUS.lg ?? 14,
    borderWidth: 1.5,
    borderColor: COLORS.border,
    backgroundColor: COLORS.card,
    overflow: 'hidden',
  },
  permCardGranted: {
    borderColor: COLORS.green + '55',
    backgroundColor: COLORS.greenLight,
  },
  permCardRow: {
    flexDirection: 'row', alignItems: 'center',
    padding: SPACING.md ?? 14,
    gap: SPACING.sm ?? 10,
  },
  permIconWrap: {
    width: 42, height: 42, borderRadius: RADIUS.md ?? 10,
    backgroundColor: COLORS.primaryLight,
    alignItems: 'center', justifyContent: 'center',
  },
  permIconWrapGranted: { backgroundColor: COLORS.greenLight },
  permCardText: { flex: 1 },
  permTitle: {
    fontSize: FONT.md ?? 15, fontWeight: '700',
    color: COLORS.text, marginBottom: 2,
  },
  permDesc: { fontSize: FONT.sm ?? 13, lineHeight: 18, color: COLORS.textSecondary },

  expandedBody: {
    borderTopWidth: 1, borderTopColor: COLORS.border,
    paddingHorizontal: SPACING.md ?? 14,
    paddingBottom: SPACING.md ?? 14,
    paddingTop: SPACING.sm ?? 10,
    gap: SPACING.sm ?? 10,
    backgroundColor: COLORS.surface,
  },
  expandedDetail: {
    fontSize: FONT.sm ?? 13, lineHeight: 20,
    color: COLORS.textSecondary,
  },
  grantBtn: {
    borderRadius: RADIUS.md ?? 10,
    paddingVertical: 12,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: COLORS.primary,
  },
  grantBtnText: { color: '#fff', fontSize: FONT.md ?? 15, fontWeight: '700' },

  /* Info note */
  infoNote: {
    flexDirection: 'row', alignItems: 'flex-start',
    gap: SPACING.xs ?? 6,
    borderRadius: RADIUS.md ?? 10,
    borderWidth: 1, borderColor: COLORS.primaryLight,
    backgroundColor: COLORS.primaryLight,
    padding: SPACING.sm ?? 10,
  },
  infoNoteText: {
    flex: 1, fontSize: FONT.sm ?? 13,
    lineHeight: 19, color: COLORS.textSecondary,
  },

  /* Buttons */
  primaryBtn: {
    borderRadius: RADIUS.lg ?? 14,
    paddingVertical: 15,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: COLORS.primary,
  },
  primaryBtnDim: { opacity: 0.6 },
  primaryBtnText: { color: '#fff', fontSize: FONT.md ?? 15, fontWeight: '700' },

  /* Finish screen */
  finishContainer: {
    alignItems: 'center', gap: SPACING.md ?? 14,
    paddingTop: SPACING.xl ?? 32,
  },
  finishIconWrap: {
    width: 84, height: 84, borderRadius: RADIUS.xl ?? 24,
    backgroundColor: COLORS.primaryLight,
    alignItems: 'center', justifyContent: 'center',
    marginBottom: 4,
  },
  finishTitle: {
    fontSize: FONT.xxl ?? 26, fontWeight: '900',
    textAlign: 'center', color: COLORS.text,
  },
  finishSub: {
    fontSize: FONT.md ?? 15, textAlign: 'center',
    lineHeight: 23, color: COLORS.textSecondary,
  },

  tipsList: { width: '100%', gap: SPACING.sm ?? 10 },
  tipRow: {
    flexDirection: 'row', alignItems: 'center',
    gap: SPACING.sm ?? 10,
    padding: SPACING.md ?? 14,
    borderRadius: RADIUS.lg ?? 14,
    borderWidth: 1, borderColor: COLORS.border,
    backgroundColor: COLORS.surface,
  },
  tipIconWrap: {
    width: 34, height: 34, borderRadius: RADIUS.md ?? 10,
    backgroundColor: COLORS.primaryLight,
    alignItems: 'center', justifyContent: 'center',
  },
  tipText: {
    flex: 1, fontSize: FONT.sm ?? 13,
    lineHeight: 19, fontWeight: '500', color: COLORS.text,
  },

  footerNote: {
    fontSize: FONT.xs ?? 11, textAlign: 'center',
    lineHeight: 17, color: COLORS.muted,
    paddingHorizontal: SPACING.md ?? 16,
  },

  /* Privacy trust note */
  trustNote: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: SPACING.xs ?? 6,
    borderRadius: RADIUS.md ?? 10,
    borderWidth: 1,
    borderColor: COLORS.greenLight,
    backgroundColor: COLORS.greenLight,
    padding: SPACING.sm ?? 10,
  },
  trustNoteText: {
    flex: 1,
    fontSize: FONT.sm ?? 13,
    lineHeight: 19,
    color: COLORS.textSecondary,
  },
  trustNoteBold: {
    fontWeight: '700',
    color: COLORS.text,
  },
});
