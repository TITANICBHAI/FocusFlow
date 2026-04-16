/**
 * app/_layout.tsx
 *
 * expo-router entry layout. Reproduces the module-level setup from App.tsx:
 *   1. Background task definitions
 *   2. Notification foreground handler
 *   3. EventBridge initialisation
 *   4. Splash screen keep-alive
 *   5. Notification response handler
 *   6. Notification action categories
 *   7. React component tree (providers + expo-router Stack)
 */

// ─── 1. Register all background tasks with the OS ────────────────────────────
import '@/tasks/backgroundTasks';

import React, { useCallback, useEffect, useRef } from 'react';
import { Stack, router, usePathname } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as SplashScreen from 'expo-splash-screen';
import * as Notifications from 'expo-notifications';
import { StyleSheet, View, Text, Animated, Platform } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { COLORS, FONT, SPACING } from '@/styles/theme';

import { AppProvider, useApp } from '@/context/AppContext';
import { useTheme } from '@/hooks/useTheme';
import { EventBridge } from '@/services/eventBridge';
import { navigateToTask, consumePendingTaskNavigation } from '@/navigation/navigationRef';
import { registerBackgroundFetch, registerOverrunCheckTask } from '@/tasks/backgroundTasks';
import { dismissPersistentNotification } from '@/services/notificationService';
import { BlockedAppOverlay } from '@/components/BlockedAppOverlay';

// ─── 2. Foreground notification display behaviour ─────────────────────────────
Notifications.setNotificationHandler({
  handleNotification: async (notification) => {
    const data = notification.request.content.data as { type?: string };
    // Suppress the internal persistent-dismiss bookkeeping notification silently.
    // The focus persistent notification is now owned by the native ForegroundTaskService
    // and never goes through Expo, so no suppression is needed for it here.
    if (data?.type === 'persistent-dismiss') {
      return { shouldShowBanner: false, shouldShowList: false, shouldPlaySound: false, shouldSetBadge: false };
    }
    return { shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false };
  },
});

// ─── 3. Connect native event channel ─────────────────────────────────────────
EventBridge.init();

// ─── 4. Keep splash visible until app context is ready ───────────────────────
SplashScreen.preventAutoHideAsync();

// ─── 5. Notification response handler (tap or action button) ─────────────────
Notifications.addNotificationResponseReceivedListener((response) => {
  const data = response.notification.request.content.data as {
    taskId?: string;
    type?: string;
  };
  const actionId = response.actionIdentifier;

  if (!data?.taskId) return;

  if (
    actionId === Notifications.DEFAULT_ACTION_IDENTIFIER ||
    actionId === 'VIEW'
  ) {
    navigateToTask(data.taskId);
  }
});

// ─── 6. Foreground notification received listener ────────────────────────────
Notifications.addNotificationReceivedListener(async (notification) => {
  const data = notification.request.content.data as {
    taskId?: string;
    type?: string;
  };
  if (data?.type === 'LATE_START_WARNING' && data.taskId) {
    // Handled by ScheduleScreen polling
  }
  if (data?.type === 'persistent-dismiss') {
    // Auto-dismiss the persistent notification when the task ends
    try {
      await dismissPersistentNotification();
    } catch {
      // ignore
    }
  }
});

// ─── 7. Notification action categories ───────────────────────────────────────
async function setupNotificationCategories() {
  if (Platform.OS === 'web' || typeof Notifications.setNotificationCategoryAsync !== 'function') return;
  await Notifications.setNotificationCategoryAsync('task-active', [
    {
      identifier: 'COMPLETE',
      buttonTitle: '✅ Complete',
      options: { opensAppToForeground: false },
    },
    {
      identifier: 'EXTEND',
      buttonTitle: '⏱ +15 min',
      options: { opensAppToForeground: false },
    },
    {
      identifier: 'VIEW',
      buttonTitle: '👁 View',
      options: { opensAppToForeground: true },
    },
  ]);

  await Notifications.setNotificationCategoryAsync('task-reminder', [
    {
      identifier: 'VIEW',
      buttonTitle: '👁 Open',
      options: { opensAppToForeground: true },
    },
    {
      identifier: 'COMPLETE',
      buttonTitle: '✅ Done',
      options: { opensAppToForeground: false },
    },
  ]);
}

// ─── Animated in-app splash overlay ──────────────────────────────────────────
// Shows a branded loading screen while the SQLite DB is initialising.
// Fades out the moment isDbReady becomes true so there is no blank flash.

