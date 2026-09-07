import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import { useApp } from '@/context/AppContext';
import { useTheme } from '@/hooks/useTheme';
import {
  buildRestoreCallbacks,
  parseBackupJson,
  restoreFromJson,
} from '@/services/backupService';
import {
  consumeBackupImport,
  discardBackupImport,
  peekBackupImport,
} from '@/services/pendingBackupImport';
import { scheduleTaskRemindersBatch } from '@/services/notificationService';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

export default function ImportConfirmScreen() {
  const params = useLocalSearchParams<{ id?: string }>();
  const importId = Array.isArray(params.id) ? params.id[0] : params.id;
  const { theme } = useTheme();
  const { state, addTask, deleteTask, refreshTasks, updateSettings } = useApp();
  const [replaceTasks, setReplaceTasks] = useState(false);
  const [busy, setBusy] = useState(false);

  const content = importId ? peekBackupImport(importId) : null;
  const parsed = useMemo(
    () => (content ? parseBackupJson(content) : { ok: false as const, error: 'This import has expired.' }),
    [content],
  );

  const close = () => {
    if (importId) discardBackupImport(importId);
    router.back();
  };

  const handleImport = async () => {
    if (!importId || !content || !parsed.ok || busy) return;
    setBusy(true);
    try {
      const result = await restoreFromJson(
        content,
        buildRestoreCallbacks(
          {
            updateSettings,
            addTask,
            scheduleTasks: scheduleTaskRemindersBatch,
            deleteTask,
            refreshTasks,
            currentTasks: state.tasks,
            currentSettings: state.settings,
            currentFocusSession: state.focusSession,
          },
          replaceTasks,
        ),
      );

      if ('error' in result) {
        Alert.alert('Import failed', result.error);
        return;
      }

      consumeBackupImport(importId);
      const warningText = result.warnings.length > 0
        ? `\n\nWarnings:\n${result.warnings.join('\n')}`
        : '';
      Alert.alert(
        'Backup imported',
        `${result.tasksImported} task${result.tasksImported === 1 ? '' : 's'} added. ` +
          `${result.tasksSkipped} skipped.${warningText}`,
        [{ text: 'Done', onPress: () => router.back() }],
      );
    } catch (error) {
      Alert.alert('Import failed', String(error));
    } finally {
      setBusy(false);
    }
  };

  if (!parsed.ok) {
    return (
      <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
        <View style={styles.centered}>
          <Ionicons name="document-text-outline" size={42} color={theme.muted} />
          <Text style={[styles.title, { color: theme.text }]}>Import unavailable</Text>
          <Text style={[styles.body, { color: theme.muted }]}>{parsed.error}</Text>
          <TouchableOpacity
            accessibilityRole="button"
            style={[styles.secondaryButton, { borderColor: theme.border }]}
            onPress={close}
          >
            <Text style={[styles.secondaryButtonText, { color: theme.text }]}>Close</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const { envelope } = parsed;
  const taskCount = envelope.summary?.taskCount ?? envelope.tasks.length;
  const blockedWordCount = envelope.summary?.blockedWordCount ??
    envelope.settings.blockedWords?.length ?? 0;
  const settingsCount = Object.keys(envelope.settings).length;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
      <View style={[styles.header, { borderBottomColor: theme.border }]}>
        <TouchableOpacity
          accessibilityLabel="Cancel import"
          accessibilityRole="button"
          onPress={close}
          style={styles.iconButton}
        >
          <Ionicons name="close" size={24} color={theme.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: theme.text }]}>Import backup</Text>
        <View style={styles.iconButton} />
      </View>

      <ScrollView
        contentContainerStyle={[styles.content, { paddingBottom: SPACING.xxl }]}
        showsVerticalScrollIndicator={false}
      >
        <View style={[styles.hero, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <View style={styles.heroIcon}>
            <Ionicons name="cloud-download-outline" size={28} color={COLORS.primary} />
          </View>
          <Text style={[styles.title, { color: theme.text }]}>FocusFlow backup</Text>
          <Text style={[styles.body, { color: theme.muted }]}>
            Review what will be brought onto this device before anything changes.
          </Text>
        </View>

        <Text style={[styles.sectionTitle, { color: theme.textSecondary }]}>This file contains</Text>
        <View style={[styles.summaryCard, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <SummaryRow icon="list-outline" label="Tasks" value={String(taskCount)} theme={theme} />
          <SummaryRow icon="settings-outline" label="Settings fields" value={String(settingsCount)} theme={theme} />
          <SummaryRow icon="ban-outline" label="Blocked words" value={String(blockedWordCount)} theme={theme} />
        </View>

        <Text style={[styles.sectionTitle, { color: theme.textSecondary }]}>Import behavior</Text>
        <View style={[styles.choiceCard, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <View style={styles.choiceText}>
            <Text style={[styles.choiceTitle, { color: theme.text }]}>
              {replaceTasks ? 'Replace all existing tasks' : 'Merge with existing tasks'}
            </Text>
            <Text style={[styles.body, { color: theme.muted }]}>
              {replaceTasks
                ? 'Current tasks will be deleted before the backup tasks are restored. Settings are still merged.'
                : 'Existing task IDs are kept. New tasks and portable settings are added without deleting current data.'}
            </Text>
          </View>
          <Switch
            accessibilityLabel="Replace existing tasks"
            value={replaceTasks}
            onValueChange={setReplaceTasks}
            trackColor={{ false: theme.border, true: COLORS.red + '88' }}
            thumbColor={replaceTasks ? COLORS.red : theme.muted}
          />
        </View>

        {replaceTasks && (
          <View style={[styles.warning, { backgroundColor: COLORS.red + '14', borderColor: COLORS.red + '55' }]}>
            <Ionicons name="warning-outline" size={18} color={COLORS.red} />
            <Text style={[styles.warningText, { color: theme.text }]}>
              Replace is destructive. It is unavailable while a focus session is active.
            </Text>
          </View>
        )}

        <TouchableOpacity
          accessibilityRole="button"
          accessibilityLabel={replaceTasks ? 'Replace tasks and import backup' : 'Merge and import backup'}
          style={[styles.importButton, { backgroundColor: replaceTasks ? COLORS.red : COLORS.primary }]}
          onPress={() => void handleImport()}
          disabled={busy}
        >
          {busy ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.importButtonText}>
              {replaceTasks ? 'Replace & Import' : 'Merge & Import'}
            </Text>
          )}
        </TouchableOpacity>
        <TouchableOpacity
          accessibilityRole="button"
          style={[styles.cancelButton, { borderColor: theme.border }]}
          onPress={close}
          disabled={busy}
        >
          <Text style={[styles.cancelButtonText, { color: theme.text }]}>Cancel</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

function SummaryRow({
  icon,
  label,
  value,
  theme,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  value: string;
  theme: { text: string; muted: string; border: string };
}) {
  return (
    <View style={[styles.summaryRow, { borderBottomColor: theme.border }]}>
      <Ionicons name={icon} size={19} color={COLORS.primary} />
      <Text style={[styles.summaryLabel, { color: theme.text }]}>{label}</Text>
      <Text style={[styles.summaryValue, { color: theme.muted }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: {
    minHeight: 58,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: SPACING.md,
  },
  iconButton: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: { fontSize: FONT.lg, fontWeight: '800' },
  content: { padding: SPACING.lg, gap: SPACING.md },
  hero: {
    borderWidth: 1,
    borderRadius: RADIUS.lg,
    padding: SPACING.xl,
    alignItems: 'center',
    gap: SPACING.sm,
  },
  heroIcon: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: COLORS.primary + '18',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: SPACING.xs,
  },
  title: { fontSize: FONT.xl, fontWeight: '800', textAlign: 'center' },
  body: { fontSize: FONT.sm, lineHeight: 20, textAlign: 'center' },
  sectionTitle: {
    fontSize: FONT.sm,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginTop: SPACING.sm,
  },
  summaryCard: { borderWidth: 1, borderRadius: RADIUS.md, paddingHorizontal: SPACING.md },
  summaryRow: {
    minHeight: 52,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
  },
  summaryLabel: { flex: 1, fontSize: FONT.md, fontWeight: '600' },
  summaryValue: { fontSize: FONT.md, fontWeight: '700' },
  choiceCard: {
    borderWidth: 1,
    borderRadius: RADIUS.md,
    padding: SPACING.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.md,
  },
  choiceText: { flex: 1, gap: SPACING.xs },
  choiceTitle: { fontSize: FONT.md, fontWeight: '700' },
  warning: {
    borderWidth: 1,
    borderRadius: RADIUS.md,
    padding: SPACING.md,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: SPACING.sm,
  },
  warningText: { flex: 1, fontSize: FONT.sm, lineHeight: 20 },
  importButton: {
    minHeight: 52,
    borderRadius: RADIUS.md,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: SPACING.sm,
  },
  importButtonText: { color: '#fff', fontSize: FONT.md, fontWeight: '800' },
  cancelButton: {
    minHeight: 50,
    borderWidth: 1,
    borderRadius: RADIUS.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelButtonText: { fontSize: FONT.md, fontWeight: '700' },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: SPACING.xl,
    gap: SPACING.md,
  },
  secondaryButton: {
    minWidth: 140,
    minHeight: 48,
    borderWidth: 1,
    borderRadius: RADIUS.md,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: SPACING.sm,
  },
  secondaryButtonText: { fontSize: FONT.md, fontWeight: '700' },
});