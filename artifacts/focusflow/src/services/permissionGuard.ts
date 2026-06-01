/**
 * permissionGuard.ts
 *
 * Shared utility for the three permissions that are required before any
 * blocking feature can function. Always checked in this order:
 *
 *   1. Overlay (Appear on Top)    — SYSTEM_ALERT_WINDOW
 *   2. Usage Access               — PACKAGE_USAGE_STATS
 *   3. Accessibility Service      — AccessibilityService
 *
 * Import { promptBlockingPermissions } wherever a blocking action is initiated.
 * Import { getBlockingPermStatus }     to read status without alerting.
 */

import { Alert, Linking, Platform } from 'react-native';
import { UsageStatsModule } from '@/native-modules/UsageStatsModule';
import { ForegroundLaunchModule } from '@/native-modules/ForegroundLaunchModule';

export interface BlockingPermStatus {
  overlay: boolean;
  usage: boolean;
  accessibility: boolean;
}

/**
 * Returns the current grant status of all three blocking permissions.
 * Non-Android always returns all-true so callers need no platform guard.
 */
export async function getBlockingPermStatus(): Promise<BlockingPermStatus> {
  if (Platform.OS !== 'android') {
    return { overlay: true, usage: true, accessibility: true };
  }
  const [overlay, usage, accessibility] = await Promise.all([
    ForegroundLaunchModule.hasOverlayPermission().catch(() => false),
    UsageStatsModule.hasPermission().catch(() => false),
    UsageStatsModule.hasAccessibilityPermission().catch(() => false),
  ]);
  return { overlay, usage, accessibility };
}

/**
 * Checks all three permissions in order. If the first missing one is found,
 * shows a targeted Alert explaining that specific permission and offering to
 * open the right settings page. Returns true only when all three are granted.
 *
 * Call this at the top of every blocking action handler. If it returns false,
 * bail out immediately — the user has been shown the correct prompt.
 */
export async function promptBlockingPermissions(): Promise<boolean> {
  if (Platform.OS !== 'android') return true;

  const status = await getBlockingPermStatus();
  if (status.overlay && status.usage && status.accessibility) return true;

  if (!status.overlay) {
    Alert.alert(
      'Step 1 of 3 — Appear on Top',
      '"Appear on Top" lets FocusFlow show the block screen directly over the blocked app with no flash.\n\nTap Open Settings, then enable FocusFlow in the list.',
      [
        { text: 'Not Now', style: 'cancel' },
        {
          text: 'Open Settings',
          onPress: () =>
            ForegroundLaunchModule.requestOverlayPermission().catch(() =>
              Linking.openSettings(),
            ),
        },
      ],
    );
    return false;
  }

  if (!status.usage) {
    Alert.alert(
      'Step 2 of 3 — Usage Access',
      'Usage Access lets FocusFlow detect which app is in the foreground so it can block it the instant you open it.\n\nTap Open Settings, find FocusFlow, and turn it on.',
      [
        { text: 'Not Now', style: 'cancel' },
        {
          text: 'Open Settings',
          onPress: () => UsageStatsModule.openUsageAccessSettings().catch(() => {}),
        },
      ],
    );
    return false;
  }

  // Accessibility — last in chain
  Alert.alert(
    'Step 3 of 3 — Accessibility Service',
    'The Accessibility Service is what makes blocking instant and tamper-proof. Without it, blocked apps open freely.\n\nTap Open Settings, find FocusFlow, and turn the service on.',
    [
      { text: 'Not Now', style: 'cancel' },
      {
        text: 'Open Settings',
        onPress: () => UsageStatsModule.openAccessibilitySettings().catch(() => {}),
      },
    ],
  );
  return false;
}
