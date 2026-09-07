import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import dayjs, { Dayjs } from 'dayjs';
import { useTheme } from '@/hooks/useTheme';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { withScreenErrorBoundary } from '@/components/withScreenErrorBoundary';
import {
  dbGetReportNote,
  dbGetTasksInDateRange,
  dbGetWeekReportNotes,
  dbSaveReportNote,
} from '@/data/database';
import { computeDailyAnalysis, computeWeeklyAnalysis } from '@/services/insightEngine';
import { getWeekEnd, getWeekStart } from '@/utils/weekUtils';
import type { Task } from '@/data/types';

type ReportType = 'day' | 'week';

function paramValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function ReportScreen() {
  const insets = useSafeAreaInsets();
  const { theme } = useTheme();
  const params = useLocalSearchParams<{ type?: string; date?: string; refDate?: string }>();
  const type: ReportType = paramValue(params.type) === 'week' ? 'week' : 'day';
  const requestedDate = paramValue(params.refDate) ?? paramValue(params.date);
  const anchor = useMemo(() => {
    const parsed = requestedDate ? dayjs(requestedDate) : dayjs().subtract(1, 'day');
    return parsed.isValid() ? parsed : dayjs().subtract(1, 'day');
  }, [requestedDate]);

  const range = useMemo(() => {
    if (type === 'week') {
      const start = getWeekStart(0, anchor);
      const end = getWeekEnd(start);
      return { start, end, label: `${start.format('MMM D')} – ${end.format('MMM D, YYYY')}` };
    }
    return {
      start: anchor.startOf('day'),
      end: anchor.endOf('day'),
      label: anchor.format('dddd, MMMM D, YYYY'),
    };
  }, [anchor, type]);

  const baseline = useMemo(() => {
    if (type === 'week') {
      return {
        start: range.start.subtract(7, 'day'),
        end: range.start.subtract(1, 'day').endOf('day'),
      };
    }
    return {
      start: range.start.subtract(30, 'day').startOf('day'),
      end: range.start.subtract(1, 'day').endOf('day'),
    };
  }, [range.start, type]);

  const [loading, setLoading] = useState(true);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [baselineTasks, setBaselineTasks] = useState<Task[]>([]);
  const [weekNotes, setWeekNotes] = useState<Record<string, string>>({});
  const [note, setNote] = useState('');
  const [savedNote, setSavedNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const refDate = range.start.format('YYYY-MM-DD');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [reportTasks, comparisonTasks, existingNote, existingWeekNote] = await Promise.all([
        dbGetTasksInDateRange(range.start.toISOString(), range.end.toISOString()),
        dbGetTasksInDateRange(baseline.start.toISOString(), baseline.end.toISOString()),
        type === 'week'
          ? dbGetWeekReportNotes(refDate)
          : dbGetReportNote(refDate, 'day'),
        type === 'week' ? dbGetReportNote(refDate, 'week') : Promise.resolve(null),
      ]);
      setTasks(reportTasks);
      setBaselineTasks(comparisonTasks);
      const text = type === 'week'
        ? (existingWeekNote as string | null) ?? ''
        : (existingNote as string | null) ?? '';
      setWeekNotes(type === 'week' ? existingNote as Record<string, string> : {});
      setNote(text);
      setSavedNote(text);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Could not load this report.');
    } finally {
      setLoading(false);
    }
  }, [baseline, range.end, range.start, refDate, type]);

  useEffect(() => { void load(); }, [load]);

  const analysis = useMemo(
    () => type === 'week'
      ? computeWeeklyAnalysis(tasks, baselineTasks)
      : computeDailyAnalysis(tasks, baselineTasks),
    [baselineTasks, tasks, type],
  );

  const completed = tasks.filter((task) => task.status === 'completed');
  const skipped = tasks.filter((task) => task.status === 'skipped');
  const focusMinutes = completed.filter((task) => task.focusMode).reduce((sum, task) => sum + task.durationMinutes, 0);
  const completionRate = tasks.length > 0 ? Math.round((completed.length / tasks.length) * 100) : 0;
  const bestDay = useMemo(() => {
    if (type !== 'week') return null;
    const rows = buildTimeline(range.start, range.end, tasks);
    return rows
      .filter((row) => row.tasks.length > 0)
      .map((row) => ({
        date: row.date,
        rate: row.tasks.filter((task) => task.status === 'completed').length / row.tasks.length,
      }))
      .sort((a, b) => b.rate - a.rate || a.date.valueOf() - b.date.valueOf())[0]?.date ?? null;
  }, [range.start, tasks, type]);
  const saveNote = useCallback(async () => {
    const trimmed = note.trim();
    if (trimmed === savedNote) return;
    await dbSaveReportNote(refDate, type, trimmed);
    setSavedNote(trimmed);
  }, [note, refDate, savedNote, type]);

  const timeline = useMemo(() => {
    return buildTimeline(range.start, range.end, tasks);
  }, [range.start, tasks, type]);

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <View style={[styles.header, { borderBottomColor: theme.border }]}>
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="Go back">
          <Ionicons name="chevron-back" size={26} color={theme.text} />
        </TouchableOpacity>
        <View style={styles.headerCopy}>
          <Text style={[styles.eyebrow, { color: COLORS.primary }]}>{type === 'week' ? 'WEEKLY REPORT' : 'DAILY REPORT'}</Text>
          <Text style={[styles.headerTitle, { color: theme.text }]}>{range.label}</Text>
        </View>
        <View style={{ width: 26 }} />
      </View>

      {loading ? (
        <View style={styles.center}><ActivityIndicator size="large" color={COLORS.primary} /></View>
      ) : error ? (
        <View style={styles.center}>
          <Ionicons name="alert-circle-outline" size={44} color={COLORS.red} />
          <Text style={[styles.errorText, { color: theme.text }]}>{error}</Text>
          <TouchableOpacity style={[styles.retryButton, { backgroundColor: COLORS.primary }]} onPress={() => void load()}>
            <Text style={styles.retryText}>Try again</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <ScrollView
            contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + SPACING.xxl }]}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            <View style={[styles.hero, { backgroundColor: theme.card, borderColor: COLORS.primary + '30' }]}>
              <Text style={[styles.heroLabel, { color: theme.muted }]}>THE TAKEAWAY</Text>
              <Text style={[styles.headline, { color: theme.text }]}>{analysis.headline}</Text>
              <View style={styles.summaryRow}>
                <SummaryStat value={String(tasks.length)} label="tasks" color={COLORS.blue} theme={theme} />
                {type === 'week' ? (
                  <>
                    <SummaryStat value={`${completionRate}%`} label="complete" color={COLORS.green} theme={theme} />
                    <SummaryStat value={`${focusMinutes}m`} label="focus" color={COLORS.primary} theme={theme} />
                    {bestDay && <SummaryStat value={bestDay.format('ddd')} label="best day" color={COLORS.orange} theme={theme} />}
                  </>
                ) : (
                  <>
                    <SummaryStat value={String(completed.length)} label="done" color={COLORS.green} theme={theme} />
                    <SummaryStat value={String(skipped.length)} label="skipped" color={COLORS.orange} theme={theme} />
                    <SummaryStat value={`${focusMinutes}m`} label="focus" color={COLORS.primary} theme={theme} />
                  </>
                )}
              </View>
            </View>

            <View style={[styles.card, { backgroundColor: theme.card }]}>
              <Text style={[styles.sectionTitle, { color: theme.text }]}>What stands out</Text>
              {analysis.insights.length === 0 ? (
                <Text style={[styles.emptyText, { color: theme.muted }]}>
                  Not enough variation today to call out a specific pattern — steady as it goes.
                </Text>
              ) : analysis.insights.map((insight) => (
                <View key={insight.id} style={styles.insightRow}>
                  <View style={[styles.insightDot, { backgroundColor: COLORS.primary }]} />
                  <Text style={[styles.insightText, { color: theme.text }]}>{insight.text}</Text>
                </View>
              ))}
            </View>

            <View style={[styles.card, { backgroundColor: theme.card }]}>
              <Text style={[styles.sectionTitle, { color: theme.text }]}>Your note</Text>
              <Text style={[styles.noteHint, { color: theme.muted }]}>Optional. Saved when you leave the field.</Text>
              <TextInput
                value={note}
                onChangeText={setNote}
                onBlur={() => { void saveNote(); }}
                placeholder="What do you want to remember?"
                placeholderTextColor={theme.muted}
                multiline
                textAlignVertical="top"
                style={[styles.noteInput, { color: theme.text, backgroundColor: theme.surface, borderColor: theme.border }]}
              />
            </View>

            <View style={[styles.card, { backgroundColor: theme.card }]}>
              <Text style={[styles.sectionTitle, { color: theme.text }]}>Task timeline</Text>
              {timeline.map((row) => (
                <View key={row.date.format('YYYY-MM-DD')} style={styles.dayBlock}>
                  {type === 'week' && (
                    <View style={styles.daySummary}>
                      <View style={{ flex: 1 }}>
                        <Text style={[styles.dayHeading, { color: theme.text }]}>{row.date.format('ddd, MMM D')}</Text>
                        <Text style={[styles.dayMeta, { color: theme.muted }]}>
                          {row.tasks.filter((task) => task.status === 'completed').length}/{row.tasks.length} complete · {row.tasks.filter((task) => task.status === 'completed' && task.focusMode).reduce((sum, task) => sum + task.durationMinutes, 0)}m focus
                        </Text>
                      </View>
                      {weekNotes[row.date.format('YYYY-MM-DD')] && (
                        <Text style={[styles.dailyNote, { color: theme.muted }]}>
                          {weekNotes[row.date.format('YYYY-MM-DD')]}
                        </Text>
                      )}
                    </View>
                  )}
                  {row.tasks.length === 0 ? (
                    <Text style={[styles.emptyText, { color: theme.muted }]}>No tasks scheduled.</Text>
                  ) : row.tasks.map((task) => <TaskTimelineRow key={task.id} task={task} theme={theme} />)}
                </View>
              ))}
            </View>
          </ScrollView>
        </KeyboardAvoidingView>
      )}
    </SafeAreaView>
  );
}

