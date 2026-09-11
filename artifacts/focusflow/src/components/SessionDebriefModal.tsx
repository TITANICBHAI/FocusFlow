import React, { useMemo } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { RecentFocusSessionSummary } from '@/data/database';
import { useTheme } from '@/hooks/useTheme';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

interface SessionDebriefModalProps {
  session: RecentFocusSessionSummary | null;
  visible: boolean;
  onDismiss: () => void;
}

export function SessionDebriefModal({
  session,
  visible,
  onDismiss,
}: SessionDebriefModalProps) {
  const { theme } = useTheme();

  const copy = useMemo(() => {
    if (!session) return null;
    const actualMinutes = Math.max(
      0,
      Math.round((new Date(session.endedAt).getTime() - new Date(session.startedAt).getTime()) / 60_000),
    );
    const scheduleDelta = session.plannedMinutes - actualMinutes;
    const timing = scheduleDelta >= 2
      ? ` You finished ${scheduleDelta} minutes ahead of schedule.`
      : scheduleDelta <= -2
        ? ` You ran ${Math.abs(scheduleDelta)} minutes over the plan.`
        : '';

    return session.overrideCount === 0
      ? {
          title: 'Clean session',
          body: `Zero blocked-app attempts.${timing}`,
          icon: 'shield-checkmark-outline' as const,
          color: COLORS.green,
        }
      : {
          title: 'Tough session',
          body: `${session.overrideCount} blocked-app attempt${session.overrideCount === 1 ? '' : 's'} during this session.${timing}`,
          icon: 'alert-circle-outline' as const,
          color: COLORS.orange,
        };
  }, [session]);

  if (!session || !copy) return null;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onDismiss}
      statusBarTranslucent
    >
      <View style={styles.backdrop}>
        <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <View style={styles.header}>
            <View style={[styles.icon, { backgroundColor: `${copy.color}18` }]}>
              <Ionicons name={copy.icon} size={24} color={copy.color} />
            </View>
            <Pressable
              testID="session-debrief-dismiss"
              accessibilityRole="button"
              accessibilityLabel="Dismiss session debrief"
              hitSlop={12}
              onPress={onDismiss}
            >
              <Ionicons name="close" size={22} color={theme.muted} />
            </Pressable>
          </View>
          <Text style={[styles.eyebrow, { color: copy.color }]}>SESSION DEBRIEF</Text>
          <Text style={[styles.title, { color: theme.text }]}>{copy.title}</Text>
          <Text style={[styles.task, { color: theme.muted }]}>{session.taskTitle}</Text>
          <Text style={[styles.body, { color: theme.textSecondary }]}>{copy.body}</Text>
          <Pressable
            testID="session-debrief-done"
            accessibilityRole="button"
            style={[styles.button, { backgroundColor: COLORS.primary }]}
            onPress={onDismiss}
          >
            <Text style={styles.buttonText}>Done</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: SPACING.xl,
    backgroundColor: 'rgba(0,0,0,0.48)',
  },
  card: {
    width: '100%',
    maxWidth: 380,
    padding: SPACING.xl,
    borderRadius: RADIUS.xl,
    borderWidth: 1,
    gap: SPACING.sm,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  icon: {
    width: 48,
    height: 48,
    borderRadius: RADIUS.full,
    alignItems: 'center',
    justifyContent: 'center',
  },
  eyebrow: {
    marginTop: SPACING.sm,
    fontSize: FONT.xs,
    fontWeight: '900',
    letterSpacing: 1,
  },
  title: {
    fontSize: FONT.xl,
    fontWeight: '900',
  },
  task: {
    fontSize: FONT.sm,
    fontWeight: '700',
  },
  body: {
    fontSize: FONT.md,
    lineHeight: 22,
  },
  button: {
    alignItems: 'center',
    paddingVertical: SPACING.md,
    marginTop: SPACING.md,
    borderRadius: RADIUS.md,
  },
  buttonText: {
    color: COLORS.card,
    fontSize: FONT.sm,
    fontWeight: '900',
  },
});