/**
 * OnboardingScreen — slimmed to two steps:
 *   Step 1 — Three essential permissions (Usage Access, Overlay, Accessibility)
 *             Notifications are requested silently on mount.
 *   Step 2 — "You're all set!" finish screen
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
import * as Notifications from 'expo-notifications';
import { NetworkBlockModule } from '@/native-modules/NetworkBlockModule';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import { useApp } from '@/context/AppContext';
import { useTheme } from '@/hooks/useTheme';
import { requestPermissions } from '@/services/notificationService';
import { UsageStatsModule } from '@/native-modules/UsageStatsModule';
import { ForegroundLaunchModule } from '@/native-modules/ForegroundLaunchModule';
import { RestrictedSettingsBanner } from '@/components/RestrictedSettingsBanner';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

type PermStatus = 'granted' | 'denied' | 'unknown';

interface PermItem {
  id: string;
  emoji: string;
  title: string;
  friendlyDescription: string;
  detailDescription: string;
  deepLinkLabel: string;
}

const CORE_PERMISSIONS: PermItem[] = [
  {
    id: 'usage',
    emoji: '👁',
    title: 'See which app is open',
    friendlyDescription: 'So FocusFlow knows when you switch to a blocked app.',
    detailDescription:
      'Without this, FocusFlow is completely blind — it cannot detect which app you switched to and blocking will silently fail.',
    deepLinkLabel: 'Open Usage Settings',
  },
  {
    id: 'overlay',
    emoji: '🛡️',
    title: 'Show block screen on top',
    friendlyDescription: 'Lets FocusFlow place a gentle block screen over distraction apps.',
    detailDescription:
      'This draws the block overlay directly over blocked apps. Without it, the block screen cannot appear.',
    deepLinkLabel: 'Enable Draw Over Apps',
  },
  {
    id: 'accessibility',
    emoji: '⚡',
    title: 'Instant app redirect',
    friendlyDescription: 'The moment you open a blocked app, FocusFlow redirects you away instantly.',
    detailDescription:
      'This is the engine behind instant blocking. FocusFlow only reads which app is in the foreground — it never reads your messages, passwords, or any personal content.',
    deepLinkLabel: 'Open Accessibility Settings',
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

const FINISH_TIPS = [
  { emoji: '📅', text: 'Schedule tab → add tasks and start focus sessions' },
  { emoji: '🚫', text: 'Side menu → Standalone Block to block any app instantly' },
  { emoji: '📊', text: 'Stats tab → see your focus streaks and progress' },
];

export default function OnboardingScreen() {
  const { state, updateSettings } = useApp();
  const { theme } = useTheme();
  const [step, setStep] = useState<1 | 2>(1);
  const scrollRef = useRef<ScrollView>(null);
  const [statuses, setStatuses] = useState<Record<string, PermStatus>>({});
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const appStateRef = useRef(AppState.currentState);
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(fadeAnim, { toValue: 1, duration: 500, useNativeDriver: true }).start();
  }, [step]);

  const checkAll = useCallback(async () => {
    const result: Record<string, PermStatus> = {};
    await Promise.all(
      CORE_PERMISSIONS.map(async (p) => {
        result[p.id] = await checkStatus(p.id);
      })
    );
    setStatuses(result);
  }, []);

  useEffect(() => { void checkAll(); }, [checkAll]);

  useEffect(() => {
    requestPermissions().catch(() => {});
  }, []);

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
    try {
      await SharedPrefsModule.putString('user_consented_background_service', 'true');
    } catch { /* non-fatal */ }
    router.replace('/(tabs)');
  };

  const advanceStep = () => {
    fadeAnim.setValue(0);
    setStep(2);
    setTimeout(() => scrollRef.current?.scrollTo({ y: 0, animated: false }), 50);
  };

  const grantedCount = CORE_PERMISSIONS.filter((p) => statuses[p.id] === 'granted').length;
  const allGranted = grantedCount === CORE_PERMISSIONS.length;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
      <View style={[styles.stepBar, { backgroundColor: theme.card, borderBottomColor: theme.border }]}>
        {([1, 2] as const).map((n) => (
          <View key={n} style={styles.stepItem}>
            <View
              style={[
                styles.stepDot,
                step === n && { backgroundColor: COLORS.primary },
                step > n && { backgroundColor: COLORS.green },
                step < n && { backgroundColor: theme.border },
              ]}
            >
              {step > n
                ? <Ionicons name="checkmark" size={12} color="#fff" />
                : <Text style={[styles.stepDotText, { color: step === n ? '#fff' : theme.muted }]}>{n}</Text>
              }
            </View>
            <Text style={[styles.stepLabel, { color: step === n ? theme.text : theme.muted }]}>
              {n === 1 ? 'Permissions' : 'All set!'}
            </Text>
          </View>
        ))}
        <View style={[styles.stepConnector, { backgroundColor: theme.border }]} />
      </View>

      <ScrollView
        ref={scrollRef}
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {step === 1 && (
          <Animated.View style={{ opacity: fadeAnim }}>
            <View style={styles.header}>
              <Text style={styles.headerEmoji}>🌸</Text>
              <Text style={[styles.headerTitle, { color: theme.text }]}>Almost ready!</Text>
              <Text style={[styles.headerSub, { color: theme.muted }]}>
                FocusFlow needs a couple of Android permissions to actually block apps.
                These are only used for blocking — nothing else.
              </Text>
            </View>

            <View style={{ marginBottom: SPACING.sm }}>
              <RestrictedSettingsBanner />
            </View>

            <View style={[styles.progressRow, { backgroundColor: theme.card, borderColor: theme.border }]}>
              <Text style={[styles.progressLabel, { color: theme.muted }]}>
                {grantedCount === 0
                  ? 'Tap each card to grant access'
                  : allGranted
                  ? '✅ All permissions granted — you\'re good to go!'
                  : `${grantedCount} of ${CORE_PERMISSIONS.length} granted`}
              </Text>
              <View style={[styles.progressBarBg, { backgroundColor: theme.border }]}>
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

            {CORE_PERMISSIONS.map((perm) => {
              const status = statuses[perm.id] ?? 'unknown';
              const isGranted = status === 'granted';
              const isExpanded = expandedId === perm.id;
              const isLoading = actionLoading === perm.id;

              return (
                <TouchableOpacity
                  key={perm.id}
                  style={[
                    styles.permCard,
                    { backgroundColor: theme.card, borderColor: isGranted ? COLORS.green + '55' : theme.border },
                    isGranted && { backgroundColor: COLORS.green + '08' },
                  ]}
                  onPress={() => setExpandedId(isExpanded ? null : perm.id)}
                  activeOpacity={0.8}
                >
                  <View style={styles.permCardRow}>
                    <Text style={styles.permEmoji}>{perm.emoji}</Text>
                    <View style={styles.permCardText}>
                      <Text style={[styles.permTitle, { color: theme.text }]}>{perm.title}</Text>
                      <Text style={[styles.permDesc, { color: theme.muted }]}>{perm.friendlyDescription}</Text>
                    </View>
                    {isGranted ? (
                      <View style={styles.grantedBadge}>
                        <Ionicons name="checkmark-circle" size={24} color={COLORS.green} />
                      </View>
                    ) : (
                      <Ionicons name={isExpanded ? 'chevron-up' : 'chevron-down'} size={18} color={theme.muted} />
                    )}
                  </View>

                  {isExpanded && !isGranted && (
                    <View style={[styles.expandedBody, { borderTopColor: theme.border }]}>
                      <Text style={[styles.expandedDetail, { color: theme.textSecondary ?? theme.muted }]}>
                        {perm.detailDescription}
                      </Text>
                      <TouchableOpacity
                        style={[styles.grantBtn, { backgroundColor: COLORS.primary }]}
                        onPress={() => handleGrant(perm)}
                        disabled={isLoading}
                        activeOpacity={0.85}
                      >
                        {isLoading ? (
                          <ActivityIndicator color="#fff" size="small" />
                        ) : (
                          <Text style={styles.grantBtnText}>{perm.deepLinkLabel} →</Text>
                        )}
                      </TouchableOpacity>
                    </View>
                  )}
                </TouchableOpacity>
              );
            })}

            {!allGranted && (
              <View style={[styles.skipNote, { backgroundColor: COLORS.primary + '0D', borderColor: COLORS.primary + '30' }]}>
                <Ionicons name="information-circle-outline" size={16} color={COLORS.primary} />
                <Text style={[styles.skipNoteText, { color: theme.muted }]}>
                  You can also grant these later in{' '}
                  <Text style={{ color: COLORS.primary, fontWeight: '700' }}>Settings → Permissions</Text>.
                </Text>
              </View>
            )}

            <TouchableOpacity
              style={[
                styles.continueBtn,
                { backgroundColor: allGranted ? COLORS.primary : COLORS.primary + 'AA' },
              ]}
              onPress={advanceStep}
              activeOpacity={0.85}
            >
              <Text style={styles.continueBtnText}>
                {allGranted ? 'Continue →' : 'Continue anyway →'}
              </Text>
            </TouchableOpacity>
          </Animated.View>
        )}

        {step === 2 && (
          <Animated.View style={[styles.finishContainer, { opacity: fadeAnim }]}>
            <Text style={styles.finishEmoji}>🌸</Text>
            <Text style={[styles.finishTitle, { color: theme.text }]}>You're all set!</Text>
            <Text style={[styles.finishSub, { color: theme.muted }]}>
              FocusFlow is ready to help you build deep focus. Here's a quick look at what's waiting for you:
            </Text>

            <View style={styles.tipsList}>
              {FINISH_TIPS.map((tip, i) => (
                <View key={i} style={[styles.tipRow, { backgroundColor: theme.card, borderColor: theme.border }]}>
                  <Text style={styles.tipEmoji}>{tip.emoji}</Text>
                  <Text style={[styles.tipText, { color: theme.text }]}>{tip.text}</Text>
                </View>
              ))}
            </View>

            <TouchableOpacity
              style={[styles.continueBtn, { backgroundColor: COLORS.primary }]}
              onPress={handleFinish}
              activeOpacity={0.85}
            >
              <Text style={styles.continueBtnText}>Start focusing 🌱</Text>
            </TouchableOpacity>

            <Text style={[styles.footerNote, { color: theme.muted }]}>
              You can personalise your profile and add more preferences anytime from Settings.
            </Text>
          </Animated.View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  stepBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.xl ?? 40,
    paddingVertical: SPACING.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    position: 'relative',
  },
  stepConnector: {
    position: 'absolute',
    height: 1,
    left: '25%',
    right: '25%',
    top: '50%',
    zIndex: -1,
  },
  stepItem: { alignItems: 'center', gap: 4, zIndex: 1 },
  stepDot: {
    width: 28, height: 28, borderRadius: 14,
    alignItems: 'center', justifyContent: 'center',
  },
  stepDotText: { fontSize: 12, fontWeight: '700' },
  stepLabel: { fontSize: FONT.xs ?? 11, fontWeight: '600' },
  scroll: { padding: SPACING.md ?? 16, gap: SPACING.md ?? 16, paddingBottom: 48 },
  header: { alignItems: 'center', gap: 8, marginBottom: 4 },
  headerEmoji: { fontSize: 52, marginBottom: 4 },
  headerTitle: { fontSize: FONT.xl ?? 24, fontWeight: '900', textAlign: 'center' },
  headerSub: {
    fontSize: FONT.sm ?? 14,
    textAlign: 'center',
    lineHeight: 21,
    maxWidth: 320,
    alignSelf: 'center',
  },
  progressRow: {
    borderRadius: RADIUS.lg ?? 12,
    borderWidth: StyleSheet.hairlineWidth,
    padding: SPACING.md ?? 16,
    gap: SPACING.sm ?? 8,
  },
  progressLabel: { fontSize: FONT.sm ?? 14, fontWeight: '600' },
  progressBarBg: { height: 6, borderRadius: 3, overflow: 'hidden' },
  progressBarFill: { height: '100%', borderRadius: 3 },
  permCard: {
    borderRadius: RADIUS.lg ?? 12,
    borderWidth: 1.5,
    overflow: 'hidden',
  },
  permCardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: SPACING.md ?? 16,
    gap: SPACING.sm ?? 8,
  },
  permEmoji: { fontSize: 28, width: 36, textAlign: 'center' },
  permCardText: { flex: 1 },
  permTitle: { fontSize: FONT.md ?? 16, fontWeight: '800', marginBottom: 2 },
  permDesc: { fontSize: FONT.sm ?? 14, lineHeight: 19 },
  grantedBadge: { paddingLeft: 4 },
  expandedBody: {
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: SPACING.md ?? 16,
    paddingBottom: SPACING.md ?? 16,
    paddingTop: SPACING.sm ?? 8,
    gap: SPACING.sm ?? 8,
  },
  expandedDetail: { fontSize: FONT.sm ?? 14, lineHeight: 21 },
  grantBtn: {
    borderRadius: RADIUS.md ?? 10,
    paddingVertical: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  grantBtnText: { color: '#fff', fontSize: FONT.md ?? 16, fontWeight: '700' },
  skipNote: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: SPACING.xs ?? 6,
    borderRadius: RADIUS.md ?? 10,
    borderWidth: 1,
    padding: SPACING.sm ?? 12,
  },
  skipNoteText: { flex: 1, fontSize: FONT.sm ?? 14, lineHeight: 20 },
  continueBtn: {
    borderRadius: RADIUS.lg ?? 16,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  continueBtnText: { color: '#fff', fontSize: FONT.md ?? 16, fontWeight: '800' },
  footerNote: { fontSize: FONT.xs ?? 12, textAlign: 'center', lineHeight: 18 },
  finishContainer: { alignItems: 'center', gap: SPACING.md ?? 16, paddingTop: SPACING.lg ?? 24 },
  finishEmoji: { fontSize: 72, marginBottom: 4 },
  finishTitle: { fontSize: FONT.xxl ?? 28, fontWeight: '900', textAlign: 'center' },
  finishSub: {
    fontSize: FONT.md ?? 16,
    textAlign: 'center',
    lineHeight: 24,
    maxWidth: 320,
  },
  tipsList: { width: '100%', gap: SPACING.sm ?? 10 },
  tipRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm ?? 10,
    padding: SPACING.md ?? 14,
    borderRadius: RADIUS.lg ?? 12,
    borderWidth: StyleSheet.hairlineWidth,
  },
  tipEmoji: { fontSize: 22, width: 30, textAlign: 'center' },
  tipText: { flex: 1, fontSize: FONT.sm ?? 14, lineHeight: 20, fontWeight: '500' },
});
