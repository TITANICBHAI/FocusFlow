/**
 * Live status dashboard for every blocking layer in FocusFlow.
 *
 * Active is intentionally a status surface, not a second Defense settings
 * screen. It always shows the six live protection categories and expands only
 * the lists where the user needs more detail.
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router, useFocusEffect } from 'expo-router';
import dayjs from 'dayjs';

import { useApp } from '@/context/AppContext';
import { useTheme } from '@/hooks/useTheme';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { dbGetTodayFocusMinutes, dbGetTodayOverrideCount, dbGetRecentDayCompletions } from '@/data/database';
import { PinVerifyModal } from '@/components/PinVerifyModal';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import { SessionPinModule } from '@/native-modules/SessionPinModule';
import { InstalledAppsModule, type InstalledApp } from '@/native-modules/InstalledAppsModule';
import { NetworkBlockModule, type NetworkBlockStatus } from '@/native-modules/NetworkBlockModule';
import type { DailyAllowanceEntry } from '@/data/types';

type AllowanceUsage = {
  date?: string;
  count?: number;
  usedMs?: number;
  windowStartMs?: number;
};

export default function ActiveScreen() {
  const insets = useSafeAreaInsets();
  const { theme } = useTheme();
  const { state, stopFocusMode, setStandaloneBlockAndAllowance } = useApp();
  const { settings } = state;
  const [expanded, setExpanded] = useState<string | null>(null);
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [allowanceUsage, setAllowanceUsage] = useState<Record<string, AllowanceUsage>>({});
  const [vpnStatus, setVpnStatus] = useState<NetworkBlockStatus | null>(null);
  const [todayStats, setTodayStats] = useState({ completed: 0, total: 0, focusMinutes: 0, blocked: 0 });
  const [defPinVisible, setDefPinVisible] = useState(false);
  const [focusPinVisible, setFocusPinVisible] = useState(false);
  const pendingDefAction = useRef<(() => void) | null>(null);

  const focusActive = state.focusSession?.isActive === true;
  const focusTask = state.focusSession
    ? state.tasks.find((task) => task.id === state.focusSession?.taskId)
    : null;
  const standalonePackages = settings.standaloneBlockPackages ?? [];
  const standaloneUntil = settings.standaloneBlockUntil ? new Date(settings.standaloneBlockUntil) : null;
  const standaloneActive = Boolean(
    standaloneUntil &&
      standalonePackages.length > 0 &&
      standaloneUntil.getTime() > Date.now(),
  );
  const alwaysOnPackages = settings.alwaysOnPackages ?? [];
  const alwaysOnVpnPackages = settings.alwaysOnVpnPackages ?? [];
  const allowanceEntries = settings.dailyAllowanceEntries ?? [];
  const keywords = settings.blockedWords ?? [];
  const vpnPackages = useMemo(
    () => unique([
      ...alwaysOnVpnPackages,
      ...(settings.standaloneVpnPackages ?? []),
    ]),
    [alwaysOnVpnPackages, settings.standaloneVpnPackages],
  );
  const appNames = useMemo(
    () => new Map(apps.map((app) => [app.packageName, app.appName])),
    [apps],
  );

  const refreshLiveData = useCallback(async () => {
    const [rawUsage, status] = await Promise.all([
      SharedPrefsModule.getString('daily_allowance_used').catch(() => null),
      NetworkBlockModule.getNetworkBlockStatus().catch(() => null),
    ]);
    if (rawUsage) {
      try {
        const parsed = JSON.parse(rawUsage) as Record<string, AllowanceUsage>;
        const today = dayjs().format('YYYY-MM-DD');
        setAllowanceUsage(Object.fromEntries(
          Object.entries(parsed).map(([pkg, value]) => [pkg, value.date === today ? value : {}]),
        ));
      } catch {
        setAllowanceUsage({});
      }
    } else {
      setAllowanceUsage({});
    }
    setVpnStatus(status);
  }, []);

  useFocusEffect(
    useCallback(() => {
      let mounted = true;
      const refresh = () => {
        void refreshLiveData();
        void (async () => {
          try {
            const [rows, focusMinutes, blocked] = await Promise.all([
              dbGetRecentDayCompletions(1),
              dbGetTodayFocusMinutes(),
              dbGetTodayOverrideCount(),
            ]);
            if (!mounted) return;
            const todayKey = dayjs().format('YYYY-MM-DD');
            const today = rows.find((row) => row.date === todayKey);
            const total = state.tasks.filter((task) => dayjs(task.startTime).format('YYYY-MM-DD') === todayKey).length;
            setTodayStats({
              completed: today?.completed ?? 0,
              total: today?.total ?? total,
              focusMinutes,
              blocked,
            });
          } catch {
            if (mounted) setTodayStats({ completed: 0, total: 0, focusMinutes: 0, blocked: 0 });
          }
        })();
      };
      refresh();
      const timer = setInterval(refresh, 15_000);
      return () => {
        mounted = false;
        clearInterval(timer);
      };
    }, [refreshLiveData, state.tasks]),
  );

  useEffect(() => {
    let mounted = true;
    InstalledAppsModule.getInstalledApps()
      .then((installed) => { if (mounted) setApps(installed); })
      .catch(() => {});
    return () => { mounted = false; };
  }, []);

  const withDefensePin = (action: () => void) => {
    SharedPrefsModule.getString('defense_pin_hash')
      .then((hash) => {
        if (hash) {
          pendingDefAction.current = action;
          setDefPinVisible(true);
        } else {
          action();
        }
      })
      .catch(() => action());
  };

  const stopFocus = () => {
    SessionPinModule.isPinSet().then((pinSet) => {
      if (pinSet) {
        setFocusPinVisible(true);
      } else {
        Alert.alert('Stop focus session?', 'This ends app blocking for the current task.', [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Stop', style: 'destructive', onPress: () => { void stopFocusMode(); } },
        ]);
      }
    }).catch(() => {});
  };

  const clearStandalone = () => {
    if (standaloneActive) {
      Alert.alert('Block Timer Running', 'The standalone block cannot be cleared until its timer expires.');
      return;
    }
    withDefensePin(() => {
      Alert.alert(
        'Clear standalone apps?',
        `Remove ${standalonePackages.length} app${standalonePackages.length === 1 ? '' : 's'} from the timed block list?`,
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Clear',
            style: 'destructive',
            onPress: () => { void setStandaloneBlockAndAllowance([], null, allowanceEntries); },
          },
        ],
      );
    });
  };

  const alwaysOnActive = (settings.alwaysOnEnforcementEnabled ?? false) && alwaysOnPackages.length > 0;
  const vpnConfigured = vpnPackages.length > 0 || settings.vpnBlockEnabled === true;
  const vpnRunning = vpnStatus?.running === true;
  const nothingActive = !focusActive && !standaloneActive && !alwaysOnActive && allowanceEntries.length === 0 &&
    keywords.length === 0 && !vpnRunning;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <View style={[styles.header, { backgroundColor: theme.card, borderBottomColor: theme.border }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={10}>
          <Ionicons name="chevron-back" size={24} color={theme.text} />
        </TouchableOpacity>
        <View style={styles.headerCopy}>
          <Text style={[styles.title, { color: theme.text }]}>Active</Text>
          <Text style={[styles.subtitle, { color: theme.muted }]}>Live status of your protections</Text>
        </View>
        <View style={[styles.liveDot, { backgroundColor: nothingActive ? theme.muted : COLORS.green }]} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: 40 + insets.bottom }]} showsVerticalScrollIndicator={false}>
        <View style={[styles.summary, { backgroundColor: nothingActive ? theme.card : COLORS.primary + '12', borderColor: nothingActive ? theme.border : COLORS.primary + '35' }]}>
          <Ionicons name={nothingActive ? 'checkmark-circle-outline' : 'pulse-outline'} size={22} color={nothingActive ? COLORS.green : COLORS.primary} />
          <View style={{ flex: 1 }}>
            <Text style={[styles.summaryTitle, { color: theme.text }]}>{nothingActive ? 'Nothing blocking right now' : 'Protection is active'}</Text>
            <Text style={[styles.summaryText, { color: theme.muted }]}>
              {nothingActive ? 'Start Focus or configure a protection layer in Defense.' : 'This page updates automatically while it is open.'}
            </Text>
          </View>
        </View>

        <StatusCard icon="hourglass-outline" color={focusActive ? COLORS.primary : theme.muted} title="Focus Session" status={focusActive ? 'Active' : 'Not active'} theme={theme}>
          {focusActive && focusTask ? (
            <>
              <DetailRow label="Task" value={focusTask.title} theme={theme} />
              <DetailRow label="Ends at" value={dayjs(focusTask.endTime).format('HH:mm')} theme={theme} />
              <TouchableOpacity style={[styles.action, { borderColor: COLORS.red + '55', backgroundColor: COLORS.red + '12' }]} onPress={stopFocus}>
                <Ionicons name="stop-circle-outline" size={16} color={COLORS.red} />
                <Text style={[styles.actionText, { color: COLORS.red }]}>Stop Focus</Text>
              </TouchableOpacity>
            </>
          ) : (
            <EmptyText text="No task-based focus session is running." theme={theme} />
          )}
        </StatusCard>

        <StatusCard icon="ban-outline" color={standaloneActive ? COLORS.red : theme.muted} title="Standalone Block" status={standaloneActive ? 'Active' : 'Not active'} theme={theme}>
          <DetailRow label="Apps" value={standalonePackages.length ? `${standalonePackages.length} blocked` : 'None selected'} theme={theme} />
          <DetailRow label="Until" value={standaloneUntil ? formatDateTime(standaloneUntil) : 'No timer running'} theme={theme} />
          {standaloneActive ? (
            <TouchableOpacity style={[styles.action, { borderColor: theme.border }]} onPress={() => router.push('/(tabs)/focus')}>
              <Ionicons name="add-circle-outline" size={16} color={COLORS.primary} />
              <Text style={[styles.actionText, { color: COLORS.primary }]}>Add time or apps</Text>
            </TouchableOpacity>
          ) : standalonePackages.length > 0 ? (
            <TouchableOpacity style={[styles.action, { borderColor: COLORS.red + '55' }]} onPress={clearStandalone}>
              <Ionicons name="trash-outline" size={16} color={COLORS.red} />
              <Text style={[styles.actionText, { color: COLORS.red }]}>Clear saved apps</Text>
            </TouchableOpacity>
          ) : null}
        </StatusCard>

        <StatusCard icon="infinite-outline" color={alwaysOnActive ? COLORS.orange : theme.muted} title="Always-On Apps" status={alwaysOnActive ? 'Active' : 'Not active'} theme={theme} expandable={alwaysOnPackages.length > 0} expanded={expanded === 'alwaysOn'} onToggle={() => setExpanded(expanded === 'alwaysOn' ? null : 'alwaysOn')}>
          <DetailRow label="Apps" value={alwaysOnPackages.length ? `${alwaysOnPackages.length} blocked continuously` : 'No always-on apps'} theme={theme} />
          {expanded === 'alwaysOn' && <PackageList packages={alwaysOnPackages} appNames={appNames} theme={theme} />}
          <TouchableOpacity style={[styles.action, { borderColor: theme.border }]} onPress={() => router.push('/always-on')}>
            <Ionicons name="settings-outline" size={16} color={COLORS.primary} />
            <Text style={[styles.actionText, { color: COLORS.primary }]}>Manage Always-On apps</Text>
          </TouchableOpacity>
        </StatusCard>

        <StatusCard icon="sunny-outline" color={allowanceEntries.length ? COLORS.orange : theme.muted} title="Daily Allowance" status={allowanceEntries.length ? `${allowanceEntries.length} app${allowanceEntries.length === 1 ? '' : 's'} configured` : 'Not configured'} theme={theme} expandable={allowanceEntries.length > 0} expanded={expanded === 'allowance'} onToggle={() => setExpanded(expanded === 'allowance' ? null : 'allowance')}>
          {allowanceEntries.length === 0 ? (
            <EmptyText text="No per-app daily limits are configured." theme={theme} />
          ) : expanded === 'allowance' ? (
            allowanceEntries.map((entry) => <AllowanceRow key={entry.packageName} entry={entry} usage={allowanceUsage[entry.packageName]} appName={appNames.get(entry.packageName)} theme={theme} />)
          ) : (
            <Text style={[styles.preview, { color: theme.muted }]}>Tap to see usage, remaining allowance, and reset times.</Text>
          )}
          <TouchableOpacity style={[styles.action, { borderColor: theme.border }]} onPress={() => router.push('/(tabs)/defense')}>
            <Ionicons name="settings-outline" size={16} color={COLORS.primary} />
            <Text style={[styles.actionText, { color: COLORS.primary }]}>Manage daily allowance</Text>
          </TouchableOpacity>
        </StatusCard>

        <StatusCard icon="text-outline" color={keywords.length ? COLORS.primary : theme.muted} title="Keyword Blocker" status={keywords.length ? 'Active' : 'Not active'} theme={theme} expandable={keywords.length > 0} expanded={expanded === 'keywords'} onToggle={() => setExpanded(expanded === 'keywords' ? null : 'keywords')}>
          <DetailRow label="Keywords" value={keywords.length ? `${keywords.length} active immediately` : 'No keywords configured'} theme={theme} />
          {expanded === 'keywords' && <View style={styles.chips}>{keywords.map((word) => <View key={word} style={[styles.chip, { backgroundColor: COLORS.primary + '14' }]}><Text style={[styles.chipText, { color: COLORS.primary }]}>{word}</Text></View>)}</View>}
          <TouchableOpacity style={[styles.action, { borderColor: theme.border }]} onPress={() => router.push('/keyword-blocker')}>
            <Ionicons name="create-outline" size={16} color={COLORS.primary} />
            <Text style={[styles.actionText, { color: COLORS.primary }]}>Manage keywords</Text>
          </TouchableOpacity>
        </StatusCard>

        <StatusCard icon="shield-checkmark-outline" color={vpnRunning ? COLORS.green : theme.muted} title="VPN Blocking" status={vpnRunning ? 'Active' : 'Not active'} theme={theme} expandable={vpnPackages.length > 0} expanded={expanded === 'vpn'} onToggle={() => setExpanded(expanded === 'vpn' ? null : 'vpn')}>
          <DetailRow label="Status" value={vpnRunning ? (vpnStatus?.error ? `Running · ${vpnStatus.error}` : vpnStatus.failedPackages.length > 0 ? `Running · ${vpnStatus.failedPackages.length} app${vpnStatus.failedPackages.length === 1 ? '' : 's'} failed` : 'Running normally') : vpnConfigured ? (vpnStatus?.state === 'permission_missing' ? 'Permission required' : 'Configured but stopped') : 'No VPN apps configured'} theme={theme} />
          {vpnPackages.length > 0 && <DetailRow label="Apps" value={`${vpnPackages.length} app${vpnPackages.length === 1 ? '' : 's'} selected`} theme={theme} />}
          {expanded === 'vpn' && <PackageList packages={vpnPackages} appNames={appNames} theme={theme} />}
          <TouchableOpacity style={[styles.action, { borderColor: theme.border }]} onPress={() => router.push('/block-defense?tab=system')}>
            <Ionicons name="settings-outline" size={16} color={COLORS.primary} />
            <Text style={[styles.actionText, { color: COLORS.primary }]}>Manage VPN blocking</Text>
          </TouchableOpacity>
        </StatusCard>

        <View style={[styles.today, { borderTopColor: theme.border }]}>
          <Text style={[styles.todayLabel, { color: theme.muted }]}>TODAY</Text>
          <Text style={[styles.todayText, { color: theme.text }]}>
            {todayStats.completed}/{Math.max(todayStats.total, todayStats.completed)} tasks · {todayStats.focusMinutes}m focus · {todayStats.blocked} blocked attempts
          </Text>
        </View>
      </ScrollView>

      <PinVerifyModal visible={defPinVisible} pinType="defense" title="Defense Password Required" description="Enter your defense password to make this change." onVerified={() => { setDefPinVisible(false); pendingDefAction.current?.(); pendingDefAction.current = null; }} onCancel={() => { setDefPinVisible(false); pendingDefAction.current = null; }} />
      <PinVerifyModal visible={focusPinVisible} pinType="focus" title="Stop Focus Session" description="Enter your focus session password to end the session and stop blocking." onVerified={() => { setFocusPinVisible(false); void stopFocusMode(); }} onCancel={() => setFocusPinVisible(false)} />
    </SafeAreaView>
  );
}

function StatusCard({ icon, color, title, status, theme, children, expandable = false, expanded = false, onToggle }: { icon: keyof typeof Ionicons.glyphMap; color: string; title: string; status: string; theme: ReturnType<typeof useTheme>['theme']; children: React.ReactNode; expandable?: boolean; expanded?: boolean; onToggle?: () => void }) {
  return (
    <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <TouchableOpacity style={styles.cardHeader} onPress={expandable ? onToggle : undefined} activeOpacity={expandable ? 0.7 : 1}>
        <View style={[styles.icon, { backgroundColor: color + '20' }]}><Ionicons name={icon} size={17} color={color} /></View>
        <View style={{ flex: 1 }}>
          <Text style={[styles.cardTitle, { color: theme.text }]}>{title}</Text>
          <Text style={[styles.cardStatus, { color: color === theme.muted ? theme.muted : color }]}>{status}</Text>
        </View>
        {expandable && <Ionicons name={expanded ? 'chevron-up' : 'chevron-down'} size={17} color={theme.muted} />}
      </TouchableOpacity>
      <View style={styles.cardBody}>{children}</View>
    </View>
  );
}

function DetailRow({ label, value, theme }: { label: string; value: string; theme: ReturnType<typeof useTheme>['theme'] }) {
  return <View style={[styles.detailRow, { borderBottomColor: theme.border }]}><Text style={[styles.detailLabel, { color: theme.muted }]}>{label}</Text><Text style={[styles.detailValue, { color: theme.text }]} numberOfLines={2}>{value}</Text></View>;
}

function PackageList({ packages, appNames, theme }: { packages: string[]; appNames: Map<string, string>; theme: ReturnType<typeof useTheme>['theme'] }) {
  return <View style={[styles.packageList, { backgroundColor: theme.surface }]}>{packages.map((pkg) => <Text key={pkg} style={[styles.packageName, { color: theme.text }]}>{appNames.get(pkg) ?? shortPackageName(pkg)}</Text>)}</View>;
}

function AllowanceRow({ entry, usage, appName, theme }: { entry: DailyAllowanceEntry; usage?: AllowanceUsage; appName?: string; theme: ReturnType<typeof useTheme>['theme'] }) {
  const used = entry.mode === 'count' ? (usage?.count ?? 0) : Math.round((usage?.usedMs ?? 0) / 60_000);
  const limit = entry.mode === 'count' ? entry.countPerDay : entry.mode === 'time_budget' ? entry.budgetMinutes : entry.intervalMinutes;
  const unit = entry.mode === 'count' ? 'opens' : 'min';
  const reset = entry.mode === 'interval' ? intervalResetLabel(entry, usage) : 'Resets at midnight';
  return <View style={[styles.allowanceRow, { borderBottomColor: theme.border }]}><Text style={[styles.allowanceName, { color: theme.text }]}>{appName ?? shortPackageName(entry.packageName)}</Text><Text style={[styles.allowanceUsage, { color: used >= limit ? COLORS.red : theme.muted }]}>{used} / {limit} {unit} used · {reset}</Text></View>;
}

function EmptyText({ text, theme }: { text: string; theme: ReturnType<typeof useTheme>['theme'] }) {
  return <Text style={[styles.empty, { color: theme.muted }]}>{text}</Text>;
}

function formatDateTime(date: Date): string {
  return `${dayjs(date).format('MMM D')} at ${dayjs(date).format('HH:mm')}`;
}

function intervalResetLabel(entry: DailyAllowanceEntry, usage?: AllowanceUsage): string {
  if (!usage?.windowStartMs) return 'New window available';
  const remaining = Math.max(0, Math.round((usage.windowStartMs + entry.intervalHours * 3_600_000 - Date.now()) / 60_000));
  return remaining > 0 ? `Resets in ${remaining}m` : 'New window available';
}

function shortPackageName(pkg: string): string {
  const parts = pkg.split('.');
  const last = parts[parts.length - 1] === 'android' ? parts[parts.length - 2] : parts[parts.length - 1];
  return last ? last.charAt(0).toUpperCase() + last.slice(1) : pkg;
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values.filter(Boolean)));
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: SPACING.lg, paddingVertical: SPACING.md, borderBottomWidth: StyleSheet.hairlineWidth },
  headerCopy: { flex: 1, marginLeft: SPACING.sm },
  title: { fontSize: FONT.lg, fontWeight: '800' },
  subtitle: { fontSize: FONT.xs, marginTop: 2 },
  liveDot: { width: 9, height: 9, borderRadius: 5 },
  content: { padding: SPACING.md, gap: SPACING.md },
  summary: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, padding: SPACING.md, borderRadius: RADIUS.md, borderWidth: 1 },
  summaryTitle: { fontSize: FONT.md, fontWeight: '700' },
  summaryText: { fontSize: FONT.xs, marginTop: 3 },
  card: { borderWidth: 1, borderRadius: RADIUS.md, overflow: 'hidden' },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, padding: SPACING.md },
  icon: { width: 30, height: 30, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  cardTitle: { fontSize: FONT.md, fontWeight: '700' },
  cardStatus: { fontSize: FONT.xs, fontWeight: '600', marginTop: 2 },
  cardBody: { paddingHorizontal: SPACING.md, paddingBottom: SPACING.sm },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', gap: SPACING.md, paddingVertical: SPACING.sm, borderBottomWidth: StyleSheet.hairlineWidth },
  detailLabel: { fontSize: FONT.xs, minWidth: 70 },
  detailValue: { flex: 1, textAlign: 'right', fontSize: FONT.xs, fontWeight: '600' },
  empty: { fontSize: FONT.xs, lineHeight: 18, paddingVertical: SPACING.xs },
  preview: { fontSize: FONT.xs, paddingVertical: SPACING.xs },
  action: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACING.xs, paddingVertical: SPACING.sm, marginTop: SPACING.sm, borderRadius: RADIUS.sm, borderWidth: 1 },
  actionText: { fontSize: FONT.xs, fontWeight: '700' },
  packageList: { borderRadius: RADIUS.sm, marginTop: SPACING.sm, paddingHorizontal: SPACING.sm },
  packageName: { fontSize: FONT.xs, paddingVertical: SPACING.xs, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: 'rgba(128,128,128,0.18)' },
  allowanceRow: { paddingVertical: SPACING.sm, borderBottomWidth: StyleSheet.hairlineWidth },
  allowanceName: { fontSize: FONT.sm, fontWeight: '700' },
  allowanceUsage: { fontSize: FONT.xs, marginTop: 3 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: SPACING.xs, paddingVertical: SPACING.xs },
  chip: { borderRadius: RADIUS.sm, paddingHorizontal: SPACING.sm, paddingVertical: 5 },
  chipText: { fontSize: FONT.xs, fontWeight: '600' },
  today: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: SPACING.md, marginTop: SPACING.xs },
  todayLabel: { fontSize: 10, fontWeight: '700', letterSpacing: 1 },
  todayText: { fontSize: FONT.xs, marginTop: 4 },
});