function buildTimeline(start: Dayjs, end: Dayjs, tasks: Task[]): Array<{ date: Dayjs; tasks: Task[] }> {
  if (start.isSame(end, 'day')) return [{ date: start, tasks }];
  const rows: Array<{ date: Dayjs; tasks: Task[] }> = [];
  const isCurrentWeek = start.isBefore(dayjs().endOf('day')) && end.isAfter(dayjs().startOf('day'));
  const lastOffset = isCurrentWeek ? Math.min(6, Math.max(0, dayjs().startOf('day').diff(start, 'day'))) : 6;
  for (let offset = 0; offset <= lastOffset; offset++) {
    const date = start.add(offset, 'day');
    rows.push({
      date,
      tasks: tasks.filter((task) => dayjs(task.startTime).format('YYYY-MM-DD') === date.format('YYYY-MM-DD')),
    });
  }
  return rows;
}

function SummaryStat({ value, label, color, theme }: { value: string; label: string; color: string; theme: any }) {
  return (
    <View style={styles.summaryStat}>
      <Text style={[styles.summaryValue, { color }]}>{value}</Text>
      <Text style={[styles.summaryLabel, { color: theme.muted }]}>{label}</Text>
    </View>
  );
}

function TaskTimelineRow({ task, theme }: { task: Task; theme: any }) {
  const isCompleted = task.status === 'completed';
  const statusColor = isCompleted ? COLORS.green : task.status === 'skipped' ? COLORS.orange : theme.muted;
  return (
    <View style={[styles.taskRow, { borderBottomColor: theme.border }]}>
      <Ionicons
        name={isCompleted ? 'checkmark-circle' : task.status === 'skipped' ? 'remove-circle-outline' : 'ellipse-outline'}
        size={19}
        color={statusColor}
      />
      <View style={styles.taskCopy}>
        <Text style={[styles.taskTitle, { color: theme.text }]} numberOfLines={1}>{task.title}</Text>
        <Text style={[styles.taskMeta, { color: theme.muted }]}>
          {dayjs(task.startTime).format('h:mm A')} · {task.durationMinutes}m
        </Text>
      </View>
      <Text style={[styles.taskStatus, { color: statusColor }]}>{task.status}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  flex: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: SPACING.xl, gap: SPACING.md },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: SPACING.md, paddingVertical: SPACING.sm, borderBottomWidth: StyleSheet.hairlineWidth },
  headerCopy: { flex: 1, alignItems: 'center', gap: 2 },
  eyebrow: { fontSize: FONT.xs, fontWeight: '800', letterSpacing: 1 },
  headerTitle: { fontSize: FONT.sm, fontWeight: '700' },
  content: { padding: SPACING.md, gap: SPACING.md },
  hero: { borderRadius: RADIUS.xl, borderWidth: 1.5, padding: SPACING.lg, gap: SPACING.md },
  heroLabel: { fontSize: FONT.xs, fontWeight: '800', letterSpacing: 1, textAlign: 'center' },
  headline: { fontSize: FONT.xl, fontWeight: '900', lineHeight: 29, textAlign: 'center' },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-around' },
  summaryStat: { alignItems: 'center', gap: 2 },
  summaryValue: { fontSize: FONT.lg, fontWeight: '900' },
  summaryLabel: { fontSize: FONT.xs, fontWeight: '600' },
  card: { borderRadius: RADIUS.lg, padding: SPACING.md, gap: SPACING.sm },
  sectionTitle: { fontSize: FONT.md, fontWeight: '800' },
  insightRow: { flexDirection: 'row', alignItems: 'flex-start', gap: SPACING.sm },
  insightDot: { width: 7, height: 7, borderRadius: 4, marginTop: 7 },
  insightText: { flex: 1, fontSize: FONT.sm, lineHeight: 20 },
  emptyText: { fontSize: FONT.sm, lineHeight: 19 },
  noteHint: { fontSize: FONT.xs },
  noteInput: { minHeight: 88, borderWidth: 1, borderRadius: RADIUS.md, padding: SPACING.sm, fontSize: FONT.sm, lineHeight: 20 },
  dayBlock: { gap: SPACING.xs },
  daySummary: { flexDirection: 'row', alignItems: 'flex-start', gap: SPACING.sm, marginTop: SPACING.sm },
  dayHeading: { fontSize: FONT.sm, fontWeight: '800' },
  dayMeta: { fontSize: FONT.xs, marginTop: 2 },
  dailyNote: { flex: 1, fontSize: FONT.xs, fontStyle: 'italic', lineHeight: 17, textAlign: 'right' },
  taskRow: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, paddingVertical: SPACING.sm, borderBottomWidth: StyleSheet.hairlineWidth },
  taskCopy: { flex: 1, gap: 2 },
  taskTitle: { fontSize: FONT.sm, fontWeight: '600' },
  taskMeta: { fontSize: FONT.xs },
  taskStatus: { fontSize: FONT.xs, fontWeight: '700', textTransform: 'capitalize' },
  errorText: { textAlign: 'center', fontSize: FONT.sm },
  retryButton: { borderRadius: RADIUS.full, paddingHorizontal: SPACING.lg, paddingVertical: SPACING.sm },
  retryText: { color: '#fff', fontSize: FONT.sm, fontWeight: '800' },
});

export default withScreenErrorBoundary(ReportScreen, 'Report');