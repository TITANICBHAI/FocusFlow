/**
 * OnboardingScreen
 *
 * Shown on first launch as a full-screen modal over the tabs.
 * Rich expandable permission cards matching the detail level of
 * the manage-permissions screen. Core access and optional setup are
 * shown as separate steps; personalization happens later from Settings.
 *
 * - Auto-checks all permission statuses on mount and on every app-resume.
 * - Button is always enabled; if permissions are missing a tip points the
 *   user to Settings → Permissions instead of blocking entry.
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { PinSetupModal } from '@/components/PinSetupModal';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Switch,
  ActivityIndicator,
  AppState,
  Linking,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import * as Notifications from 'expo-notifications';
import { NativeImagePickerModule } from '@/native-modules/NativeImagePickerModule';
import { NetworkBlockModule } from '@/native-modules/NetworkBlockModule';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import { Alert } from 'react-native';
import { useApp } from '@/context/AppContext';
import { useTheme } from '@/hooks/useTheme';
import { requestPermissions } from '@/services/notificationService';
import { ForegroundServiceModule } from '@/native-modules/ForegroundServiceModule';
import { UsageStatsModule } from '@/native-modules/UsageStatsModule';
import { ForegroundLaunchModule } from '@/native-modules/ForegroundLaunchModule';
import { RestrictedSettingsBanner } from '@/components/RestrictedSettingsBanner';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

type PermStatus = 'granted' | 'denied' | 'unknown';
type OnboardingStep = 'core' | 'optional';
type ProtectionMode = 'standard' | 'iron';

interface PermItem {
  id: string;
  icon: keyof typeof Ionicons.glyphMap;
  section: OnboardingStep;
  requiredToContinue?: boolean;
  title: string;
  description: string;
  whyNeeded: string;
  brokenWithout: string[];
  deepLinkLabel: string;
  grantAction: 'auto' | 'manual';
}

const PERMISSIONS: PermItem[] = [
  {
    id: 'media',
    section: 'optional',
    icon: 'images-outline',
    title: 'Media & Files',
    description: 'Access your photo library to set a custom wallpaper on the block screen.',
    whyNeeded:
      'Only needed if you want to pick a custom background image for the block overlay. The default wallpaper works without this.',
    brokenWithout: [
      'You cannot pick a custom wallpaper for the block screen',
      'The default built-in wallpaper will be used instead',
    ],
    deepLinkLabel: 'Allow Media Access',
    grantAction: 'auto',
  },
  {
    id: 'notifications',
    section: 'core',
    requiredToContinue: true,
    icon: 'notifications-outline',
    title: 'Notifications',
    description: 'Task reminders and live focus session alerts.',
    whyNeeded:
      'Required for all alerts, task reminders, and keeping the foreground service visible to Android.',
    brokenWithout: [
      'No task start or end reminders',
      'The focus session notification disappears',
      'Android may kill the blocking service without the persistent notification',
    ],
    deepLinkLabel: 'Allow Notifications',
    grantAction: 'auto',
  },
  {
    id: 'battery',
    section: 'core',
    icon: 'battery-charging-outline',
    title: 'Battery Optimization',
    description: 'Keeps FocusFlow alive in the background on all OEM ROMs.',
    whyNeeded:
      'Samsung, Xiaomi, Realme, and OnePlus phones aggressively kill background services — this exemption stops that.',
    brokenWithout: [
      'Blocking service gets killed within minutes on most phones',
      'Focus sessions stop enforcing after the screen turns off',
      'Especially severe on Samsung One UI, MIUI, and ColorOS',
    ],
    deepLinkLabel: 'Request Exemption',
    grantAction: 'auto',
  },
  {
    id: 'overlay',
    section: 'core',
    requiredToContinue: true,
    icon: 'layers-outline',
    title: 'Appear on Top',
    description: 'Draws the block screen directly over blocked apps.',
    whyNeeded:
      'This lets FocusFlow cover blocked apps instantly without briefly showing the app underneath.',
    brokenWithout: [
      'Block overlay opens inside FocusFlow instead of directly over the blocked app',
      'A brief flash of the blocked app may appear before redirect',
    ],
    deepLinkLabel: 'Enable Appear on Top',
    grantAction: 'manual',
  },
  {
    id: 'usage',
    section: 'core',
    requiredToContinue: true,
    icon: 'analytics-outline',
    title: 'Usage Access',
    description: 'Lets FocusFlow see which app is in the foreground.',
    whyNeeded:
      'Without this, FocusFlow is blind — it cannot detect which app you switched to.',
    brokenWithout: [
      'FocusFlow cannot detect which app you opened',
      'App blocking will silently fail',
      'Stats and focus session tracking will be inaccurate',
    ],
    deepLinkLabel: 'Open Usage Access Settings',
    grantAction: 'manual',
  },
  {
    id: 'accessibility',
    section: 'core',
    requiredToContinue: true,
    icon: 'eye-outline',
    title: 'Accessibility Service',
    description: 'Redirects you away from blocked apps the instant you open them.',
    whyNeeded:
      'This is how FocusFlow instantly redirects you the moment you open a blocked app during a focus session.',
    brokenWithout: [
      'App blocking will not work at all',
      'Blocked apps will open freely during focus sessions',
      'You can bypass all blocks with no consequence',
    ],
    deepLinkLabel: 'Open Accessibility Settings',
    grantAction: 'manual',
  },
  {
    id: 'vpn',
    section: 'optional',
    icon: 'shield-half-outline',
    title: 'VPN Permission',
    description: 'Cuts internet access for selected distracting apps during a focus session — without blocking your own traffic.',
    whyNeeded:
      'Iron Mode can combine app blocking with a local VPN layer. For example: prepare downloaded work videos before a session, then restrict network access for distracting apps while you focus. Downloaded content may remain available offline — though some apps require occasional verification.',
    brokenWithout: [
      'Network Blocking (Block Enforcement → System Protection) will not start',
      'Blocked apps will still have full internet access during a focus session',
    ],
    deepLinkLabel: 'Allow VPN',
    grantAction: 'auto',
  },
  {
    id: 'device_admin',
    section: 'optional',
    icon: 'shield-outline',
    title: 'Device Admin',
    description: 'Makes it harder for Samsung, Xiaomi, and other OEM phones to force-stop FocusFlow during a focus session.',
    whyNeeded:
      'Some OEM ROMs make it easy to swipe away or force-stop apps from recents. Device Admin adds resistance to those paths, but it is not an absolute uninstall or force-stop guarantee.',
    brokenWithout: [
      'On Samsung One UI, MIUI, and ColorOS, stopping FocusFlow may be easier',
      'Some device or advanced system paths may end a focus session sooner',
    ],
    deepLinkLabel: 'Activate Device Admin',
    grantAction: 'manual',
  },
];

const CORE_LAYER_GROUPS: Array<{
  label: string;
  subtitle: string;
  ids: string[];
}> = [
  {
    label: 'Layer 1 — Detect',
    subtitle: 'Lets FocusFlow see which app is open and redirect instantly.',
    ids: ['usage', 'accessibility'],
  },
  {
    label: 'Layer 2 — Stay active',
    subtitle: 'Keeps the blocking service alive on aggressive OEM phones.',
    ids: ['notifications', 'battery', 'overlay'],
  },
];

async function checkStatus(id: string): Promise<PermStatus> {
  try {
    switch (id) {
      case 'media': {
        const granted = await NativeImagePickerModule.checkMediaPermission();
        return granted ? 'granted' : 'denied';
      }
      case 'notifications': {
        const { status } = await Notifications.getPermissionsAsync();
        return status === 'granted' ? 'granted' : 'denied';
      }
      case 'battery': {
        const ok = await UsageStatsModule.isIgnoringBatteryOptimizations();
        return ok ? 'granted' : 'denied';
      }
      case 'usage': {
        const ok = await UsageStatsModule.hasPermission();
        return ok ? 'granted' : 'denied';
      }
      case 'accessibility': {
        const ok = await UsageStatsModule.hasAccessibilityPermission();
        return ok ? 'granted' : 'denied';
      }
      case 'overlay': {
        const ok = await ForegroundLaunchModule.hasOverlayPermission();
        return ok ? 'granted' : 'denied';
      }
      case 'vpn': {
        const ok = await NetworkBlockModule.isVpnPermissionGranted();
        return ok ? 'granted' : 'denied';
      }
      case 'device_admin': {
        const ok = await UsageStatsModule.isDeviceAdminActive();
        return ok ? 'granted' : 'denied';
      }
      default:
        return 'denied';
    }
  } catch {
    return 'denied';
  }
}

export default function OnboardingScreen() {
  const { state, updateSettings } = useApp();
  const { theme } = useTheme();
  const [step, setStep] = useState<OnboardingStep>('core');
  const [protectionMode, setProtectionMode] = useState<ProtectionMode>(
    state.settings.protectionMode ?? 'standard',
  );
  const [statuses, setStatuses] = useState<Record<string, PermStatus>>({});
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [pinProtectionChoice, setPinProtectionChoice] = useState(false);
  const [defensePinSet, setDefensePinSet] = useState(false);
  const [pinSetupVisible, setPinSetupVisible] = useState(false);
  const appStateRef = useRef(AppState.currentState);

  // Check whether a defense PIN is already stored (e.g. user came back to onboarding)
  useEffect(() => {
    SharedPrefsModule.getString('defense_pin_hash')
      .then((hash) => setDefensePinSet(!!hash))
      .catch(() => {});
  }, []);

  const checkAll = useCallback(async () => {
    const result: Record<string, PermStatus> = {};
    await Promise.all(
      PERMISSIONS.map(async (p) => {
        result[p.id] = await checkStatus(p.id);
      })
    );
    setStatuses(result);
  }, []);

  useEffect(() => {
    void checkAll();
  }, [checkAll]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', async (next) => {
      if (appStateRef.current.match(/inactive|background/) && next === 'active') {
        await checkAll();
      }
      appStateRef.current = next;
    });
    return () => sub.remove();
  }, [checkAll]);

  const handleGrant = async (perm: PermItem) => {
    if (statuses[perm.id] === 'granted') return;
    setActionLoading(perm.id);
    try {
      if (perm.id === 'media') {
        const granted = await NativeImagePickerModule.requestMediaPermission();
        setStatuses((prev) => ({ ...prev, media: granted ? 'granted' : 'denied' }));
      } else if (perm.id === 'notifications') {
        const granted = await requestPermissions();
        setStatuses((prev) => ({ ...prev, notifications: granted ? 'granted' : 'denied' }));
      } else if (perm.id === 'battery') {
        await ForegroundServiceModule.requestBatteryOptimizationExemption();
        setTimeout(async () => {
          const s = await checkStatus('battery');
          setStatuses((prev) => ({ ...prev, battery: s }));
        }, 800);
      } else if (perm.id === 'usage') {
        if (Platform.OS === 'android') {
          await Linking.sendIntent('android.settings.USAGE_ACCESS_SETTINGS');
        }
      } else if (perm.id === 'accessibility') {
        await UsageStatsModule.openAccessibilitySettings();
      } else if (perm.id === 'overlay') {
        await ForegroundLaunchModule.requestOverlayPermission();
      } else if (perm.id === 'vpn') {
        await NetworkBlockModule.requestVpnPermission();
        setTimeout(async () => {
          const s = await checkStatus('vpn');
          setStatuses((prev) => ({ ...prev, vpn: s }));
        }, 800);
      } else if (perm.id === 'device_admin') {
        await UsageStatsModule.openDeviceAdminSettings();
        setTimeout(async () => {
          const s = await checkStatus('device_admin');
          setStatuses((prev) => ({ ...prev, device_admin: s }));
        }, 800);
      }
    } catch {
      try {
        await Linking.openSettings();
      } catch { /* ignore */ }
    } finally {
      setActionLoading(null);
    }
  };

  const corePerms = PERMISSIONS.filter((p) => p.section === 'core');
  const optionalPerms = PERMISSIONS.filter((p) => p.section === 'optional');
  const requiredPerms = corePerms.filter((p) => p.requiredToContinue);
  const visibleCorePerms =
    protectionMode === 'iron' ? corePerms : requiredPerms;
  const grantedCount = requiredPerms.filter((p) => statuses[p.id] === 'granted').length;
  const allRequiredReady = grantedCount === requiredPerms.length;
  const allOptionalGranted = optionalPerms.every((p) => statuses[p.id] === 'granted');

  const renderPermissionCard = (perm: PermItem, showOptionalBadge = false) => {
    const status = statuses[perm.id] ?? 'unknown';
    const isExpanded = expandedId === perm.id;
    const isLoading = actionLoading === perm.id;

    return (
      <View
        key={perm.id}
        style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }, status === 'granted' && styles.cardGranted]}
      >
        <TouchableOpacity
          style={styles.cardMain}
          onPress={() => setExpandedId(isExpanded ? null : perm.id)}
          activeOpacity={0.75}
        >
          <View style={[styles.iconWrap, { backgroundColor: statusColor(status, perm.requiredToContinue) + '22' }]}>
            <Ionicons name={perm.icon} size={22} color={statusColor(status, perm.requiredToContinue)} />
          </View>

          <View style={styles.cardBody}>
            <View style={styles.cardTitleRow}>
              <Text style={[styles.cardTitle, { color: theme.text }]}>{perm.title}</Text>
              {showOptionalBadge && (
                <View style={[badge.wrap, { backgroundColor: COLORS.primary + '18', borderColor: COLORS.primary + '33', marginRight: 4 }]}>
                  <Text style={[badge.text, { color: COLORS.primary }]}>Optional</Text>
                </View>
              )}
              <StatusBadge status={status} requiredToContinue={perm.requiredToContinue} />
            </View>
            <Text style={[styles.cardDesc, { color: theme.muted }]} numberOfLines={isExpanded ? undefined : 2}>
              {perm.description}
            </Text>
          </View>

          <Ionicons
            name={isExpanded ? 'chevron-up' : 'chevron-down'}
            size={16}
            color={theme.muted}
          />
        </TouchableOpacity>

        {isExpanded && (
          <View style={[styles.expandedSection, { backgroundColor: theme.surface, borderTopColor: theme.border }]}>
            <View style={styles.whyBox}>
              <Ionicons name="bulb-outline" size={14} color={COLORS.orange} />
              <Text style={styles.whyText}>{perm.whyNeeded}</Text>
            </View>

            {status !== 'granted' && (
              <View style={styles.brokenSection}>
                <Text style={[styles.brokenTitle, { color: theme.text }]}>Without this permission:</Text>
                {perm.brokenWithout.map((item, i) => (
                  <View key={i} style={styles.brokenRow}>
                    <Ionicons name="close-circle" size={14} color={COLORS.red} />
                    <Text style={[styles.brokenText, { color: theme.textSecondary }]}>{item}</Text>
                  </View>
                ))}
              </View>
            )}

            {status !== 'granted' && (
              <TouchableOpacity
                style={styles.grantBtn}
                onPress={() => handleGrant(perm)}
                disabled={isLoading}
              >
                {isLoading ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <>
                    <Ionicons name="open-outline" size={14} color="#fff" />
                    <Text style={styles.grantBtnText}>{perm.deepLinkLabel}</Text>
                  </>
                )}
              </TouchableOpacity>
            )}
          </View>
        )}
      </View>
    );
  };

  const handleFinish = async () => {
    try {
      await updateSettings({
        ...state.settings,
        onboardingComplete: true,
        pinProtectionEnabled: pinProtectionChoice,
          protectionMode,
      });
    } catch {
      Alert.alert('Could not finish setup', 'Please try again.');
      return;
    }
    // Write background service consent flag so BootReceiver knows the user
    // has completed onboarding and explicitly authorised background operation.
    // Huawei AppGallery rule 2.19: foreground services must not start without
    // user authorisation. This flag gates the idle auto-start on reboot.
    try {
      await SharedPrefsModule.putString('user_consented_background_service', 'true');
    } catch { /* non-fatal */ }
    try {
      await SharedPrefsModule.putString('onboarding_complete', 'true');
    } catch { /* non-fatal */ }
    router.replace('/how-to-use?onboarding=1');
  };

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>

        {/* Header */}
        <View style={styles.header}>
          <View style={styles.logoCircle}>
            <Ionicons name="shield-checkmark" size={38} color="#fff" />
          </View>
          <Text style={[styles.appName, { color: theme.text }]}>FocusFlow</Text>
          <Text style={[styles.stepTitle, { color: theme.text }]}>
            {step === 'core' ? 'Set up core access' : 'Optional protection'}
          </Text>
          <Text style={[styles.tagline, { color: theme.muted }]}>
            {step === 'core'
              ? protectionMode === 'iron'
                ? 'Build the strongest protection against distraction.'
                : 'Start with the essential access FocusFlow needs.'
              : 'Finish the extra layers or come back later.'}
          </Text>
        </View>

        {step === 'core' && (
          <>
        {/* Protection mode choice stays on the existing onboarding screen so
            choosing a stronger setup does not add another welcome step. */}
        <View style={[styles.modeCard, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <Text style={[styles.modeTitle, { color: theme.text }]}>Choose your protection level</Text>
          <Text style={[styles.modeSubtitle, { color: theme.muted }]}>
            You can change this later from Settings → Permissions.
          </Text>
          <View style={styles.modeOptions}>
            <TouchableOpacity
              style={[
                styles.modeOption,
                { borderColor: protectionMode === 'standard' ? COLORS.primary : theme.border },
                protectionMode === 'standard' && { backgroundColor: COLORS.primaryLight },
              ]}
              onPress={() => setProtectionMode('standard')}
              activeOpacity={0.8}
            >
              <Ionicons
                name="shield-outline"
                size={22}
                color={protectionMode === 'standard' ? COLORS.primary : theme.muted}
              />
              <View style={styles.modeOptionBody}>
                <Text style={[styles.modeOptionTitle, { color: theme.text }]}>Standard</Text>
                <Text style={[styles.modeOptionText, { color: theme.muted }]}>
                  Essential app blocking with four core permissions.
                </Text>
              </View>
              {protectionMode === 'standard' && (
                <Ionicons name="checkmark-circle" size={20} color={COLORS.primary} />
              )}
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.modeOption,
                { borderColor: protectionMode === 'iron' ? COLORS.orange : theme.border },
                protectionMode === 'iron' && { backgroundColor: COLORS.orangeLight },
              ]}
              onPress={() => setProtectionMode('iron')}
              activeOpacity={0.8}
            >
              <Ionicons
                name="shield-checkmark"
                size={22}
                color={protectionMode === 'iron' ? COLORS.orange : theme.muted}
              />
              <View style={styles.modeOptionBody}>
                <View style={styles.modeOptionTitleRow}>
                  <Text style={[styles.modeOptionTitle, { color: theme.text }]}>Iron Mode</Text>
                  <View style={styles.ironBadge}>
                    <Text style={styles.ironBadgeText}>STRONGER</Text>
                  </View>
                </View>
                <Text style={[styles.modeOptionText, { color: theme.muted }]}>
                  Adds battery, VPN, Device Admin, and PIN protection.
                </Text>
              </View>
              {protectionMode === 'iron' && (
                <Ionicons name="checkmark-circle" size={20} color={COLORS.orange} />
              )}
            </TouchableOpacity>
          </View>
        </View>

        {/* Restricted-settings unlock banner — shown above everything else
            on the first-run flow when the OS is currently locking the
            Accessibility toggle. Auto-hides the moment the user completes
            the App Info → ⋮ → Allow restricted settings flow. */}
        <View style={{ marginHorizontal: SPACING.lg, marginBottom: SPACING.md }}>
          <RestrictedSettingsBanner />
        </View>

        {/* Why banner */}
        <View style={styles.tutorialBanner}>
          <View style={styles.tutorialIconWrap}>
            <Ionicons name="shield-checkmark" size={24} color={COLORS.primary} />
          </View>
          <View style={styles.tutorialTextWrap}>
            <Text style={styles.tutorialTitle}>Why these permissions?</Text>
            <Text style={styles.tutorialBody}>
              FocusFlow enforces focus at the system level — not just reminders.
              To actually block apps and keep your session running, Android requires
              special access that regular apps don't need.
            </Text>
          </View>
        </View>

        {/* Progress bar */}
        <View style={styles.progressSection}>
          <View style={styles.progressLabelRow}>
            <Text style={[styles.progressLabel, { color: theme.muted }]}>Required access ready</Text>
            <Text style={[styles.progressCount, allRequiredReady && styles.progressCountDone]}>
              {grantedCount} / {requiredPerms.length}
            </Text>
          </View>
          <View style={[styles.progressBar, { backgroundColor: theme.border }]}>
            <View
              style={[
                styles.progressFill,
                {
                  width: `${(grantedCount / requiredPerms.length) * 100}%` as any,
                  backgroundColor: allRequiredReady ? COLORS.green : COLORS.primary,
                },
              ]}
            />
          </View>
          {allRequiredReady && (
            <Text style={styles.allSetText}>
              Core blocking access is ready.
            </Text>
          )}
        </View>

        {/* Section label */}
         <Text style={[styles.sectionLabel, { color: theme.muted }]}>
           {protectionMode === 'iron' ? 'IRON MODE CORE ACCESS' : 'ESSENTIAL ACCESS'} — TAP A CARD TO SEE DETAILS
         </Text>

         {/* Core permission cards */}
         {protectionMode === 'standard'
           ? visibleCorePerms.map((perm) => renderPermissionCard(perm))
           : CORE_LAYER_GROUPS.map((group) => {
               const groupPerms = PERMISSIONS.filter((p) => group.ids.includes(p.id));
               const allGranted = groupPerms.every((p) => statuses[p.id] === 'granted');

               return (
                 <View key={group.label}>
                   <View style={styles.layerHeader}>
                     <View style={styles.layerHeaderLeft}>
                       <Text style={[styles.layerLabel, { color: allGranted ? COLORS.green : COLORS.orange }]}>
                         {group.label}
                       </Text>
                       {allGranted && (
                         <Ionicons name="checkmark-circle" size={14} color={COLORS.green} />
                       )}
                     </View>
                     <Text style={[styles.layerSubtitle, { color: theme.muted }]}>{group.subtitle}</Text>
                   </View>
                   {groupPerms.map((perm) => renderPermissionCard(perm))}
                 </View>
               );
             })}

        {/* Missing required permissions tip */}
        {!allRequiredReady && (
          <View style={styles.manageTip}>
            <Ionicons name="information-circle-outline" size={18} color={COLORS.primary} />
            <Text style={[styles.manageTipText, { color: theme.textSecondary }]}>
              Usage Access, Accessibility Service, Appear on Top, and Notifications can be fixed anytime in{' '}
              <Text style={styles.manageTipHighlight}>Settings → Permissions</Text>.
            </Text>
          </View>
        )}

        {protectionMode === 'standard' && (
          <View style={[styles.ironPromo, { backgroundColor: COLORS.orangeLight, borderColor: COLORS.orange + '44' }]}>
            <Ionicons name="shield-checkmark-outline" size={20} color={COLORS.orange} />
            <View style={styles.ironPromoBody}>
              <Text style={[styles.ironPromoTitle, { color: theme.text }]}>Want stronger protection later?</Text>
              <Text style={[styles.ironPromoText, { color: theme.textSecondary }]}>
                Switch to Iron Mode anytime from Settings → Permissions to add VPN network blocking, Device Admin, Battery Optimization, and PIN protection.
              </Text>
            </View>
          </View>
        )}

        {protectionMode === 'iron' && (
          <View style={[styles.ironPromo, { backgroundColor: COLORS.orangeLight, borderColor: COLORS.orange + '44' }]}>
            <Ionicons name="lock-closed-outline" size={20} color={COLORS.orange} />
            <View style={styles.ironPromoBody}>
              <Text style={[styles.ironPromoTitle, { color: theme.text }]}>Iron Mode adds extra resistance</Text>
              <Text style={[styles.ironPromoText, { color: theme.textSecondary }]}>
                VPN can cut network access for selected distracting apps, while Device Admin makes force-stopping FocusFlow harder. You can still use a selected app’s downloaded content offline where Android allows it.
              </Text>
            </View>
          </View>
        )}
          </>
        )}

        {step === 'optional' && (
          <>
        <TouchableOpacity
          style={styles.backToCore}
          onPress={() => setStep('core')}
          activeOpacity={0.75}
        >
          <Ionicons name="chevron-back" size={16} color={COLORS.primary} />
          <Text style={styles.backToCoreText}>Back to core setup</Text>
        </TouchableOpacity>

         {/* Optional permission cards */}
         <View style={styles.layerHeader}>
           <View style={styles.layerHeaderLeft}>
             <Text style={[styles.layerLabel, { color: allOptionalGranted ? COLORS.green : COLORS.orange }]}>
               Layer 3 — Resist bypass
             </Text>
             {allOptionalGranted && (
               <Ionicons name="checkmark-circle" size={14} color={COLORS.green} />
             )}
           </View>
           <Text style={[styles.layerSubtitle, { color: theme.muted }]}>
             Cuts network access, resists force-stop, and locks settings behind a PIN.
           </Text>
         </View>
        <Text style={[styles.optionalHint, { color: theme.muted }]}>
           Configure the extra Iron Mode layers now, or enable them later from Settings → Permissions.
        </Text>
         {optionalPerms.map((perm) => renderPermissionCard(perm, true))}

        {/* ── PIN Protection preference ─────────────────────────────────── */}
        <Text style={[styles.sectionLabel, { color: theme.muted, marginTop: SPACING.sm }]}>
          SECURITY PREFERENCE
        </Text>
        <View style={[styles.pinCard, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <View style={styles.pinCardMain}>
            <View style={[styles.pinCardIcon, { backgroundColor: COLORS.primary + '1A' }]}>
              <Ionicons name="lock-closed-outline" size={22} color={COLORS.primary} />
            </View>
            <View style={styles.pinCardBody}>
              <Text style={[styles.pinCardTitle, { color: theme.text }]}>
                PIN Protection
              </Text>
              <Text style={[styles.pinCardDesc, { color: theme.muted }]}>
                Require a password to disable block enforcement toggles. Prevents impulsive self-sabotage mid-session.
              </Text>
            </View>
            <Switch
              value={pinProtectionChoice}
              onValueChange={setPinProtectionChoice}
              trackColor={{ false: COLORS.border, true: COLORS.primary + '88' }}
              thumbColor={pinProtectionChoice ? COLORS.primary : COLORS.muted}
            />
          </View>
          {pinProtectionChoice && defensePinSet && (
            <View style={[styles.pinCardHint, { backgroundColor: COLORS.green + '12', borderTopColor: theme.border }]}>
              <Ionicons name="checkmark-circle-outline" size={14} color={COLORS.green} />
              <Text style={[styles.pinCardHintText, { color: COLORS.green }]}>
                Defense Password set — your protections are locked.
              </Text>
            </View>
          )}
          {pinProtectionChoice && !defensePinSet && (
            <View style={[styles.pinCardHint, { backgroundColor: COLORS.primary + '0D', borderTopColor: theme.border }]}>
              <View style={{ flex: 1, gap: SPACING.xs }}>
                <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: SPACING.xs }}>
                  <Ionicons name="information-circle-outline" size={14} color={COLORS.primary} style={{ marginTop: 1 }} />
                  <Text style={[styles.pinCardHintText, { color: theme.muted, flex: 1 }]}>
                    Set your Defense Password now — or add it later in Block Enforcement.
                  </Text>
                </View>
                <TouchableOpacity
                  style={styles.setPinBtn}
                  onPress={() => setPinSetupVisible(true)}
                  activeOpacity={0.8}
                >
                  <Ionicons name="key-outline" size={14} color="#fff" />
                  <Text style={styles.setPinBtnText}>Set Password Now</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
          {!pinProtectionChoice && (
            <View style={[styles.pinCardHint, { backgroundColor: theme.border + '33', borderTopColor: theme.border }]}>
              <Ionicons name="information-circle-outline" size={14} color={theme.muted} />
              <Text style={[styles.pinCardHintText, { color: theme.muted }]}>
                You can enable this anytime in Settings → PIN Protection or Block Enforcement.
              </Text>
            </View>
          )}
        </View>

         {/* Activation summary — makes the Iron Mode setup feel like an activation */}
         {(allRequiredReady || allOptionalGranted) && (
           <View style={[styles.activationCard, {
             backgroundColor: allOptionalGranted ? COLORS.orange + '14' : theme.card,
             borderColor: allOptionalGranted ? COLORS.orange + '55' : theme.border,
           }]}>
             <Text style={[styles.activationTitle, { color: allOptionalGranted ? COLORS.orange : theme.text }]}>
               {allOptionalGranted ? 'Iron Mode ready' : 'Layers active so far'}
             </Text>
             {[
               { label: 'App detection', ids: ['usage', 'accessibility'] },
               { label: 'Background survival', ids: ['notifications', 'battery', 'overlay'] },
               { label: 'Network restriction', ids: ['vpn'] },
               { label: 'Force-stop resistance', ids: ['device_admin'] },
               { label: 'Settings PIN', ids: [] as string[], extra: defensePinSet },
             ].map(({ label, ids, extra }) => {
               const active = ids.length > 0
                 ? ids.every((id) => statuses[id] === 'granted')
                 : (extra ?? false);
               return (
                 <View key={label} style={styles.activationRow}>
                   <Ionicons
                     name={active ? 'checkmark-circle' : 'ellipse-outline'}
                     size={14}
                     color={active ? COLORS.green : theme.muted}
                   />
                   <Text style={[styles.activationRowText, { color: active ? theme.text : theme.muted }]}>
                     {label}
                   </Text>
                 </View>
               );
             })}
           </View>
         )}
          </>
        )}

        {/* Continue buttons — only the required core permissions count
            toward readiness; the button remains available so users can return
            to Settings later if Android setup is interrupted. */}
        <TouchableOpacity
          style={[styles.enterBtn, step === 'core' && allRequiredReady && styles.enterBtnReady]}
          onPress={() => {
             if (step === 'core') {
               if (protectionMode === 'iron') {
                 setStep('optional');
               } else {
                 void handleFinish();
               }
            } else {
              void handleFinish();
            }
          }}
          activeOpacity={0.85}
        >
          <Text style={styles.enterBtnText}>
            {step === 'core'
              ? protectionMode === 'iron' ? 'Continue Iron Mode setup →' : 'Enter FocusFlow →'
              : 'Activate Iron Mode & enter'}
          </Text>
        </TouchableOpacity>

        <Text style={[styles.footerNote, { color: theme.muted }]}>
          {step === 'core'
            ? protectionMode === 'iron'
              ? 'You can finish Iron Mode later from Settings → Permissions.'
              : 'You can switch to Iron Mode later from Settings → Permissions.'
            : 'Optional features can be enabled later from Settings.'}
        </Text>
      </ScrollView>

      <PinSetupModal
        visible={pinSetupVisible}
        pinType="defense"
        onSaved={() => {
          setPinSetupVisible(false);
          setDefensePinSet(true);
        }}
        onCancel={() => setPinSetupVisible(false)}
      />
    </SafeAreaView>
  );
}