function AppSplashOverlay() {
  const { state } = useApp();
  const opacity = useRef(new Animated.Value(1)).current;
  const pulse = useRef(new Animated.Value(1)).current;
  const [visible, setVisible] = React.useState(true);
  const dismissedRef = useRef(false);

  const dismiss = useCallback(() => {
    if (dismissedRef.current) return;
    dismissedRef.current = true;
    if (Platform.OS === 'web') {
      setVisible(false);
      return;
    }
    Animated.timing(opacity, {
      toValue: 0,
      duration: 350,
      useNativeDriver: true,
    }).start();
    // Always hide after the animation duration — never rely solely on .start() callback
    setTimeout(() => setVisible(false), 400);
  }, [opacity]);

  // Pulsing logo animation while loading
  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 1.12, duration: 850, useNativeDriver: true }),
        Animated.timing(pulse, { toValue: 1, duration: 850, useNativeDriver: true }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [pulse]);

  // Dismiss when DB is ready or loading finishes
  useEffect(() => {
    if (state.isDbReady || !state.isLoading) {
      dismiss();
    }
  }, [state.isDbReady, state.isLoading, dismiss]);

  // Hard failsafe: dismiss after 4 s no matter what
  useEffect(() => {
    const t = setTimeout(dismiss, 4_000);
    return () => clearTimeout(t);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!visible) return null;

  return (
    <Animated.View style={[splashStyles.overlay, { opacity }]} pointerEvents="none">
      <Animated.View style={{ transform: [{ scale: pulse }] }}>
        <View style={splashStyles.logoCircle}>
          <Ionicons name="shield-checkmark" size={52} color="#fff" />
        </View>
      </Animated.View>
      <View style={{ alignItems: 'center', gap: 6 }}>
        <Text style={splashStyles.name}>FocusFlow</Text>
        <Text style={splashStyles.tagline}>Your discipline operating system</Text>
      </View>
    </Animated.View>
  );
}

const splashStyles = StyleSheet.create({
  overlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.md,
    zIndex: 999,
  },
  logoCircle: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: 'rgba(255,255,255,0.18)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: SPACING.xs,
  },
  name: {
    fontSize: FONT.xxl + 6,
    fontWeight: '900',
    color: '#fff',
    letterSpacing: -1,
  },
  tagline: {
    fontSize: FONT.sm,
    color: 'rgba(255,255,255,0.7)',
    letterSpacing: 0.3,
  },
});

// ─── Themed status bar ───────────────────────────────────────────────────────
function ThemedStatusBar() {
  const { isDark } = useTheme();
  return <StatusBar style={isDark ? 'light' : 'dark'} />;
}

// ─── Onboarding guard ─────────────────────────────────────────────────────────
// Runs inside AppProvider so it has access to context.
// Once the DB is ready, redirects to /onboarding on first install.
// On every subsequent open onboardingComplete is true so nothing happens.

function OnboardingGuard() {
  const { state } = useApp();
  const pathname = usePathname();

  useEffect(() => {
    if (!state.isDbReady) return;
    if (!state.settings.privacyAccepted) {
      if (pathname !== '/privacy-policy') router.replace('/privacy-policy');
      return;
    }
    if (!state.settings.onboardingComplete) {
      if (pathname !== '/onboarding') router.replace('/onboarding');
    }
  }, [pathname, state.isDbReady, state.settings.onboardingComplete, state.settings.privacyAccepted]);

  return null;
}

// ─── React component ──────────────────────────────────────────────────────────

export default function RootLayout() {
  useEffect(() => {
    async function bootstrap() {
      try {
        await SplashScreen.hideAsync().catch(() => {});
        await setupNotificationCategories();
        await registerBackgroundFetch();
        await registerOverrunCheckTask();
        consumePendingTaskNavigation();

        try {
          const { ForegroundServiceModule } = await import('@/native-modules/ForegroundServiceModule');
          await ForegroundServiceModule.requestBatteryOptimizationExemption();
        } catch {
          // Native module not yet linked (dev build without EAS)
        }
      } finally {
        setTimeout(() => SplashScreen.hideAsync().catch(() => {}), 400);
      }
    }

    bootstrap();
  }, []);

  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <AppProvider>
          <AppSplashOverlay />
          <OnboardingGuard />
          <BlockedAppOverlay />
          <Stack screenOptions={{ headerShown: false }}>
            <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
            <Stack.Screen name="privacy-policy" options={{ headerShown: false, presentation: 'fullScreenModal' }} />
            <Stack.Screen name="onboarding" options={{ headerShown: false, presentation: 'fullScreenModal' }} />
            <Stack.Screen name="permissions" options={{ headerShown: false }} />
            <Stack.Screen name="+not-found" />
          </Stack>
          <ThemedStatusBar />
        </AppProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
});
