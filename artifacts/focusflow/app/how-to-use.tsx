/**
 * how-to-use.tsx
 *
 * In-app guide that walks users through FocusFlow's core features.
 */

import React, { useEffect } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  BackHandler,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';

import { useTheme } from '@/hooks/useTheme';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

interface GuideSection {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  color: string;
  steps: { heading: string; body: string }[];
}

const GUIDE: GuideSection[] = [
  {
    icon: 'layers-outline',
    title: 'All Modes',
    color: COLORS.primary,
    steps: [
      { heading: 'Focus Mode', body: 'Focus Mode is connected to a task. Start a task from the Focus tab, choose the apps allowed during that session, and start Focus Mode when you are ready to work.' },
      { heading: 'Standalone Block', body: 'Standalone Block does not need a task. Open it from the Focus tab, choose the apps you want blocked, and set how long the block should last. It is the best choice when you want a timed block right now.' },
      { heading: 'Keyword Blocker', body: 'Keyword Blocker watches visible text, searches, and URLs for words you add. When a blocked word appears, FocusFlow sends the current app home. It works independently of an app list or focus session.' },
      { heading: 'VPN Network Protection', body: 'VPN protection cuts internet access for the apps you select. Use the VPN list for always-on network blocking, or add VPN protection to a standalone block or group schedule when that block is running.' },
      { heading: 'Group Schedules', body: 'Group schedules are recurring block windows. Add several apps to one window, choose the days and times, and let the same group run automatically every week. A schedule can also include VPN protection.' },
      { heading: 'Home Launcher', body: 'Home Launcher replaces your home screen with a focused launcher. Choose Classic or Glassy, select the apps shown in the drawer, and use launcher protections to make switching away harder during a standalone block.' },
    ],
  },
  {
    icon: 'shield-checkmark-outline',
    title: 'Absolute Blocking',
    color: COLORS.red,
    steps: [
      { heading: 'Start with Standalone Block', body: 'Go to the Focus tab, choose Standalone Block, select every app you want blocked, and set the time. This works without creating a task and stays active until the timer ends.' },
      { heading: 'Grant the important permissions first', body: 'Open Settings → Permissions and grant Accessibility and Usage Access so FocusFlow can detect and stop blocked apps. Grant Device Admin as an additional layer before starting a serious standalone block; it adds resistance to force-stop and uninstall escape paths, but it is not a magic guarantee by itself.' },
      { heading: 'Turn on Protect system controls', body: 'In the Defense tab, enable Protect system controls before the block starts. This protects system screens and navigation paths that could otherwise be used to weaken an active block.' },
      { heading: 'Add the extra layers you need', body: 'From the Defense tab, enable Network Protection, launcher protections, aversion deterrents, Shorts/Reels blocking, or other safeguards. These layers work alongside the app block instead of replacing it.' },
      { heading: 'Know what absolute means', body: 'FocusFlow blocks the apps and escape routes you configured. Keep emergency, phone, launcher, and other protected system apps available, and do not treat any Android protection as a substitute for emergency access.' },
    ],
  },
  {
    icon: 'lock-closed-outline',
    title: 'What Can and Cannot Change',
    color: COLORS.orange,
    steps: [
      { heading: 'Always-On and VPN lists', body: 'You can add more apps to the Always-On list or VPN list while protection is running. Removing apps from either list is locked during an active Focus Mode or Standalone Block so the block cannot be weakened halfway through.' },
      { heading: 'Keyword Blocker', body: 'You can add keywords without a password. Removing keywords or clearing the list is protected, and an active standalone block can lock those removals completely.' },
      { heading: 'Group schedules', body: 'You can add, edit, or remove apps and windows in a group schedule when it is not locked. Schedule management is more heavily protected: edits, removals, shortening a window, or deleting a schedule can require the Defense PIN, and active standalone protection can prevent destructive changes.' },
      { heading: 'Why FocusFlow locks changes', body: 'A protection tool is only useful if it cannot be quietly weakened after it starts. FocusFlow allows safer additions, but guards removals, shorter windows, disabled toggles, and other changes that reduce protection.' },
    ],
  },
  {
    icon: 'key-outline',
    title: 'PIN System',
    color: COLORS.purple,
    steps: [
      { heading: 'Focus Session PIN', body: 'The Focus Session PIN guards ending an active Focus Mode session. It is the lock used when you try to stop focus early, so starting a session can mean committing to its full duration.' },
      { heading: 'Defense PIN', body: 'The Defense PIN guards actions that weaken protection: disabling protected Defense toggles, removing apps from Always-On or VPN lists, removing keywords, and changing protected settings.' },
      { heading: 'Group schedules are guarded more heavily', body: 'Adding, editing, shortening, or deleting a group schedule can require the Defense PIN. This prevents a recurring block from being quietly reduced or removed.' },
      { heading: 'Adding is intentionally easier in three lists', body: 'Adding apps to Always-On, adding apps to the VPN list, and adding keywords do not normally require a PIN. The protection is focused on preventing removal or weakening, not on stopping you from adding another safeguard.' },
      { heading: 'Set both passwords before a serious block', body: 'Open Defense → PIN Protection to configure the Focus Session PIN and Defense PIN. Keep them somewhere safe; forgetting them can leave a protection active until its normal expiry or until the correct recovery path is used.' },
    ],
  },
  {
    icon: 'options-outline',
    title: 'Other Toggles',
    color: COLORS.green,
    steps: [
      { heading: 'Protect system controls', body: 'Blocks or redirects sensitive system-control paths such as power-menu, Settings, and other escape routes. It cannot be turned off while Focus Mode or Standalone Block is active.' },
      { heading: 'Network Protection and self-heal', body: 'Network Protection uses the local VPN to cut internet access for selected apps. Self-heal watches the VPN and helps restore it if Android disconnects it. Android VPN permission is required.' },
      { heading: 'Launcher protections', body: 'Home Launcher protections can lock the default launcher choice, protect against uninstall attempts, and keep FocusFlow in control during a standalone block. Configure them from Home Launcher or the Defense tab.' },
      { heading: 'Aversion deterrents', body: 'Vibration, screen dimming, and sound alerts react when a blocked app opens. They are optional deterrents that reinforce the block; they do not replace Accessibility, Usage Access, or the block list.' },
      { heading: 'Content and Focus settings', body: 'Shorts/Reels blocking, Auto-enable Focus Mode, and keeping focus active for the full task duration change how enforcement behaves. Enable only the layers that match your routine, then test them before starting a long block.' },
    ],
  },
];

