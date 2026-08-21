import React, { useCallback, useRef, useState } from 'react';
import {
  Alert,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';

import { useApp } from '@/context/AppContext';
import { useTheme } from '@/hooks/useTheme';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { DailyAllowanceModal } from '@/components/DailyAllowanceModal';
import { PinSetupModal } from '@/components/PinSetupModal';
import { PinVerifyModal } from '@/components/PinVerifyModal';
import { withScreenErrorBoundary } from '@/components/withScreenErrorBoundary';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import AsyncStorage from '@react-native-async-storage/async-storage';

type DefenseAction = (defensePinHash?: string) => void;
const DEFENSE_HINT_DISMISSED_KEY = '@focusflow/defenseHintDismissed';

function DefenseScreen() {
  const insets = useSafeAreaInsets();
  const { theme } = useTheme();
  const { state, updateSettings, setDailyAllowanceEntries } = useApp();
  const { settings } = state;

  const [dailyAllowanceVisible, setDailyAllowanceVisible] = useState(false);
  const [pinModal, setPinModal] = useState<
    | { type: 'none' }
    | { type: 'verify'; title: string; description: string; action: DefenseAction }
    | { type: 'setup'; action: DefenseAction }
  >({ type: 'none' });
  const [showDefenseHint, setShowDefenseHint] = useState(false);
  const pendingSetupAction = useRef<DefenseAction | null>(null);

  React.useEffect(() => {
    void AsyncStorage.getItem(DEFENSE_HINT_DISMISSED_KEY).then((dismissed) => {
      if (!dismissed) setShowDefenseHint(true);
    });
  }, []);

  const dismissDefenseHint = useCallback(() => {
    setShowDefenseHint(false);
    void AsyncStorage.setItem(DEFENSE_HINT_DISMISSED_KEY, '1');
  }, []);

  const update = useCallback(
    async (partial: Partial<typeof settings>, defensePinHash: string | null = null) => {
      try {
        await updateSettings({ ...settings, ...partial }, { defensePinHash });
      } catch {
        Alert.alert('Save failed', 'Could not save this defense setting. Please try again.');
      }
    },
    [settings, updateSettings],
  );

  const requireDefensePin = useCallback(
    (title: string, description: string, action: DefenseAction) => {
      void SharedPrefsModule.getString('defense_pin_hash')
        .then((hash) => {
          if (hash) {
            setPinModal({ type: 'verify', title, description, action });
            return;
          }
          if (settings.pinProtectionEnabled ?? false) {
            Alert.alert(
              'No Defense Password set',
              'PIN protection is enabled but no Defense Password has been set yet.',
              [
                {
                  text: 'Set Password',
                  onPress: () => {
                    pendingSetupAction.current = action;
                    setPinModal({ type: 'setup', action });
                  },
                },
                { text: 'Proceed anyway', onPress: () => action() },
                { text: 'Cancel', style: 'cancel' },
              ],
            );
            return;
          }
          action();
        })
        .catch(() => action());
    },
    [settings.pinProtectionEnabled],
  );

  const toggleProtectedSetting = (
    key:
      | 'systemGuardEnabled'
      | 'blockYoutubeShortsEnabled'
      | 'blockInstagramReelsEnabled'
      | 'aversionDimmerEnabled'
      | 'aversionVibrateEnabled'
      | 'aversionSoundEnabled',
    enabled: boolean,
    label: string,
  ) => {
    if (enabled) {
      void update({ [key]: true });
      return;
    }
    if (state.focusSession?.isActive || isStandaloneActive(settings)) {
      Alert.alert('Protection is active', `${label} cannot be turned off while a block is active.`);
      return;
    }
    requireDefensePin(`Disable ${label}`, `Enter your defense password to turn off ${label}.`, (hash) => {
      void update({ [key]: false }, hash ?? null);
    });
  };

  if (!state.isDbReady) {
    return (
      <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
        <Header theme={theme} />
        <View style={styles.loading}>
          <Text style={[styles.loadingText, { color: theme.muted }]}>Loading…</Text>
        </View>
      </SafeAreaView>
    );
  }

  const alwaysOnCount = (settings.alwaysOnPackages ?? []).length;
  const allowanceCount = (settings.dailyAllowanceEntries ?? []).length;
  const alwaysOnEnabled = settings.alwaysOnEnforcementEnabled ?? false;
  const activeBlock = state.focusSession?.isActive === true || isStandaloneActive(settings);

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <Header theme={theme} />
      {showDefenseHint && (
        <View style={[styles.hintBanner, { backgroundColor: COLORS.primary + '12', borderColor: COLORS.primary + '35' }]}>
          <Ionicons name="information-circle-outline" size={20} color={COLORS.primary} />
          <Text style={[styles.hintText, { color: theme.text }]}>
            Defense Password and PIN Protection are lower down this page — scroll to find them.
          </Text>
          <TouchableOpacity
            onPress={dismissDefenseHint}
            accessibilityRole="button"
            accessibilityLabel="Dismiss Defense hint"
            hitSlop={8}
          >
            <Ionicons name="close" size={19} color={theme.muted} />
          </TouchableOpacity>
        </View>
      )}
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={[styles.content, { paddingBottom: 70 + insets.bottom }]}
      >
        <Section title="Always-On Blocking" theme={theme}>
          <SettingRow
            label="Always-On Enforcement"
            description={
              alwaysOnEnabled
                ? `${alwaysOnCount} app${alwaysOnCount === 1 ? '' : 's'} blocked around the clock`
                : 'Keep selected apps blocked 24/7'
            }
            theme={theme}
          >
            <Switch
              value={alwaysOnEnabled}
              onValueChange={(enabled) => {
                if (enabled) {
                  void update({ alwaysOnEnforcementEnabled: true });
                } else {
                  requireDefensePin(
                    'Disable Always-On Enforcement',
                    'Enter your defense password to turn off always-on protection.',
                    (hash) => void update({ alwaysOnEnforcementEnabled: false }, hash ?? null),
                  );
                }
              }}
              trackColor={{ false: theme.border, true: COLORS.primary + '88' }}
              thumbColor={alwaysOnEnabled ? COLORS.primary : theme.muted}
            />
          </SettingRow>
          <SettingButton
            icon="apps-outline"
            label="Manage Always-On App List"
            description={
              alwaysOnCount === 0
                ? 'Choose apps that should stay blocked'
                : `${alwaysOnCount} app${alwaysOnCount === 1 ? '' : 's'} selected`
            }
            onPress={() => router.push('/always-on')}
            theme={theme}
          />
          <SettingButton
            icon="sunny-outline"
            label="Daily Allowance"
            description={
              allowanceCount === 0
                ? 'Set daily count, time, or interval limits per app'
                : `${allowanceCount} app${allowanceCount === 1 ? '' : 's'} configured`
            }
            onPress={() => setDailyAllowanceVisible(true)}
            theme={theme}
          />
        </Section>

        <Section title="Defense Tools" theme={theme}>
          <SettingButton
            icon="text-outline"
            label="Keyword Blocker"
            description="Block keywords in URLs, searches, and on-screen text"
            onPress={() => router.push('/keyword-blocker')}
            theme={theme}
          />
          <SettingButton
            icon="time-outline"
            label="Block Schedules"
            description="Manage recurring time-window blocks"
            onPress={() => router.push('/block-defense?tab=greyout')}
            theme={theme}
          />
          <SettingButton
            icon="shield-half-outline"
            label="PIN Protection"
            description={
              settings.pinProtectionEnabled
                ? 'Defense password required before disabling protection'
                : 'Require a password before protections can be disabled'
            }
            onPress={() => router.push('/block-defense')}
            theme={theme}
          />
        </Section>

        <Section title="System Guard" theme={theme}>
          <ProtectedToggle
            label="Protect system controls"
            description="Block power menu, notification shade, and sensitive Settings pages"
            value={settings.systemGuardEnabled ?? false}
            onValueChange={(value) => toggleProtectedSetting('systemGuardEnabled', value, 'System Guard')}
            disabled={activeBlock && (settings.systemGuardEnabled ?? false)}
            theme={theme}
          />
          <ProtectedToggle
            label="Block YouTube Shorts"
            description="Redirect away from the Shorts player"
            value={settings.blockYoutubeShortsEnabled ?? false}
            onValueChange={(value) => toggleProtectedSetting('blockYoutubeShortsEnabled', value, 'YouTube Shorts protection')}
            disabled={activeBlock && (settings.blockYoutubeShortsEnabled ?? false)}
            theme={theme}
          />
          <ProtectedToggle
            label="Block Instagram Reels"
            description="Redirect away from the Reels viewer"
            value={settings.blockInstagramReelsEnabled ?? false}
            onValueChange={(value) => toggleProtectedSetting('blockInstagramReelsEnabled', value, 'Instagram Reels protection')}
            disabled={activeBlock && (settings.blockInstagramReelsEnabled ?? false)}
            theme={theme}
          />
        </Section>

        <Section title="Aversion Deterrents" theme={theme}>
          <ProtectedToggle
            label="Screen Dimmer"
            description="Show a near-black overlay when a blocked app is open"
            value={settings.aversionDimmerEnabled}
            onValueChange={(value) => toggleProtectedSetting('aversionDimmerEnabled', value, 'Screen Dimmer')}
            theme={theme}
          />
          <ProtectedToggle
            label="Vibration Harassment"
            description="Pulse vibration while a blocked app is in the foreground"
            value={settings.aversionVibrateEnabled}
            onValueChange={(value) => toggleProtectedSetting('aversionVibrateEnabled', value, 'Vibration Harassment')}
            theme={theme}
          />
          <ProtectedToggle
            label="Sound Alert"
            description="Play an alert when a blocked app launches"
            value={settings.aversionSoundEnabled}
            onValueChange={(value) => toggleProtectedSetting('aversionSoundEnabled', value, 'Sound Alert')}
            theme={theme}
          />
        </Section>
      </ScrollView>

      <DailyAllowanceModal
        visible={dailyAllowanceVisible}
        selectedEntries={settings.dailyAllowanceEntries ?? []}
        locked={isStandaloneActive(settings)}
        requireDefensePin
        onSave={async (entries) => {
          await setDailyAllowanceEntries(entries);
          setDailyAllowanceVisible(false);
        }}
        onClose={() => setDailyAllowanceVisible(false)}
      />

      <PinVerifyModal
        visible={pinModal.type === 'verify'}
        pinType="defense"
        title={pinModal.type === 'verify' ? pinModal.title : undefined}
        description={pinModal.type === 'verify' ? pinModal.description : undefined}
        onVerified={(hash) => {
          if (pinModal.type !== 'verify') return;
          const action = pinModal.action;
          setPinModal({ type: 'none' });
          action(hash);
        }}
        onCancel={() => setPinModal({ type: 'none' })}
      />
      <PinSetupModal
        visible={pinModal.type === 'setup'}
        pinType="defense"
        onSaved={() => {
          const action = pendingSetupAction.current;
          pendingSetupAction.current = null;
          setPinModal({ type: 'none' });
          action?.();
        }}
        onCancel={() => {
          pendingSetupAction.current = null;
          setPinModal({ type: 'none' });
        }}
      />
    </SafeAreaView>
  );
}

