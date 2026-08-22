import React, { useCallback, useRef, useState } from 'react';
import {
  Alert,
  Platform,
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
import { GreyoutScheduleModal } from '@/components/GreyoutScheduleModal';
import { ActiveHeaderButton } from '@/components/ActiveHeaderButton';
import { PinSetupModal } from '@/components/PinSetupModal';
import { PinVerifyModal } from '@/components/PinVerifyModal';
import { PinRotationModal } from '@/components/PinRotationModal';
import { VpnConsentModal } from '@/components/VpnConsentModal';
import { withScreenErrorBoundary } from '@/components/withScreenErrorBoundary';
import { NetworkBlockModule } from '@/native-modules/NetworkBlockModule';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import AsyncStorage from '@react-native-async-storage/async-storage';

type DefenseAction = (defensePinHash?: string) => void;
const DEFENSE_HINT_DISMISSED_KEY = '@focusflow/defenseHintDismissed';

function DefenseScreen() {
  const insets = useSafeAreaInsets();
  const { theme } = useTheme();
  const { state, updateSettings, setDailyAllowanceEntries } = useApp();
  const { settings } = state;
  const activeBlock = state.focusSession?.isActive === true || isStandaloneActive(settings);

  const [dailyAllowanceVisible, setDailyAllowanceVisible] = useState(false);
  const [greyoutScheduleVisible, setGreyoutScheduleVisible] = useState(false);
  const [pinModal, setPinModal] = useState<
    | { type: 'none' }
    | { type: 'verify'; title: string; description: string; action: DefenseAction }
    | { type: 'setup'; action: DefenseAction }
  >({ type: 'none' });
  const [showDefenseHint, setShowDefenseHint] = useState(false);
  const [alwaysOnPinRotationVisible, setAlwaysOnPinRotationVisible] = useState(false);
  const [vpnConsentVisible, setVpnConsentVisible] = useState(false);
  const vpnConsentResolveRef = useRef<((confirmed: boolean) => void) | null>(null);
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
      | 'vpnBlockEnabled'
      | 'vpnSelfHealEnabled'
      | 'launcherLockDuringStandalone'
      | 'launcherBlockUninstall'
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

  const showVpnConsent = (): Promise<boolean> =>
    new Promise((resolve) => {
      vpnConsentResolveRef.current = resolve;
      setVpnConsentVisible(true);
    });

  const handleVpnToggle = async (enabled: boolean) => {
    if (!enabled && activeBlock) {
      Alert.alert(
        'VPN protection is locked',
        'Network Protection cannot be turned off while a Focus session or Standalone block is running.',
      );
      return;
    }
    if (!enabled) {
      requireDefensePin(
        'Disable Network Protection',
        'Enter your Defense Password to turn off VPN blocking.',
        (defensePinHash) => void update({ vpnBlockEnabled: false }, defensePinHash ?? null),
      );
      return;
    }

    const consented = await showVpnConsent();
    if (!consented) return;
    if (Platform.OS === 'android') {
      try {
        const conflicting = await NetworkBlockModule.isAnotherVpnActive();
        if (conflicting) {
          const takeOver = await new Promise<boolean>((resolve) => {
            Alert.alert(
              'Another VPN is active',
              'Android only allows one VPN at a time. FocusFlow will temporarily take over while your block runs. You will need to reconnect your other VPN afterwards.',
              [
                { text: 'Cancel', style: 'cancel', onPress: () => resolve(false) },
                { text: 'Take over', onPress: () => resolve(true) },
              ],
            );
          });
          if (!takeOver) return;
        }
        if (!(await NetworkBlockModule.isVpnPermissionGranted())) {
          await NetworkBlockModule.requestVpnPermission();
        }
      } catch {}
    }
    void update({ vpnBlockEnabled: true });
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
  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <Header theme={theme} />
      {showDefenseHint && (
        <View style={[styles.hintBanner, { backgroundColor: COLORS.primary + '12', borderColor: COLORS.primary + '35' }]}>
          <Ionicons name="information-circle-outline" size={20} color={COLORS.primary} />
          <Text style={[styles.hintText, { color: theme.text }]}>
            Password Protection has its own page below the blocking tools, so your security settings stay easy to find.
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
      {activeBlock && (
        <View style={[styles.activeNotice, { backgroundColor: COLORS.orange + '18', borderColor: COLORS.orange + '55' }]}>
          <Ionicons name="lock-closed-outline" size={18} color={COLORS.orange} />
          <Text style={[styles.activeNoticeText, { color: theme.text }]}>
            Protection settings are locked while a {state.focusSession?.isActive ? 'Focus session' : 'Standalone block'} is running. Turn-offs will be available when it ends.
          </Text>
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
                    if (activeBlock) {
                      Alert.alert(
                        'Always-On Enforcement is locked',
                        'Always-On Enforcement cannot be turned off while a Focus session or Standalone block is running.',
                      );
                      return;
                    }
                  requireDefensePin(
                    'Disable Always-On Enforcement',
                    'Enter your defense password to turn off always-on protection.',
                    (hash) => {
                      void update({ alwaysOnEnforcementEnabled: false }, hash ?? null);
                      setAlwaysOnPinRotationVisible(true);
                    },
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
            label="Block Schedule"
            description="Manage recurring time-window blocks"
            onPress={() =>
              requireDefensePin(
                'Manage Block Schedules',
                'Enter your defense password to add, edit, or remove schedule batches.',
                () => setGreyoutScheduleVisible(true),
              )
            }
            theme={theme}
          />
          <SettingRow
            label="Network Protection (VPN)"
            description={
              activeBlock && (settings.vpnBlockEnabled ?? false)
                ? 'Locked on — Focus session or Standalone block is running'
                : 'Cut internet access for selected apps through FocusFlow’s local VPN'
            }
            theme={theme}
          >
            <Switch
              value={settings.vpnBlockEnabled ?? false}
              onValueChange={(value) => void handleVpnToggle(value)}
              disabled={activeBlock && (settings.vpnBlockEnabled ?? false)}
              trackColor={{ false: theme.border, true: COLORS.primary + '88' }}
              thumbColor={settings.vpnBlockEnabled ? COLORS.primary : theme.muted}
            />
          </SettingRow>
          <SettingRow
            label="VPN Self-Healing"
            description={
              activeBlock && (settings.vpnSelfHealEnabled ?? false)
                ? 'Locked on — active block in progress'
                : 'Restart the VPN if it disconnects during an active block'
            }
            theme={theme}
          >
            <Switch
              value={settings.vpnSelfHealEnabled ?? false}
              onValueChange={(value) => void update({ vpnSelfHealEnabled: value })}
              disabled={!(settings.vpnBlockEnabled ?? false) || (activeBlock && (settings.vpnSelfHealEnabled ?? false))}
              trackColor={{ false: theme.border, true: COLORS.primary + '88' }}
              thumbColor={settings.vpnSelfHealEnabled ? COLORS.primary : theme.muted}
            />
          </SettingRow>
          <SettingButton
            icon="list-outline"
            label="Manage VPN App List"
            description="Choose which apps should have internet access blocked"
            onPress={() => router.push('/vpn-block-list')}
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
            onPress={() => router.push('/password-protection')}
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

        <Section title="Focus Session Behaviour" theme={theme}>
          <SettingRow
            label="Keep focus active for the full duration"
            description={
              settings.keepFocusActiveUntilTaskEnd
                ? 'On — completing a task early keeps app-blocking running until the original end time'
                : 'Off — completing a task immediately ends the focus session (default)'
            }
            theme={theme}
          >
            <Switch
              value={settings.keepFocusActiveUntilTaskEnd ?? false}
              onValueChange={(value) => void update({ keepFocusActiveUntilTaskEnd: value })}
              trackColor={{ false: theme.border, true: COLORS.primary + '88' }}
              thumbColor={settings.keepFocusActiveUntilTaskEnd ? COLORS.primary : theme.muted}
            />
          </SettingRow>
        </Section>

        <Section title="Home Launcher" theme={theme}>
          <SettingRow
            label="Lock launcher during standalone block"
            description={
              activeBlock && (settings.launcherLockDuringStandalone ?? true)
                ? 'Locked on — active block in progress'
                : 'Prevent switching away from FocusFlow Launcher during a Standalone block'
            }
            theme={theme}
          >
            <Switch
              value={settings.launcherLockDuringStandalone ?? true}
              onValueChange={(value) => {
                if (!value && isStandaloneActive(settings)) {
                  Alert.alert('Home Launcher is locked', 'This option cannot be turned off while a Standalone block is running.');
                  return;
                }
                void update({ launcherLockDuringStandalone: value });
              }}
              disabled={isStandaloneActive(settings) && (settings.launcherLockDuringStandalone ?? true)}
              trackColor={{ false: theme.border, true: COLORS.primary + '88' }}
              thumbColor={settings.launcherLockDuringStandalone !== false ? COLORS.primary : theme.muted}
            />
          </SettingRow>
          <SettingRow
            label="Block uninstall from launcher long-press"
            description={
              activeBlock && (settings.launcherBlockUninstall ?? false)
                ? 'Locked on — active block in progress'
                : 'Hide Uninstall from the launcher long-press menu'
            }
            theme={theme}
          >
            <Switch
              value={settings.launcherBlockUninstall ?? false}
              onValueChange={(value) => {
                if (!value && activeBlock) {
                  Alert.alert('Uninstall protection is locked', 'This option cannot be turned off while a Focus session or Standalone block is running.');
                  return;
                }
                void update({ launcherBlockUninstall: value });
              }}
              disabled={activeBlock && (settings.launcherBlockUninstall ?? false)}
              trackColor={{ false: theme.border, true: COLORS.primary + '88' }}
              thumbColor={settings.launcherBlockUninstall ? COLORS.primary : theme.muted}
            />
          </SettingRow>
          <SettingButton
            icon="home-outline"
            label="Configure Home Launcher"
            description="Choose pinned apps, hidden apps, wallpaper, and clock style"
            onPress={() => {
              if (isStandaloneActive(settings)) {
                Alert.alert('Home Launcher is locked', 'Launcher settings are unavailable while a Standalone block is running.');
                return;
              }
              router.push('/home-launcher');
            }}
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

      <GreyoutScheduleModal
        visible={greyoutScheduleVisible}
        windows={settings.greyoutSchedule ?? []}
        standaloneActive={isStandaloneActive(settings)}
        onSave={async (windows) => {
          await update({ greyoutSchedule: windows });
          setGreyoutScheduleVisible(false);
        }}
        onClose={() => setGreyoutScheduleVisible(false)}
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
      <PinRotationModal
        visible={alwaysOnPinRotationVisible}
        pinType="defense"
        reuseTrackerKey="alwayson"
        actionLabel="Update Always-On Password"
        actionDescription="Always-On Enforcement has been paused. Set the password that will be required next time you change this setting."
        onComplete={() => setAlwaysOnPinRotationVisible(false)}
        onCancel={() => setAlwaysOnPinRotationVisible(false)}
      />
      <VpnConsentModal
        visible={vpnConsentVisible}
        onConfirm={() => {
          setVpnConsentVisible(false);
          vpnConsentResolveRef.current?.(true);
          vpnConsentResolveRef.current = null;
        }}
        onCancel={() => {
          setVpnConsentVisible(false);
          vpnConsentResolveRef.current?.(false);
          vpnConsentResolveRef.current = null;
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
        <ActiveHeaderButton />
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
  activeNotice: {
    marginHorizontal: SPACING.md,
    marginTop: SPACING.sm,
    padding: SPACING.sm,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
  },
  activeNoticeText: { flex: 1, fontSize: FONT.xs, lineHeight: 17, fontWeight: '600' },
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