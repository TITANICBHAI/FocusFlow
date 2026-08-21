import React, { useEffect, useRef, useState } from 'react';
import { withScreenErrorBoundary } from '@/components/withScreenErrorBoundary';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Linking,
  Platform,
  AppState,
  ScrollView,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useApp } from '@/context/AppContext';
import { useTaskTimer } from '@/hooks/useTimer';
import { usePomodoro } from '@/hooks/usePomodoro';
import { formatTime, isAwaitingDecision } from '@/services/taskService';
import { dbLogFocusOverride } from '@/data/database';
import { UsageStatsModule } from '@/native-modules/UsageStatsModule';
import { StandaloneBlockModal } from '@/components/StandaloneBlockModal';
import ExtendModal from '@/components/ExtendModal';
import { PinRotationModal } from '@/components/PinRotationModal';
import { PinVerifyModal } from '@/components/PinVerifyModal';
import { SessionPinModule } from '@/native-modules/SessionPinModule';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { useTheme } from '@/hooks/useTheme';
import type { TimerState } from '@/hooks/useTimer';

function FocusScreen() {
  const insets = useSafeAreaInsets();
  const { theme } = useTheme();
  const {
    state,
    currentTask,
    activeTasks,
    startFocusMode,
    stopFocusMode,
    completeTask,
    skipTask,
    extendTaskTime,
    setStandaloneBlockAndAllowance,
    updateSettings,
    setRecurringBlockSchedules,
  } = useApp();
  const { settings } = state;
  const task = currentTask;
  const isFocusing = state.focusSession !== null && state.focusSession.isActive;
  const [hasAccessibilityPermission, setHasAccessibilityPermission] = useState<boolean | null>(null);
  const [blockModalVisible, setBlockModalVisible] = useState(false);
  const [showExtendModal, setShowExtendModal] = useState(false);
  const [pinRotationVisible, setPinRotationVisible] = useState(false);
  const [pendingStartTaskId, setPendingStartTaskId] = useState<string | null>(null);
  const [focusStopPinVisible, setFocusStopPinVisible] = useState(false);
  const pendingStartTaskTimer = task?.startTime ?? '';
  const pendingEndTaskTimer = task?.endTime ?? '';
  const taskTimer = useTaskTimer(pendingStartTaskTimer, pendingEndTaskTimer);

  const standaloneActive = Boolean(
    settings.standaloneBlockUntil &&
      (settings.standaloneBlockPackages ?? []).length > 0 &&
      new Date(settings.standaloneBlockUntil).getTime() > Date.now(),
  );
  const awaitingDecision = task ? isAwaitingDecision(task) : false;
  const otherActiveCount = Math.max(0, activeTasks.filter((item) => item.id !== task?.id).length);
  const blockPresets = settings.blockPresets ?? [];
  const pomodoro = usePomodoro(
    isFocusing && (settings.pomodoroEnabled ?? false),
    state.focusSession?.startedAt ?? null,
    settings.pomodoroDuration ?? 25,
    settings.pomodoroBreak ?? 5,
  );

  const handleActivateFocus = async (taskId: string) => {
    const pinSet = await SessionPinModule.isPinSet().catch(() => false);
    if (pinSet) {
      setPendingStartTaskId(taskId);
      setPinRotationVisible(true);
    } else {
      startFocusMode(taskId);
    }
  };

  const handleSaveBlockPreset = async (preset: import('@/data/types').BlockPreset) => {
    await updateSettings({ ...settings, blockPresets: [...blockPresets, preset] });
  };

  const handleDeleteBlockPreset = async (id: string) => {
    await updateSettings({ ...settings, blockPresets: blockPresets.filter((preset) => preset.id !== id) });
  };

  const handleAddTime = async (minutes: number) => {
    const baseMs = settings.standaloneBlockUntil
      ? Math.max(new Date(settings.standaloneBlockUntil).getTime(), Date.now())
      : Date.now();
    await setStandaloneBlockAndAllowance(
      settings.standaloneBlockPackages ?? [],
      baseMs + minutes * 60 * 1000,
      settings.dailyAllowanceEntries ?? [],
    );
  };

  const handleQuickBlock = async (preset: import('@/data/types').BlockPreset, hours: number) => {
    if (Platform.OS === 'android') {
      const hasA11y = await UsageStatsModule.hasAccessibilityPermission().catch(() => false);
      const hasUsage = await UsageStatsModule.hasPermission().catch(() => false);
      if (!hasA11y || !hasUsage) {
        Alert.alert(
          'Permissions Required',
          'FocusFlow needs Accessibility and Usage Access to block apps.\n\nGo to Settings → Permissions to grant them.',
          [
            { text: 'Not Now', style: 'cancel' },
            {
              text: 'Open Permissions',
              onPress: async () => {
                if (!hasA11y) await UsageStatsModule.openAccessibilitySettings().catch(() => {});
                else await UsageStatsModule.openUsageAccessSettings().catch(() => {});
              },
            },
          ],
        );
        return;
      }
    }
    const existing = settings.standaloneBlockPackages ?? [];
    const mergedPackages = Array.from(new Set([...existing, ...preset.packages]));
    const baseMs = settings.standaloneBlockUntil
      ? Math.max(new Date(settings.standaloneBlockUntil).getTime(), Date.now())
      : Date.now();
    await setStandaloneBlockAndAllowance(
      mergedPackages,
      baseMs + hours * 60 * 60 * 1000,
      settings.dailyAllowanceEntries ?? [],
    );
  };

  useEffect(() => {
    const checkPermission = async () => {
      try {
        setHasAccessibilityPermission(await UsageStatsModule.hasAccessibilityPermission());
      } catch {
        setHasAccessibilityPermission(false);
      }
    };
    void checkPermission();
    let retryOne: ReturnType<typeof setTimeout> | null = null;
    let retryTwo: ReturnType<typeof setTimeout> | null = null;
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active') return;
      void checkPermission();
      if (retryOne) clearTimeout(retryOne);
      if (retryTwo) clearTimeout(retryTwo);
      retryOne = setTimeout(() => void checkPermission(), 2000);
      retryTwo = setTimeout(() => void checkPermission(), 4000);
    });
    return () => {
      subscription.remove();
      if (retryOne) clearTimeout(retryOne);
      if (retryTwo) clearTimeout(retryTwo);
    };
  }, []);

  const blockModal = (
    <StandaloneBlockModal
      visible={blockModalVisible}
      blockedPackages={settings.standaloneBlockPackages ?? []}
      blockUntil={settings.standaloneBlockUntil}
      locked={standaloneActive}
      dailyAllowanceEntries={settings.dailyAllowanceEntries ?? []}
      vpnPackages={settings.standaloneVpnPackages ?? []}
      blockPresets={blockPresets}
      recurringBlockSchedules={settings.recurringBlockSchedules ?? []}
      onSave={async (packages, untilMs, allowanceEntries, vpnPackages) => {
        await setStandaloneBlockAndAllowance(packages, untilMs, allowanceEntries, vpnPackages);
      }}
      onSavePreset={handleSaveBlockPreset}
      onDeletePreset={handleDeleteBlockPreset}
      onSaveRecurringSchedules={async (schedules) => { await setRecurringBlockSchedules(schedules); }}
      onClose={() => setBlockModalVisible(false)}
    />
  );

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: task ? task.color + '18' : theme.background }]}>
      {!task && standaloneActive ? (
        <ScrollView
          contentContainerStyle={[styles.panelContent, { paddingBottom: 60 + insets.bottom + 20 }]}
          showsVerticalScrollIndicator={false}
        >
          <View style={[styles.standaloneIconWrap, { backgroundColor: COLORS.red + '15' }]}>
            <Ionicons name="ban" size={42} color={COLORS.red} />
          </View>
          <Text style={[styles.panelTitle, { color: theme.text }]}>Apps Blocked</Text>
          <Text style={[styles.panelSubtitle, { color: theme.muted }]}>
            Standalone block is running. You can add more apps but cannot stop the block early.
          </Text>
          {settings.standaloneBlockUntil && (
            <StandaloneCountdown
              untilIso={settings.standaloneBlockUntil}
              blockedCount={(settings.standaloneBlockPackages ?? []).length}
            />
          )}
          <TouchableOpacity
            style={[styles.outlineAction, { backgroundColor: theme.card, borderColor: COLORS.red + '44' }]}
            onPress={() => setBlockModalVisible(true)}
            activeOpacity={0.8}
          >
            <Ionicons name="add-circle-outline" size={18} color={COLORS.red} />
            <Text style={[styles.outlineActionText, { color: COLORS.red }]}>Add More Apps to Block</Text>
          </TouchableOpacity>
          <Text style={[styles.sectionLabel, { color: theme.muted }]}>ADD TIME</Text>
          <View style={[styles.addTimeRow, { backgroundColor: theme.card, borderColor: theme.border }]}>
            {[30, 60, 120, 240].map((minutes) => (
              <TouchableOpacity
                key={minutes}
                style={[styles.addTimeBtn, { backgroundColor: COLORS.primary + '14', borderColor: COLORS.primary + '44' }]}
                onPress={() => { void handleAddTime(minutes); }}
              >
                <Text style={[styles.addTimeBtnText, { color: COLORS.primary }]}>
                  +{minutes >= 60 ? `${minutes / 60}h` : `${minutes}m`}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </ScrollView>
      ) : !task ? (
        <ScrollView
          contentContainerStyle={[styles.panelContent, { paddingBottom: 60 + insets.bottom + 20 }]}
          showsVerticalScrollIndicator={false}
        >
          <Ionicons name="timer-outline" size={54} color={COLORS.primary} />
          <Text style={[styles.panelTitle, { color: theme.text }]}>Ready to focus?</Text>
          <Text style={[styles.panelSubtitle, { color: theme.muted }]}>
            Choose a task from Schedule to start a focused session.
          </Text>
          <TouchableOpacity
            style={[styles.primaryBtn, { backgroundColor: COLORS.primary }]}
            onPress={() => router.push('/')}
            activeOpacity={0.85}
          >
            <Ionicons name="calendar-outline" size={20} color="#fff" />
            <Text style={styles.primaryBtnText}>Open Schedule</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.blockScheduleBtn, { backgroundColor: theme.card, borderColor: theme.border }]}
            onPress={() => setBlockModalVisible(true)}
            activeOpacity={0.8}
          >
            <Ionicons name="ban-outline" size={18} color={COLORS.primary} />
            <View style={{ flex: 1 }}>
              <Text style={[styles.blockScheduleBtnText, { color: theme.text }]}>Block Apps Without a Task</Text>
              <Text style={[styles.blockScheduleBtnDesc, { color: theme.textSecondary }]}>
                Start a standalone block or recurring schedule
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={16} color={theme.muted} />
          </TouchableOpacity>
        </ScrollView>
      ) : (
        <>
          {hasAccessibilityPermission === false && !isFocusing && (
            <TouchableOpacity
              style={styles.permissionBanner}
              onPress={async () => {
                try {
                  if (Platform.OS === 'android') await UsageStatsModule.openAccessibilitySettings();
                  else await Linking.openSettings();
                } catch {
                  await Linking.openSettings().catch(() => {});
                }
              }}
              activeOpacity={0.8}
            >
              <Ionicons name="warning-outline" size={18} color={COLORS.orange} />
              <View style={styles.permissionBannerText}>
                <Text style={styles.permissionBannerTitle}>Accessibility permission needed</Text>
                <Text style={styles.permissionBannerDesc}>
                  Focus Mode can't block apps without Accessibility access. Tap to open Settings.
                </Text>
              </View>
              <Ionicons name="chevron-forward" size={16} color={COLORS.orange} />
            </TouchableOpacity>
          )}
          <ScrollView
            style={{ flex: 1 }}
            contentContainerStyle={[styles.scrollContent, { paddingBottom: 60 + insets.bottom + 20 }]}
            showsVerticalScrollIndicator={false}
            keyboardShouldPersistTaps="handled"
          >
            <View style={styles.statusRow}>
              <View style={[styles.statusDot, { backgroundColor: isFocusing ? COLORS.green : COLORS.muted }]} />
              <Text style={[styles.statusText, { color: theme.textSecondary }]}>
                {isFocusing
                  ? (settings.pomodoroEnabled ?? false)
                    ? `Focus Mode Active · ${pomodoro.isBreakActive ? '☕ Break · apps unlocked' : pomodoro.phase === 'work' ? '🎯 Work' : '☕ Break available'}`
                    : 'Focus Mode Active'
                  : 'Task In Progress'}
              </Text>
            </View>
            <View style={[styles.taskPanel, { backgroundColor: theme.card, borderColor: task.color + '66' }]}>
              <View style={[styles.timerPanel, { backgroundColor: task.color + '18' }]}>
                <TimerDisplay timer={taskTimer} color={task.color} />
              </View>
              <Text style={[styles.taskTitle, { color: theme.text }]} numberOfLines={2}>{task.title}</Text>
              <Text style={[styles.taskTime, { color: theme.textSecondary }]}>
                {formatTime(task.startTime)} – {formatTime(task.endTime)}
              </Text>
              <View style={[styles.progressTrack, { backgroundColor: theme.border }]}>
                <View style={[styles.progressFill, { backgroundColor: task.color, width: `${Math.round(taskTimer.progress * 100)}%` as `${number}%` }]} />
              </View>
              <Text style={[styles.progressLabel, { color: theme.muted }]}>
                {taskTimer.isOverdue ? 'Overdue' : `${Math.round(taskTimer.progress * 100)}% complete`}
              </Text>
              {isFocusing && (settings.pomodoroEnabled ?? false) && (
                <PomodoroStrip
                  pomodoro={pomodoro}
                  workMinutes={settings.pomodoroDuration ?? 25}
                  breakMinutes={settings.pomodoroBreak ?? 5}
                  onTakeBreak={() => { void pomodoro.takeBreak(); }}
                />
              )}
              {otherActiveCount > 0 && (
                <TouchableOpacity
                  style={[styles.moreActiveChip, { backgroundColor: theme.card, borderColor: theme.border }]}
                  onPress={() => router.push('/')}
                >
                  <Ionicons name="layers-outline" size={12} color={COLORS.primary} />
                  <Text style={[styles.moreActiveChipText, { color: COLORS.primary }]}>+{otherActiveCount} more active</Text>
                </TouchableOpacity>
              )}
              {awaitingDecision && (
                <View style={[styles.endedPrompt, { backgroundColor: COLORS.orange + '15', borderColor: COLORS.orange + '55' }]}>
                  <View style={styles.endedPromptHeader}>
                    <Ionicons name="alarm" size={18} color={COLORS.orange} />
                    <Text style={[styles.endedPromptTitle, { color: COLORS.orange }]}>Time's up — what next?</Text>
                  </View>
                  <Text style={[styles.endedPromptDesc, { color: theme.textSecondary }]}>This task ran past its scheduled end. Pick one to clear it.</Text>
                  <View style={styles.endedPromptRow}>
                    <SecondaryBtn icon="checkmark" label="Done" color={COLORS.green} onPress={() => completeTask(task.id)} />
                    <SecondaryBtn icon="add" label="Extend" color={COLORS.orange} onPress={() => setShowExtendModal(true)} />
                    <SecondaryBtn icon="close" label="Skip" color={COLORS.muted} onPress={() => {
                      Alert.alert('Skip Task', 'Skip this task?', [
                        { text: 'Cancel', style: 'cancel' },
                        { text: 'Skip', style: 'destructive', onPress: () => skipTask(task.id) },
                      ]);
                    }} />
                  </View>
                </View>
              )}
              {task.tags.length > 0 && (
                <View style={styles.tagsRow}>
                  {task.tags.map((tag) => (
                    <View key={tag} style={[styles.tag, { backgroundColor: task.color + '22' }]}>
                      <Text style={[styles.tagText, { color: task.color }]}>#{tag}</Text>
                    </View>
                  ))}
                </View>
              )}
            </View>
            <View style={styles.actions}>
              {!isFocusing ? (
                <TouchableOpacity
                  style={[styles.primaryBtn, { backgroundColor: task.color }]}
                  onPress={async () => {
                    if (Platform.OS === 'android') {
                      const hasA11y = await UsageStatsModule.hasAccessibilityPermission().catch(() => false);
                      const hasUsage = await UsageStatsModule.hasPermission().catch(() => false);
                      if (!hasA11y || !hasUsage) {
                        Alert.alert('Permissions Required', 'Focus Mode needs Accessibility and Usage Access to block apps.\n\nGo to Settings → Permissions to grant them.', [
                          { text: 'Not Now', style: 'cancel' },
                          { text: 'Open Permissions', onPress: async () => {
                            if (!hasA11y) await UsageStatsModule.openAccessibilitySettings().catch(() => {});
                            else await UsageStatsModule.openUsageAccessSettings().catch(() => {});
                          } },
                        ]);
                        return;
                      }
                    }
                    void handleActivateFocus(task.id);
                  }}
                >
                  <Ionicons name="shield-checkmark" size={20} color="#fff" />
                  <Text style={styles.primaryBtnText}>Activate Focus</Text>
                </TouchableOpacity>
              ) : (
                <TouchableOpacity
                  style={[styles.primaryBtn, { backgroundColor: COLORS.muted }]}
                  onPress={async () => {
                    if (await SessionPinModule.isPinSet().catch(() => false)) setFocusStopPinVisible(true);
                    else Alert.alert('Stop Focus', 'End focus mode for this task?', [
                      { text: 'Cancel', style: 'cancel' },
                      { text: 'Stop', style: 'destructive', onPress: () => stopFocusMode() },
                    ]);
                  }}
                >
                  <Ionicons name="stop-circle-outline" size={20} color="#fff" />
                  <Text style={styles.primaryBtnText}>Stop Focus</Text>
                </TouchableOpacity>
              )}
              <View style={styles.secondaryActions}>
                <SecondaryBtn icon="checkmark-circle-outline" label="Done" color={COLORS.green} onPress={() => Alert.alert('Complete Task', 'Mark this task as done?', [
                  { text: 'Cancel', style: 'cancel' },
                  { text: 'Done', onPress: () => completeTask(task.id) },
                ])} />
                <SecondaryBtn icon="alarm-outline" label="Extend" color={COLORS.orange} onPress={() => setShowExtendModal(true)} />
              </View>
              {isFocusing && (
                <TouchableOpacity style={styles.emergencyBtn} onPress={() => Alert.alert('🚨 Emergency Override', 'This will stop focus mode and be logged. Only use in a genuine emergency.', [
                  { text: 'Cancel', style: 'cancel' },
                  { text: 'Override', style: 'destructive', onPress: async () => {
                    await dbLogFocusOverride(task.id, 'manual-override', 'User triggered emergency override');
                    await stopFocusMode();
                  } },
                ])}>
                  <Ionicons name="warning-outline" size={16} color={COLORS.red} />
                  <Text style={styles.emergencyBtnText}>Emergency Override</Text>
                </TouchableOpacity>
              )}
            </View>
            {isFocusing && (
              <View style={styles.allowedRow}>
                <Text style={[styles.allowedLabel, { color: theme.textSecondary }]}>Allowed: </Text>
                <Text style={[styles.allowedApps, { color: theme.muted }]}>{state.settings.allowedInFocus.join(', ')}</Text>
              </View>
            )}
          </ScrollView>
          {standaloneActive && settings.standaloneBlockUntil && (
            <TouchableOpacity style={styles.standaloneChip} onPress={() => setBlockModalVisible(true)}>
              <Ionicons name="ban" size={13} color={COLORS.red} />
              <Text style={styles.standaloneChipText}>+{(settings.standaloneBlockPackages ?? []).length} extra apps blocked</Text>
              <Ionicons name="add-outline" size={13} color={COLORS.red} />
            </TouchableOpacity>
          )}
        </>
      )}
      {blockModal}
      {task && (
        <ExtendModal
          visible={showExtendModal}
          taskId={task.id}
          onClose={() => setShowExtendModal(false)}
          onExtend={async (id, minutes) => { await extendTaskTime(id, minutes); setShowExtendModal(false); }}
        />
      )}
      <PinRotationModal
        visible={pinRotationVisible}
        pinType="focus"
        reuseTrackerKey="focus"
        actionLabel="Start Focus Session"
        actionDescription="Set the password required to end this focus session. You can keep your existing password or create a new one."
        onComplete={() => {
          setPinRotationVisible(false);
          if (pendingStartTaskId) {
            startFocusMode(pendingStartTaskId);
            setPendingStartTaskId(null);
          }
        }}
        onCancel={() => { setPinRotationVisible(false); setPendingStartTaskId(null); }}
      />
      <PinVerifyModal
        visible={focusStopPinVisible}
        pinType="focus"
        title="Stop Focus Session"
        description="Enter your focus session password to end the session and stop all blocking."
        onVerified={() => { setFocusStopPinVisible(false); stopFocusMode(); }}
        onCancel={() => setFocusStopPinVisible(false)}
      />
    </SafeAreaView>
  );
}