function isStandaloneActive(settings: { standaloneBlockUntil: string | null; standaloneBlockPackages?: string[] }) {
  return Boolean(
    settings.standaloneBlockUntil &&
      (settings.standaloneBlockPackages ?? []).length > 0 &&
      new Date(settings.standaloneBlockUntil).getTime() > Date.now(),
  );
}

function Header({ theme }: { theme: ReturnType<typeof useTheme>['theme'] }) {
  return (
    <View style={[styles.header, { backgroundColor: theme.card, borderBottomColor: theme.border }]}>
      <View style={[styles.headerIcon, { backgroundColor: COLORS.primary + '18' }]}>
        <Ionicons name="shield-checkmark" size={22} color={COLORS.primary} />
      </View>
      <View style={styles.headerText}>
        <Text style={[styles.title, { color: theme.text }]}>Defense</Text>
        <Text style={[styles.subtitle, { color: theme.muted }]}>Make distractions harder to reach</Text>
      </View>
    </View>
  );
}

function Section({
  title,
  theme,
  children,
}: {
  title: string;
  theme: ReturnType<typeof useTheme>['theme'];
  children: React.ReactNode;
}) {
  return (
    <View style={styles.section}>
      <Text style={[styles.sectionTitle, { color: theme.muted }]}>{title}</Text>
      <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>{children}</View>
    </View>
  );
}

