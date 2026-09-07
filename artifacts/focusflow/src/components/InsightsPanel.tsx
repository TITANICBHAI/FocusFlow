import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import dayjs from 'dayjs';
import type { Task } from '@/data/types';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { useTheme } from '@/hooks/useTheme';

interface InsightsPanelProps {
  tasks: Task[];
  previousTasks?: Task[];
}

function completionRate(tasks: Task[]): number {
  return tasks.length === 0
    ? 0
    : tasks.filter((task) => task.status === 'completed').length / tasks.length;
}

function formatHour(hour: number): string {
  const suffix = hour >= 12 ? 'PM' : 'AM';
  const displayHour = hour % 12 || 12;
  return `${displayHour} ${suffix}`;
}

export function InsightsPanel({ tasks, previousTasks = [] }: InsightsPanelProps) {
  const { theme } = useTheme();

  const summary = useMemo(() => {
    const completed = tasks.filter((task) => task.status === 'completed');
    const hourCounts = new Map<number, number>();
    for (const task of completed) {
      const hour = dayjs(task.startTime).hour();
      hourCounts.set(hour, (hourCounts.get(hour) ?? 0) + 1);
    }
    const bestHour = [...hourCounts.entries()].sort((a, b) => b[1] - a[1] || a[0] - b[0])[0]?.[0] ?? null;

    const dayStats = new Map<string, { total: number; completed: number }>();
    for (const task of tasks) {
      const date = dayjs(task.startTime).format('YYYY-MM-DD');
      const current = dayStats.get(date) ?? { total: 0, completed: 0 };
      current.total++;
      if (task.status === 'completed') current.completed++;
      dayStats.set(date, current);
    }
    const strongestDay = [...dayStats.entries()]
      .map(([date, stats]) => ({ date, rate: stats.completed / stats.total, total: stats.total }))
      .sort((a, b) => b.rate - a.rate || b.total - a.total || a.date.localeCompare(b.date))[0];

    const trendPoints = Math.round((completionRate(tasks) - completionRate(previousTasks)) * 100);
    return { bestHour, strongestDay, trendPoints };
  }, [previousTasks, tasks]);

  const trendColor = summary.trendPoints > 0 ? COLORS.green : summary.trendPoints < 0 ? COLORS.red : theme.muted;
  const trendIcon = summary.trendPoints > 0 ? 'trending-up' : summary.trendPoints < 0 ? 'trending-down' : 'remove-outline';
  const trendLabel = previousTasks.length === 0
    ? 'No prior week'
    : `${summary.trendPoints > 0 ? '↑' : summary.trendPoints < 0 ? '↓' : '→'} ${Math.abs(summary.trendPoints)}% vs last week`;

  return (
    <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <View style={styles.headingRow}>
        <Ionicons name="sparkles-outline" size={16} color={COLORS.primary} />
        <Text style={[styles.title, { color: theme.text }]}>At a glance</Text>
      </View>
      <View style={styles.rows}>
        <View style={styles.row}>
          <Ionicons name="time-outline" size={18} color={COLORS.primary} />
          <Text style={[styles.label, { color: theme.muted }]}>Sharpest hour</Text>
          <Text style={[styles.value, { color: theme.text }]}>
            {summary.bestHour === null ? 'Not enough data' : `${formatHour(summary.bestHour)}`}
          </Text>
        </View>
        <View style={styles.row}>
          <Ionicons name="calendar-outline" size={18} color={COLORS.orange} />
          <Text style={[styles.label, { color: theme.muted }]}>Best day</Text>
          <Text style={[styles.value, { color: theme.text }]}>
            {summary.strongestDay ? dayjs(summary.strongestDay.date).format('dddd') : 'Not enough data'}
          </Text>
        </View>
        <View style={styles.row}>
          <Ionicons name={trendIcon as any} size={18} color={trendColor} />
          <Text style={[styles.label, { color: theme.muted }]}>Completion trend</Text>
          <Text style={[styles.value, { color: trendColor }]}>{trendLabel}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    padding: SPACING.md,
    gap: SPACING.sm,
  },
  headingRow: { flexDirection: 'row', alignItems: 'center', gap: SPACING.xs },
  title: { fontSize: FONT.md, fontWeight: '800' },
  rows: { gap: SPACING.sm },
  row: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm },
  label: { flex: 1, fontSize: FONT.sm },
  value: { fontSize: FONT.sm, fontWeight: '800', textAlign: 'right' },
});