function StandaloneCountdown({ untilIso, blockedCount }: { untilIso: string; blockedCount: number }) {
  const [remaining, setRemaining] = useState(0);
  useEffect(() => {
    const tick = () => setRemaining(Math.max(0, new Date(untilIso).getTime() - Date.now()));
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [untilIso]);
  const hours = Math.floor(remaining / 3600000);
  const minutes = Math.floor((remaining % 3600000) / 60000);
  const seconds = Math.floor((remaining % 60000) / 1000);
  const time = hours > 0 ? `${hours}h ${minutes}m` : `${minutes}:${seconds.toString().padStart(2, '0')}`;
  return (
    <View style={[countdownStyles.container, { backgroundColor: COLORS.red + '12', borderColor: COLORS.red + '30' }]}>
      <Text style={[countdownStyles.label, { color: COLORS.red }]}>BLOCK EXPIRES IN</Text>
      <Text style={[countdownStyles.time, { color: COLORS.red }]}>{time}</Text>
      <Text style={[countdownStyles.apps, { color: COLORS.red }]}>{blockedCount} app{blockedCount !== 1 ? 's' : ''} blocked · cannot stop early</Text>
    </View>
  );
}

function TimerDisplay({ timer, color }: { timer: TimerState; color: string }) {
  const minutes = Math.floor(timer.remaining / 60);
  const seconds = timer.remaining % 60;
  return (
    <View style={timerStyles.container}>
      <Text style={[timerStyles.time, { color }]}>{timer.isOverdue ? `+${Math.floor(-timer.remaining / 60)}m` : `${minutes}:${seconds.toString().padStart(2, '0')}`}</Text>
      <Text style={[timerStyles.label, { color: color + 'bb' }]}>{timer.isOverdue ? 'overdue' : 'remaining'}</Text>
    </View>
  );
}

function PomodoroStrip({
  pomodoro,
  workMinutes,
  breakMinutes,
  onTakeBreak,
}: {
  pomodoro: import('@/hooks/usePomodoro').PomodoroState;
  workMinutes: number;
  breakMinutes: number;
  onTakeBreak: () => void;
}) {
  const isWork = pomodoro.phase === 'work';
  const accentColor = isWork ? COLORS.primary : COLORS.green;
  const time = `${Math.floor(pomodoro.secondsLeft / 60)}:${(pomodoro.secondsLeft % 60).toString().padStart(2, '0')}`;
  const progress = Math.min(100, Math.round(pomodoro.phaseProgress * 100));
  return (
    <View style={[pomStyles.card, { backgroundColor: accentColor + '12', borderColor: accentColor + '40' }]}>
      <View style={pomStyles.topRow}>
        <View style={[pomStyles.phaseBadge, { backgroundColor: accentColor + '22' }]}><Text style={[pomStyles.phaseLabel, { color: accentColor }]}>{isWork ? '🎯 WORK' : '☕ BREAK'}</Text></View>
        <Text style={[pomStyles.countdown, { color: accentColor }]}>{time}</Text>
        <View style={[pomStyles.cycleBadge, { borderColor: accentColor + '40' }]}><Text style={[pomStyles.cycleText, { color: accentColor }]}>Cycle {pomodoro.cycleCount + 1}</Text></View>
      </View>
      <View style={[pomStyles.progressTrack, { backgroundColor: accentColor + '20' }]}><View style={[pomStyles.progressFill, { backgroundColor: accentColor, width: `${progress}%` as `${number}%` }]} /></View>
      <Text style={[pomStyles.hint, { color: accentColor + 'bb' }]}>
        {isWork ? `${Math.floor(pomodoro.secondsLeft / 60)}m left → ${breakMinutes}m break` : pomodoro.isBreakActive ? `Apps unlocked · blocking resumes in ${time}` : `${Math.floor(pomodoro.secondsLeft / 60)}m rest available · tap below to unlock apps`}
      </Text>
      {!isWork && !pomodoro.isBreakActive && <TouchableOpacity style={[pomStyles.breakButton, { backgroundColor: COLORS.green }]} onPress={onTakeBreak}><Ionicons name="cafe-outline" size={15} color="#fff" /><Text style={pomStyles.breakButtonText}>Take {breakMinutes}m break</Text></TouchableOpacity>}
    </View>
  );
}

function SecondaryBtn({ icon, label, color, onPress }: { icon: keyof typeof Ionicons.glyphMap; label: string; color: string; onPress: () => void }) {
  return <TouchableOpacity style={[styles.secondaryBtn, { borderColor: color + '44' }]} onPress={onPress}><Ionicons name={icon} size={18} color={color} /><Text style={[styles.secondaryBtnText, { color }]}>{label}</Text></TouchableOpacity>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  panelContent: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: SPACING.xl, gap: SPACING.md },
  panelTitle: { fontSize: FONT.xl, fontWeight: '700', textAlign: 'center' },
  panelSubtitle: { fontSize: FONT.md, textAlign: 'center', lineHeight: 22 },
  standaloneIconWrap: { width: 90, height: 90, borderRadius: 45, alignItems: 'center', justifyContent: 'center' },
  outlineAction: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACING.sm, paddingVertical: SPACING.md, paddingHorizontal: SPACING.xl, borderRadius: RADIUS.lg, borderWidth: 1.5, width: '100%' },
  outlineActionText: { fontSize: FONT.sm, fontWeight: '700' },
  sectionLabel: { alignSelf: 'flex-start', fontSize: FONT.xs, fontWeight: '700', letterSpacing: 0.6, marginTop: SPACING.sm },
  addTimeRow: { flexDirection: 'row', gap: SPACING.sm, padding: SPACING.md, borderRadius: RADIUS.lg, borderWidth: 1, width: '100%' },
  addTimeBtn: { flex: 1, alignItems: 'center', paddingVertical: SPACING.md, borderRadius: RADIUS.md, borderWidth: 1 },
  addTimeBtnText: { fontSize: FONT.md, fontWeight: '700' },
  blockScheduleBtn: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, borderRadius: RADIUS.lg, padding: SPACING.md, borderWidth: 1, width: '100%' },
  blockScheduleBtnText: { fontSize: FONT.sm, fontWeight: '700' },
  blockScheduleBtnDesc: { fontSize: FONT.xs, marginTop: 2 },
  permissionBanner: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, backgroundColor: COLORS.orange + '18', borderBottomWidth: 1, borderBottomColor: COLORS.orange + '44', paddingHorizontal: SPACING.lg, paddingVertical: SPACING.sm },
  permissionBannerText: { flex: 1, gap: 2 },
  permissionBannerTitle: { fontSize: FONT.sm, fontWeight: '700', color: COLORS.orange },
  permissionBannerDesc: { fontSize: FONT.xs, color: COLORS.textSecondary, lineHeight: 16 },
  scrollContent: { flexGrow: 1 },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: SPACING.xs, padding: SPACING.lg, justifyContent: 'center' },
  statusDot: { width: 8, height: 8, borderRadius: 4 },
  statusText: { fontSize: FONT.sm, fontWeight: '600' },
  taskPanel: { marginHorizontal: SPACING.lg, borderRadius: RADIUS.xl, borderWidth: 1.5, padding: SPACING.lg, alignItems: 'center', gap: SPACING.sm },
  timerPanel: { width: '100%', borderRadius: RADIUS.lg, paddingVertical: SPACING.xl, alignItems: 'center' },
  taskTitle: { fontSize: FONT.xl, fontWeight: '700', textAlign: 'center', paddingHorizontal: SPACING.md },
  taskTime: { fontSize: FONT.md },
  progressTrack: { height: 6, borderRadius: RADIUS.full, overflow: 'hidden', width: '100%' },
  progressFill: { height: 6, borderRadius: RADIUS.full },
  progressLabel: { fontSize: FONT.xs, fontWeight: '600' },
  tagsRow: { flexDirection: 'row', gap: SPACING.xs, flexWrap: 'wrap', justifyContent: 'center' },
  tag: { paddingHorizontal: SPACING.sm, paddingVertical: 3, borderRadius: RADIUS.full },
  tagText: { fontSize: FONT.xs, fontWeight: '600' },
  actions: { padding: SPACING.lg, gap: SPACING.sm },
  primaryBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACING.sm, paddingVertical: SPACING.md, borderRadius: RADIUS.lg, width: '100%' },
  primaryBtnText: { color: '#fff', fontSize: FONT.md, fontWeight: '700' },
  secondaryActions: { flexDirection: 'row', gap: SPACING.sm },
  secondaryBtn: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACING.xs, paddingVertical: SPACING.sm, borderRadius: RADIUS.md, borderWidth: 1.5 },
  secondaryBtnText: { fontSize: FONT.sm, fontWeight: '600' },
  allowedRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: SPACING.lg, paddingBottom: SPACING.lg },
  allowedLabel: { fontSize: FONT.xs, fontWeight: '600' },
  allowedApps: { fontSize: FONT.xs },
  emergencyBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACING.xs, paddingVertical: SPACING.sm, borderRadius: RADIUS.md, borderWidth: 1.5, borderColor: COLORS.red + '44', backgroundColor: COLORS.red + '08' },
  emergencyBtnText: { fontSize: FONT.sm, fontWeight: '600', color: COLORS.red },
  standaloneChip: { flexDirection: 'row', alignItems: 'center', gap: 5, alignSelf: 'center', backgroundColor: COLORS.red + '12', borderRadius: RADIUS.full, paddingHorizontal: SPACING.md, paddingVertical: 5, marginBottom: SPACING.sm, borderWidth: 1, borderColor: COLORS.red + '30' },
  standaloneChipText: { fontSize: FONT.xs, fontWeight: '700', color: COLORS.red },
  moreActiveChip: { flexDirection: 'row', alignItems: 'center', gap: 5, paddingHorizontal: SPACING.sm, paddingVertical: 4, borderRadius: RADIUS.full, borderWidth: 1 },
  moreActiveChipText: { fontSize: FONT.xs, fontWeight: '700' },
  endedPrompt: { padding: SPACING.md, borderRadius: RADIUS.lg, borderWidth: 1.5, gap: SPACING.sm, width: '100%' },
  endedPromptHeader: { flexDirection: 'row', alignItems: 'center', gap: SPACING.xs },
  endedPromptTitle: { fontSize: FONT.md, fontWeight: '800' },
  endedPromptDesc: { fontSize: FONT.xs, lineHeight: 16 },
  endedPromptRow: { flexDirection: 'row', gap: SPACING.xs },
});