function SettingRow({
  label,
  description,
  theme,
  children,
}: {
  label: string;
  description: string;
  theme: ReturnType<typeof useTheme>['theme'];
  children: React.ReactNode;
}) {
  return (
    <View style={[styles.row, { borderBottomColor: theme.border }]}>
      <View style={styles.rowText}>
        <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
        <Text style={[styles.description, { color: theme.muted }]}>{description}</Text>
      </View>
      {children}
    </View>
  );
}

function ProtectedToggle({
  label,
  description,
  value,
  onValueChange,
  disabled = false,
  theme,
}: {
  label: string;
  description: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
  disabled?: boolean;
  theme: ReturnType<typeof useTheme>['theme'];
}) {
  return (
    <SettingRow label={label} description={disabled ? 'Locked while active protection is running' : description} theme={theme}>
      <Switch
        value={value}
        onValueChange={onValueChange}
        disabled={disabled}
        trackColor={{ false: theme.border, true: COLORS.primary + '88' }}
        thumbColor={value ? COLORS.primary : theme.muted}
      />
    </SettingRow>
  );
}

function SettingButton({
  icon,
  label,
  description,
  onPress,
  theme,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  description: string;
  onPress: () => void;
  theme: ReturnType<typeof useTheme>['theme'];
}) {
  return (
    <TouchableOpacity
      style={[styles.button, { borderBottomColor: theme.border }]}
      onPress={onPress}
      activeOpacity={0.75}
    >
      <Ionicons name={icon} size={20} color={COLORS.primary} />
      <View style={styles.rowText}>
        <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
        <Text style={[styles.description, { color: theme.muted }]}>{description}</Text>
      </View>
      <Ionicons name="chevron-forward" size={17} color={theme.muted} />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  hintBanner: {
    marginHorizontal: SPACING.md,
    marginTop: SPACING.sm,
    padding: SPACING.sm,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
  },
  hintText: { flex: 1, fontSize: FONT.xs, lineHeight: 17 },
  header: {
    minHeight: 76,
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    alignItems: 'center',
  },
  headerIcon: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: SPACING.md,
  },
  headerText: { flex: 1 },
  title: { fontSize: FONT.xl, fontWeight: '700' },
  subtitle: { fontSize: FONT.sm, marginTop: 2 },
  scroll: { flex: 1 },
  content: { paddingHorizontal: SPACING.md, paddingTop: SPACING.md },
  section: { marginBottom: SPACING.lg },
  sectionTitle: {
    fontSize: FONT.xs,
    fontWeight: '600',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
    marginBottom: SPACING.xs,
    marginLeft: SPACING.xs,
  },
  card: { borderWidth: 1, borderRadius: RADIUS.md, overflow: 'hidden' },
  row: {
    minHeight: 70,
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  button: {
    minHeight: 70,
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  rowText: { flex: 1, marginRight: SPACING.sm },
  label: { fontSize: FONT.md, fontWeight: '600' },
  description: { fontSize: FONT.xs, lineHeight: 17, marginTop: 3 },
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  loadingText: { fontSize: FONT.md },
});

export default withScreenErrorBoundary(DefenseScreen, 'Defense');