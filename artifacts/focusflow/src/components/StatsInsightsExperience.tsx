import React, { useCallback, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Linking,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import dayjs from 'dayjs';
import { useApp } from '@/context/AppContext';
import { useTheme } from '@/hooks/useTheme';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import {
  buildAnalyticsSnapshot,
  type AnalyticsSnapshot,
  type AnalyticsWindow,
} from '@/services/analytics/AnalyticsProcessor';
import {
  buildInsights,
  syncWeeklyStandout,
  type InsightCard,
} from '@/services/analytics/InsightEngine';
import {
  isUsageSummaryAvailable,
  UsageStatsModule,
} from '@/native-modules/UsageStatsModule';
import { dbGetLifetimeStats } from '@/data/database';
import { syncAchievements, type AchievementState } from '@/services/analytics/AchievementEngine';

type LoadState = 'loading' | 'ready' | 'permission' | 'error';

const VIEW_OPTIONS: { value: AnalyticsWindow; label: string }[] = [
  { value: 'yesterday', label: 'Yesterday' },
  { value: 'week', label: 'This Week' },
  { value: 'three_months', label: '3 Months' },
];

export function StatsInsightsExperience() {
  const insets = useSafeAreaInsets();
  const { state, updateSettings } = useApp();
  const { theme } = useTheme();
  const [view, setView] = useState<AnalyticsWindow>('week');
  const [snapshot, setSnapshot] = useState<AnalyticsSnapshot | null>(null);
  const [achievements, setAchievements] = useState<AchievementState | null>(null);
  const [weeklyStandout, setWeeklyStandout] = useState<InsightCard | null>(null);
  const [loadState, setLoadState] = useState<LoadState>('loading');

  const weekStartDay = state.settings.weekStartDay ?? 0;
  const load = useCallback(async () => {
    setLoadState('loading');
    setSnapshot(null);
    setWeeklyStandout(null);

    try {
      if (view === 'three_months') {
        if (!isUsageSummaryAvailable || !(await UsageStatsModule.hasPermission())) {
          setLoadState('permission');
          return;
        }
      }
      const [next, lifetime] = await Promise.all([
        buildAnalyticsSnapshot(view, {
          weekStartDay,
          usageStatsPermission: view === 'three_months',
        }),
        dbGetLifetimeStats(),
      ]);
      setSnapshot(next);
      setAchievements(await syncAchievements(lifetime, next));
      setWeeklyStandout(view === 'week' ? await syncWeeklyStandout(next) : null);
      setLoadState('ready');
    } catch {
      setLoadState('error');
    }
  }, [view, weekStartDay]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  const insights = useMemo(
    () => (snapshot ? buildInsights(snapshot) : []),
    [snapshot],
  );

  const subtitle = view === 'yesterday'
    ? 'The day that just ended'
    : view === 'week'
      ? 'The pattern forming this week'
      : 'What your routine has held over time';

  return (
    <SafeAreaView
      style={[styles.safe, { backgroundColor: theme.background }]}
      edges={['top']}
    >
      <View style={[styles.header, { backgroundColor: theme.card, borderBottomColor: theme.border }]}>
        <View style={styles.headerCopy}>
          <Text style={[styles.title, { color: theme.text }]}>Stats</Text>
          <Text style={[styles.subtitle, { color: theme.muted }]}>{subtitle}</Text>
        </View>
        <Ionicons name="analytics-outline" size={23} color={COLORS.primary} />
      </View>

      <View style={[styles.switcher, { backgroundColor: theme.card, borderBottomColor: theme.border }]}>
        {VIEW_OPTIONS.map((option) => {
          const active = option.value === view;
          return (
            <TouchableOpacity
              key={option.value}
              testID={`stats-view-${option.value}`}
              accessibilityRole="button"
              accessibilityState={{ selected: active }}
              style={[
                styles.switchButton,
                {
                  backgroundColor: active ? COLORS.primary : theme.surface,
                  borderColor: active ? COLORS.primary : theme.border,
                },
              ]}
              onPress={() => setView(option.value)}
              activeOpacity={0.82}
            >
              <Text style={[styles.switchLabel, { color: active ? COLORS.card : theme.textSecondary }]}>
                {option.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {view === 'three_months' && !state.settings.threeMonthPrivacyNoticeDismissed && (
        <LocalOnlyNotice
          theme={theme}
          onDismiss={() => {
            void updateSettings({
              ...state.settings,
              threeMonthPrivacyNoticeDismissed: true,
            });
          }}
        />
      )}

      {loadState === 'loading' && (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={COLORS.primary} />
          <Text style={[styles.centerText, { color: theme.muted }]}>Reading your local history…</Text>
        </View>
      )}

      {loadState === 'permission' && (
        <PermissionGate theme={theme} />
      )}

      {loadState === 'error' && (
        <View style={styles.center}>
          <Ionicons name="warning-outline" size={28} color={COLORS.orange} />
          <Text style={[styles.centerTitle, { color: theme.text }]}>Stats unavailable</Text>
          <Text style={[styles.centerText, { color: theme.muted }]}>
            FocusFlow could not read this period. Return to the view to try again.
          </Text>
        </View>
      )}

      {loadState === 'ready' && snapshot && (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + SPACING.xxl }]}
          showsVerticalScrollIndicator={false}
        >
          {weeklyStandout && <InsightCardView insight={weeklyStandout} theme={theme} />}
          <View style={styles.insightStack}>
            {insights.filter((insight) => insight.id !== weeklyStandout?.id).map((insight) => (
              <InsightCardView key={insight.id} insight={insight} theme={theme} />
            ))}
          </View>

          {view === 'week' && <PresenceStrip snapshot={snapshot} weekStartDay={weekStartDay} theme={theme} />}
          {view === 'three_months' && <TrendChart snapshot={snapshot} theme={theme} />}
          <TaskSummary snapshot={snapshot} theme={theme} />
          {achievements && <AchievementRow state={achievements} theme={theme} />}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

function PermissionGate({ theme }: { theme: ReturnType<typeof useTheme>['theme'] }) {
  return (
    <View style={styles.center}>
      <View style={[styles.permissionIcon, { backgroundColor: COLORS.orange + '18' }]}>
        <Ionicons name="phone-portrait-outline" size={30} color={COLORS.orange} />
      </View>
      <Text style={[styles.centerTitle, { color: theme.text }]}>Unlock the 3-Month view</Text>
      <Text style={[styles.centerText, { color: theme.muted }]}>
        This screen uses Android UsageStats to show phone behaviour patterns. The data stays on this device.
      </Text>
      <TouchableOpacity
        testID="stats-usage-access-button"
        style={[styles.permissionButton, { backgroundColor: COLORS.orange }]}
        onPress={() => {
          void UsageStatsModule.openUsageAccessSettings().catch(() => Linking.openSettings());
        }}
        activeOpacity={0.82}
      >
        <Ionicons name="settings-outline" size={17} color={COLORS.card} />
        <Text style={[styles.permissionButtonText, { color: COLORS.card }]}>Grant Usage Access</Text>
      </TouchableOpacity>
    </View>
  );
}

function LocalOnlyNotice({
  theme,
  onDismiss,
}: {
  theme: ReturnType<typeof useTheme>['theme'];
  onDismiss: () => void;
}) {
  return (
    <View style={[styles.privacyNotice, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <Ionicons name="lock-closed-outline" size={19} color={COLORS.green} />
      <View style={styles.privacyNoticeCopy}>
        <Text style={[styles.privacyNoticeTitle, { color: theme.text }]}>Your data stays here</Text>
        <Text style={[styles.privacyNoticeBody, { color: theme.textSecondary }]}>
          FocusFlow does not collect, upload, or share your analytics. This view is calculated on this device only.
        </Text>
      </View>
      <TouchableOpacity
        testID="stats-privacy-notice-dismiss"
        accessibilityRole="button"
        accessibilityLabel="Dismiss privacy notice"
        onPress={onDismiss}
        hitSlop={10}
      >
        <Ionicons name="close" size={19} color={theme.muted} />
      </TouchableOpacity>
    </View>
  );
}

function InsightCardView({
  insight,
  theme,
}: {
  insight: InsightCard;
  theme: ReturnType<typeof useTheme>['theme'];
}) {
  const accent = insight.sentiment === 'positive'
    ? COLORS.green
    : insight.sentiment === 'warning'
      ? COLORS.orange
      : COLORS.primary;
  return (
    <View style={[styles.insightCard, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <View style={styles.insightHeader}>
        <View style={[styles.insightDot, { backgroundColor: accent }]} />
        <Text style={[styles.insightCategory, { color: accent }]}>
          {insight.category === 'nothing_to_report' ? 'OBSERVATION' : insight.category.toUpperCase()}
        </Text>
      </View>
      <Text style={[styles.insightHeadline, { color: theme.text }]}>{insight.headline}</Text>
      <Text style={[styles.insightBody, { color: theme.textSecondary }]}>{insight.body}</Text>
    </View>
  );
}

function PresenceStrip({
  snapshot,
  weekStartDay,
  theme,
}: {
  snapshot: AnalyticsSnapshot;
  weekStartDay: number;
  theme: ReturnType<typeof useTheme>['theme'];
}) {
  return (
    <View style={[styles.section, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <Text style={[styles.sectionLabel, { color: theme.muted }]}>PRESENCE</Text>
      <View style={styles.presenceRow}>
        {Array.from({ length: 7 }, (_, index) => {
          const day = (weekStartDay + index) % 7;
          const attended = snapshot.tasks.byDayOfWeek[day].total > 0;
          return (
            <View key={day} style={styles.presenceItem}>
              <View style={[styles.presenceBox, { backgroundColor: attended ? COLORS.green : theme.surface, borderColor: attended ? COLORS.green : theme.border }]}>
                {attended && <Ionicons name="checkmark" size={14} color={COLORS.card} />}
              </View>
              <Text style={[styles.presenceLabel, { color: theme.muted }]}>
                {dayjs().day(day).format('dd')}
              </Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}

function TrendChart({
  snapshot,
  theme,
}: {
  snapshot: AnalyticsSnapshot;
  theme: ReturnType<typeof useTheme>['theme'];
}) {
  const weeks = snapshot.trends?.weekByWeek ?? [];
  return (
    <View style={[styles.section, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <Text style={[styles.sectionLabel, { color: theme.muted }]}>COMPLETION OVER 12 WEEKS</Text>
      <Text style={[styles.chartIntro, { color: theme.text }]}>The line is the pattern. The cards above explain it.</Text>
      <View style={styles.chart}>
        {weeks.map((week) => (
          <View key={week.weekStart} style={styles.chartColumn}>
            <View style={[styles.chartTrack, { backgroundColor: theme.surface }]}>
              <View style={[styles.chartBar, { height: `${Math.max(3, week.completionRate * 100)}%`, backgroundColor: COLORS.primary }]} />
            </View>
            <Text style={[styles.chartLabel, { color: theme.muted }]}>
              {weeks.length <= 6 || week === weeks[weeks.length - 1] ? dayjs(week.weekStart).format('M/D') : ''}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function TaskSummary({
  snapshot,
  theme,
}: {
  snapshot: AnalyticsSnapshot;
  theme: ReturnType<typeof useTheme>['theme'];
}) {
  const rows = [
    { label: 'Done', value: snapshot.tasks.completed, color: COLORS.green },
    { label: 'Skipped', value: snapshot.tasks.skipped, color: COLORS.orange },
    { label: 'Missed', value: snapshot.tasks.missed, color: COLORS.red },
  ];
  return (
    <View style={[styles.section, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <Text style={[styles.sectionLabel, { color: theme.muted }]}>TASKS</Text>
      <View style={styles.taskRow}>
        {rows.map((row) => (
          <View key={row.label} style={styles.taskMetric}>
            <Text style={[styles.taskValue, { color: row.color }]}>{row.value}</Text>
            <Text style={[styles.taskLabel, { color: theme.muted }]}>{row.label}</Text>
          </View>
        ))}
      </View>
      {snapshot.tasks.total === 0 && (
        <Text style={[styles.emptyText, { color: theme.muted }]}>No tasks were recorded in this window.</Text>
      )}
    </View>
  );
}

function AchievementRow({
  state,
  theme,
}: {
  state: AchievementState;
  theme: ReturnType<typeof useTheme>['theme'];
}) {
  if (state.definitions.length === 0) return null;
  return (
    <View style={[styles.section, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <View style={styles.achievementHeading}>
        <Text style={[styles.sectionLabel, { color: theme.muted }]}>ACHIEVEMENTS</Text>
        {state.newlyEarnedIds.length > 0 && (
          <Text style={[styles.newAchievementLabel, { color: COLORS.green }]}>NEW</Text>
        )}
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.achievementRow}>
        {state.definitions.map((achievement) => (
          <View key={achievement.id} style={[styles.achievementChip, { backgroundColor: theme.surface, borderColor: theme.border }]}>
            <Ionicons name={achievement.icon as keyof typeof Ionicons.glyphMap} size={20} color={COLORS.primary} />
            <Text style={[styles.achievementTitle, { color: theme.text }]}>{achievement.title}</Text>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerCopy: { gap: 2 },
  title: { fontSize: FONT.xl, fontWeight: '900' },
  subtitle: { fontSize: FONT.xs },
  switcher: {
    flexDirection: 'row',
    gap: SPACING.xs,
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  switchButton: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: SPACING.sm,
    borderRadius: RADIUS.md,
    borderWidth: 1,
  },
  switchLabel: { fontSize: FONT.xs, fontWeight: '800' },
  privacyNotice: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginHorizontal: SPACING.lg,
    marginTop: SPACING.sm,
    padding: SPACING.md,
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    gap: SPACING.sm,
  },
  privacyNoticeCopy: { flex: 1, gap: 2 },
  privacyNoticeTitle: { fontSize: FONT.sm, fontWeight: '900' },
  privacyNoticeBody: { fontSize: FONT.xs, lineHeight: 17 },
  scroll: { flex: 1 },
  content: { padding: SPACING.lg, gap: SPACING.md },
  insightStack: { gap: SPACING.sm },
  insightCard: {
    padding: SPACING.lg,
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    gap: SPACING.xs,
  },
  insightHeader: { flexDirection: 'row', alignItems: 'center', gap: SPACING.xs },
  insightDot: { width: 7, height: 7, borderRadius: RADIUS.full },
  insightCategory: { fontSize: FONT.xs, fontWeight: '900', letterSpacing: 0.8 },
  insightHeadline: { fontSize: FONT.lg, fontWeight: '900', lineHeight: 24 },
  insightBody: { fontSize: FONT.md, lineHeight: 22 },
  section: { padding: SPACING.lg, borderRadius: RADIUS.lg, borderWidth: 1, gap: SPACING.md },
  sectionLabel: { fontSize: FONT.xs, fontWeight: '900', letterSpacing: 1 },
  achievementHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  newAchievementLabel: { fontSize: FONT.xs, fontWeight: '900', letterSpacing: 1 },
  achievementRow: { gap: SPACING.sm },
  achievementChip: { minWidth: 108, alignItems: 'center', gap: SPACING.xs, padding: SPACING.md, borderRadius: RADIUS.md, borderWidth: 1 },
  achievementTitle: { fontSize: FONT.xs, fontWeight: '800', textAlign: 'center' },
  presenceRow: { flexDirection: 'row', justifyContent: 'space-between' },
  presenceItem: { alignItems: 'center', gap: SPACING.xs },
  presenceBox: { width: 30, height: 30, borderRadius: RADIUS.sm, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  presenceLabel: { fontSize: FONT.xs, fontWeight: '700' },
  chartIntro: { fontSize: FONT.sm },
  chart: { height: 142, flexDirection: 'row', alignItems: 'flex-end', gap: SPACING.xs },
  chartColumn: { flex: 1, height: '100%', alignItems: 'center', justifyContent: 'flex-end', gap: SPACING.xs },
  chartTrack: { width: '100%', maxWidth: 16, height: 110, borderRadius: RADIUS.sm, overflow: 'hidden', justifyContent: 'flex-end' },
  chartBar: { width: '100%', borderRadius: RADIUS.sm, minHeight: 3 },
  chartLabel: { fontSize: 9, height: 12 },
  taskRow: { flexDirection: 'row', justifyContent: 'space-around' },
  taskMetric: { alignItems: 'center', gap: 2 },
  taskValue: { fontSize: FONT.xxl, fontWeight: '900' },
  taskLabel: { fontSize: FONT.xs, fontWeight: '700' },
  emptyText: { fontSize: FONT.sm, textAlign: 'center' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: SPACING.xxl, gap: SPACING.sm },
  centerTitle: { fontSize: FONT.lg, fontWeight: '900', textAlign: 'center' },
  centerText: { fontSize: FONT.sm, lineHeight: 21, textAlign: 'center', maxWidth: 320 },
  permissionIcon: { width: 64, height: 64, borderRadius: RADIUS.full, alignItems: 'center', justifyContent: 'center', marginBottom: SPACING.sm },
  permissionButton: { flexDirection: 'row', alignItems: 'center', gap: SPACING.xs, paddingHorizontal: SPACING.lg, paddingVertical: SPACING.md, borderRadius: RADIUS.md, marginTop: SPACING.sm },
  permissionButtonText: { fontSize: FONT.sm, fontWeight: '900' },
});