const countdownStyles = StyleSheet.create({
  container: { alignItems: 'center', paddingVertical: SPACING.lg, paddingHorizontal: SPACING.xl, borderRadius: RADIUS.lg, borderWidth: 1.5, gap: SPACING.xs, width: '100%' },
  label: { fontSize: FONT.xs, fontWeight: '700', letterSpacing: 0.8 },
  time: { fontSize: 44, fontWeight: '800', letterSpacing: -1 },
  apps: { fontSize: FONT.xs, fontWeight: '600', opacity: 0.8 },
});

const timerStyles = StyleSheet.create({
  container: { alignItems: 'center' },
  time: { fontSize: 48, fontWeight: '800' },
  label: { fontSize: FONT.sm, fontWeight: '600' },
});

const pomStyles = StyleSheet.create({
  card: { width: '100%', borderRadius: RADIUS.lg, borderWidth: 1.5, padding: SPACING.md, gap: SPACING.xs, marginTop: SPACING.md },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: SPACING.sm },
  phaseBadge: { borderRadius: RADIUS.full, paddingHorizontal: SPACING.sm, paddingVertical: 4 },
  phaseLabel: { fontSize: FONT.xs, fontWeight: '800', letterSpacing: 0.6 },
  countdown: { fontSize: 28, fontWeight: '800', flex: 1, textAlign: 'center' },
  cycleBadge: { borderWidth: 1, borderRadius: RADIUS.md, paddingHorizontal: SPACING.sm, paddingVertical: 3 },
  cycleText: { fontSize: FONT.xs, fontWeight: '700' },
  progressTrack: { height: 4, borderRadius: 2, overflow: 'hidden', width: '100%' },
  progressFill: { height: 4, borderRadius: 2 },
  hint: { fontSize: 11, fontWeight: '500', textAlign: 'center' },
  breakButton: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACING.xs, borderRadius: RADIUS.md, paddingVertical: SPACING.sm, marginTop: SPACING.xs },
  breakButtonText: { color: '#fff', fontSize: FONT.sm, fontWeight: '700' },
});

export default withScreenErrorBoundary(FocusScreen, 'Focus');