function statusColor(status: PermStatus, requiredToContinue = false): string {
  if (status === 'granted') return COLORS.green;
  if (status === 'denied') return requiredToContinue ? COLORS.red : COLORS.orange;
  return COLORS.muted;
}

function StatusBadge({
  status,
  requiredToContinue = false,
}: {
  status: PermStatus;
  requiredToContinue?: boolean;
}) {
  const label =
    status === 'granted' ? 'Ready' : status === 'denied'
      ? requiredToContinue ? 'Missing' : 'Not set up'
      : 'Checking…';
  const color = statusColor(status, requiredToContinue);
  return (
    <View style={[badge.wrap, { backgroundColor: color + '22', borderColor: color + '44' }]}>
      <Text style={[badge.text, { color }]}>{label}</Text>
    </View>
  );
}

const badge = StyleSheet.create({
  wrap: {
    paddingHorizontal: SPACING.sm,
    paddingVertical: 2,
    borderRadius: RADIUS.full,
    borderWidth: 1,
  },
  text: { fontSize: FONT.xs, fontWeight: '700' },
});

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: COLORS.background },
  scroll: { padding: SPACING.lg, paddingBottom: 56, gap: SPACING.md },

  // Header
  header: {
    alignItems: 'center',
    paddingVertical: SPACING.xl,
    gap: SPACING.sm,
  },
  logoCircle: {
    width: 84,
    height: 84,
    borderRadius: 42,
    backgroundColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 6,
  },
  appName: {
    fontSize: FONT.xxl + 4,
    fontWeight: '900',
    color: COLORS.text,
    letterSpacing: -1,
  },
  stepTitle: {
    fontSize: FONT.lg,
    fontWeight: '800',
    color: COLORS.text,
    textAlign: 'center',
  },
  tagline: { fontSize: FONT.sm, color: COLORS.muted, textAlign: 'center' },

  // Tutorial banner
  modeCard: {
    borderRadius: RADIUS.lg,
    borderWidth: StyleSheet.hairlineWidth,
    padding: SPACING.md,
    gap: SPACING.sm,
  },
  modeTitle: { fontSize: FONT.md, fontWeight: '800' },
  modeSubtitle: { fontSize: FONT.xs, lineHeight: 17 },
  modeOptions: { gap: SPACING.sm },
  modeOption: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: SPACING.sm,
    padding: SPACING.sm,
    borderRadius: RADIUS.md,
    borderWidth: 1.5,
  },
  modeOptionBody: { flex: 1, gap: 3 },
  modeOptionTitleRow: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, flexWrap: 'wrap' },
  modeOptionTitle: { fontSize: FONT.sm, fontWeight: '800' },
  modeOptionText: { fontSize: FONT.xs, lineHeight: 17 },
  ironBadge: {
    backgroundColor: COLORS.orange + '22',
    borderColor: COLORS.orange + '55',
    borderWidth: 1,
    borderRadius: RADIUS.full,
    paddingHorizontal: SPACING.xs,
    paddingVertical: 2,
  },
  ironBadgeText: { color: COLORS.orange, fontSize: 9, fontWeight: '800', letterSpacing: 0.5 },
  ironPromo: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: SPACING.sm,
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    padding: SPACING.md,
  },
  ironPromoBody: { flex: 1, gap: 4 },
  ironPromoTitle: { fontSize: FONT.sm, fontWeight: '800' },
  ironPromoText: { fontSize: FONT.xs, lineHeight: 17 },
  tutorialBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: COLORS.primaryLight,
    borderRadius: RADIUS.lg,
    padding: SPACING.md,
    gap: SPACING.md,
    borderWidth: 1,
    borderColor: COLORS.primary + '33',
  },
  tutorialIconWrap: {
    width: 44,
    height: 44,
    borderRadius: RADIUS.md,
    backgroundColor: COLORS.primary + '18',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  tutorialTextWrap: { flex: 1, gap: 4 },
  tutorialTitle: {
    fontSize: FONT.sm,
    fontWeight: '800',
    color: COLORS.primary,
  },
  tutorialBody: {
    fontSize: FONT.xs,
    color: COLORS.primary + 'cc',
    lineHeight: 17,
  },

  // Progress
  progressSection: { gap: SPACING.xs },
  progressLabelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  progressLabel: { fontSize: FONT.xs, color: COLORS.muted, fontWeight: '600' },
  progressCount: { fontSize: FONT.xs, fontWeight: '800', color: COLORS.primary },
  progressCountDone: { color: COLORS.green },
  progressBar: {
    height: 6,
    backgroundColor: COLORS.border,
    borderRadius: RADIUS.full,
    overflow: 'hidden',
  },
  progressFill: { height: 6, borderRadius: RADIUS.full },
  allSetText: {
    fontSize: FONT.xs,
    color: COLORS.green,
    fontWeight: '600',
    textAlign: 'center',
    marginTop: 2,
  },

  sectionLabel: {
    fontSize: FONT.xs,
    fontWeight: '700',
    color: COLORS.muted,
    letterSpacing: 1,
  },
  layerHeader: {
    gap: 3,
    marginBottom: SPACING.xs,
    marginTop: SPACING.sm,
  },
  layerHeaderLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.xs,
  },
  layerLabel: {
    fontSize: FONT.xs,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  layerSubtitle: {
    fontSize: FONT.xs,
    lineHeight: 16,
  },

  optionalHint: {
    fontSize: FONT.sm,
    lineHeight: 18,
    marginTop: -SPACING.xs,
    marginBottom: SPACING.xs,
  },
  backToCore: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    gap: 2,
    paddingVertical: SPACING.xs,
  },
  backToCoreText: {
    fontSize: FONT.sm,
    fontWeight: '700',
    color: COLORS.primary,
  },

  // Cards
  card: {
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.lg,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: COLORS.border,
    overflow: 'hidden',
  },
  cardGranted: { borderColor: COLORS.green + '55' },
  cardMain: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    padding: SPACING.md,
    gap: SPACING.md,
  },
  iconWrap: {
    width: 44,
    height: 44,
    borderRadius: RADIUS.md,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    marginTop: 2,
  },
  cardBody: { flex: 1, gap: 4 },
  cardTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
    flexWrap: 'wrap',
  },
  cardTitle: { fontSize: FONT.md, fontWeight: '700', color: COLORS.text },
  cardDesc: { fontSize: FONT.xs, color: COLORS.muted, lineHeight: 17 },

  // Expanded
  expandedSection: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: COLORS.border,
    padding: SPACING.md,
    gap: SPACING.sm,
    backgroundColor: COLORS.surface,
  },
  whyBox: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 6,
    backgroundColor: COLORS.orangeLight,
    borderRadius: RADIUS.md,
    padding: SPACING.sm,
  },
  whyText: {
    flex: 1,
    fontSize: FONT.xs,
    color: COLORS.orange,
    lineHeight: 17,
    fontWeight: '600',
  },
  brokenSection: { gap: 6 },
  brokenTitle: {
    fontSize: FONT.xs,
    fontWeight: '700',
    color: COLORS.text,
    marginBottom: 2,
  },
  brokenRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 6 },
  brokenText: {
    flex: 1,
    fontSize: FONT.xs,
    color: COLORS.textSecondary,
    lineHeight: 17,
  },
  grantBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    backgroundColor: COLORS.primary,
    borderRadius: RADIUS.md,
    padding: SPACING.sm,
  },
  grantBtnText: { fontSize: FONT.xs, fontWeight: '700', color: '#fff' },

  // Manage permissions tip
  manageTip: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: SPACING.sm,
    backgroundColor: COLORS.primaryLight,
    borderRadius: RADIUS.lg,
    padding: SPACING.md,
    borderWidth: 1,
    borderColor: COLORS.primary + '33',
  },
  manageTipText: {
    flex: 1,
    fontSize: FONT.xs,
    color: COLORS.textSecondary,
    lineHeight: 17,
  },
  manageTipHighlight: {
    color: COLORS.primary,
    fontWeight: '700',
  },

  // PIN Protection card
  pinCard: {
    borderRadius: RADIUS.lg,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  pinCardMain: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: SPACING.md,
    gap: SPACING.md,
  },
  pinCardIcon: {
    width: 44,
    height: 44,
    borderRadius: RADIUS.md,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  pinCardBody: { flex: 1, gap: 3 },
  pinCardTitle: { fontSize: FONT.md, fontWeight: '700' },
  pinCardDesc: { fontSize: FONT.xs, lineHeight: 17 },
  pinCardHint: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: SPACING.xs,
    padding: SPACING.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  pinCardHintText: { flex: 1, fontSize: FONT.xs, lineHeight: 16 },
  setPinBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.xs,
    backgroundColor: COLORS.primary,
    borderRadius: RADIUS.md,
    paddingVertical: SPACING.xs,
    paddingHorizontal: SPACING.sm,
    alignSelf: 'flex-start',
    marginTop: 2,
  },
  setPinBtnText: { fontSize: FONT.xs, fontWeight: '700', color: '#fff' },

  // Iron Mode activation summary
  activationCard: {
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    padding: SPACING.md,
    gap: SPACING.sm,
  },
  activationTitle: {
    fontSize: FONT.sm,
    fontWeight: '800',
    marginBottom: SPACING.xs,
  },
  activationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
  },
  activationRowText: {
    fontSize: FONT.xs,
    fontWeight: '600',
  },

  // Enter button
  enterBtn: {
    backgroundColor: COLORS.primaryLight,
    borderRadius: RADIUS.lg,
    padding: SPACING.md,
    alignItems: 'center',
    marginTop: SPACING.xs,
  },
  enterBtnReady: { backgroundColor: COLORS.primary },
  enterBtnText: { fontSize: FONT.md, fontWeight: '800', color: '#fff' },

  footerNote: {
    fontSize: FONT.xs,
    color: COLORS.muted,
    textAlign: 'center',
  },
});