export default function HowToUseScreen() {
  const insets = useSafeAreaInsets();
  const { theme } = useTheme();
  const params = useLocalSearchParams<{ onboarding?: string }>();
  // When opened as part of the first-run flow, hide the back arrow and show
  // a prominent "Get Started" CTA at the bottom that drops the user on Focus.
  const isOnboarding = params.onboarding === '1';
  const [expanded, setExpanded] = React.useState<number | null>(0);

  const toggle = (i: number) => setExpanded((prev) => (prev === i ? null : i));

  // In onboarding mode, intercept the Android hardware back button so it
  // doesn't pop back to the already-completed user-profile screen. Instead,
  // pressing back drops the user on home (same as the Skip / CTA links).
  useEffect(() => {
    if (!isOnboarding) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      router.replace('/(tabs)/focus');
      return true;
    });
    return () => sub.remove();
  }, [isOnboarding]);

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <View style={[styles.header, { backgroundColor: theme.card, borderBottomColor: theme.border }]}>
        {!isOnboarding && (
          <TouchableOpacity onPress={() => router.back()} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
            <Ionicons name="chevron-back" size={24} color={theme.text} />
          </TouchableOpacity>
        )}
        <View style={{ marginLeft: isOnboarding ? 0 : SPACING.sm, flex: 1 }}>
          <Text style={[styles.title, { color: theme.text }]}>
            {isOnboarding ? 'Welcome to FocusFlow' : 'How to Use FocusFlow'}
          </Text>
          <Text style={[styles.subtitle, { color: theme.muted }]}>
            {isOnboarding ? 'A quick tour before you get started' : 'Your discipline operating system — explained'}
          </Text>
        </View>
        {isOnboarding && (
          <TouchableOpacity
            onPress={() => router.replace('/(tabs)/focus')}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
            style={styles.skipLink}
          >
            <Text style={[styles.skipText, { color: theme.muted }]}>Skip</Text>
          </TouchableOpacity>
        )}
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: 40 + insets.bottom }]}>
        {GUIDE.map((section, i) => {
          const isOpen = expanded === i;
          return (
            <View key={section.title} style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
              <TouchableOpacity
                style={styles.cardHeader}
                onPress={() => toggle(i)}
                activeOpacity={0.7}
              >
                <View style={[styles.iconCircle, { backgroundColor: section.color + '18' }]}>
                  <Ionicons name={section.icon} size={20} color={section.color} />
                </View>
                <Text style={[styles.cardTitle, { color: theme.text }]}>{section.title}</Text>
                <Ionicons
                  name={isOpen ? 'chevron-up' : 'chevron-down'}
                  size={18}
                  color={theme.muted}
                />
              </TouchableOpacity>

              {isOpen && (
                <View style={[styles.steps, { borderTopColor: theme.border }]}>
                  {section.steps.map((step, j) => (
                    <View
                      key={step.heading}
                      style={[
                        styles.step,
                        j < section.steps.length - 1 && {
                          borderBottomWidth: StyleSheet.hairlineWidth,
                          borderBottomColor: theme.border,
                        },
                      ]}
                    >
                      <View style={[styles.stepBullet, { backgroundColor: section.color }]}>
                        <Text style={styles.stepNum}>{j + 1}</Text>
                      </View>
                      <View style={{ flex: 1, gap: 3 }}>
                        <Text style={[styles.stepHeading, { color: theme.text }]}>{step.heading}</Text>
                        <Text style={[styles.stepBody, { color: theme.muted }]}>{step.body}</Text>
                      </View>
                    </View>
                  ))}
                </View>
              )}
            </View>
          );
        })}

        {isOnboarding && (
          <TouchableOpacity
            style={[styles.ctaBtn, { backgroundColor: COLORS.primary }]}
            onPress={() => router.replace('/(tabs)/focus')}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaText}>Got it — let&apos;s start</Text>
            <Ionicons name="arrow-forward" size={18} color="#fff" />
          </TouchableOpacity>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  title: { fontSize: FONT.lg, fontWeight: '800' },
  subtitle: { fontSize: FONT.xs, marginTop: 2 },
  content: { padding: SPACING.lg, gap: SPACING.sm },
  card: {
    borderRadius: RADIUS.md,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
    padding: SPACING.md,
  },
  iconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardTitle: { flex: 1, fontSize: FONT.md, fontWeight: '700' },
  steps: {
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  step: {
    flexDirection: 'row',
    gap: SPACING.sm,
    padding: SPACING.md,
  },
  stepBullet: {
    width: 20,
    height: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 1,
  },
  stepNum: { color: '#fff', fontSize: 11, fontWeight: '700' },
  stepHeading: { fontSize: FONT.sm, fontWeight: '600' },
  stepBody: { fontSize: FONT.xs, lineHeight: 18 },
  skipLink: { paddingHorizontal: SPACING.sm, paddingVertical: 4 },
  skipText: { fontSize: FONT.sm, fontWeight: '600' },
  ctaBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.xs,
    paddingVertical: SPACING.md,
    borderRadius: RADIUS.md,
    marginTop: SPACING.sm,
  },
  ctaText: { color: '#fff', fontSize: FONT.md, fontWeight: '700' },
});
