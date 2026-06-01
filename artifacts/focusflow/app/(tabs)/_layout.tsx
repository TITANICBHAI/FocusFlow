import { BlurView } from "expo-blur";
import { Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import React, { useState, useEffect, useCallback } from "react";
import { Platform, StyleSheet, View, AppState } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { COLORS } from "@/styles/theme";
import { useTheme } from "@/hooks/useTheme";
import DarkModeToggle from "@/components/DarkModeToggle";
import { SideMenu, SideMenuToggle, SideMenuGuideTip } from "@/components/SideMenu";
import { useApp } from "@/context/AppContext";
import { getBlockingPermStatus } from "@/services/permissionGuard";

const SIDE_MENU_TIP_KEY = "@focusflow/sideMenuTipSeen";

export default function TabLayout() {
  const isIOS = Platform.OS === "ios";
  const isWeb = Platform.OS === "web";
  const insets = useSafeAreaInsets();
  const { theme, isDark } = useTheme();
  const { state } = useApp();

  const [menuOpen, setMenuOpen] = useState(false);
  const [showGuideTip, setShowGuideTip] = useState(false);
  const [showPermBadge, setShowPermBadge] = useState(false);

  // Check permission status for the Settings tab badge dot.
  // Re-runs every time the app comes to foreground so the dot disappears
  // the moment the user grants the last missing permission.
  useEffect(() => {
    if (!state.settings.onboardingComplete) return;
    const check = async () => {
      const status = await getBlockingPermStatus();
      setShowPermBadge(!status.overlay || !status.usage || !status.accessibility);
    };
    void check();
    const sub = AppState.addEventListener("change", (s) => {
      if (s === "active") void check();
    });
    return () => sub.remove();
  }, [state.settings.onboardingComplete]);

  // Show guide tip once after onboarding completes
  useEffect(() => {
    if (!state.isDbReady || !state.settings.onboardingComplete) return;
    void AsyncStorage.getItem(SIDE_MENU_TIP_KEY).then((seen) => {
      if (!seen) {
        // Small delay so the user sees the main screen first
        const t = setTimeout(() => setShowGuideTip(true), 1800);
        return () => clearTimeout(t);
      }
    });
  }, [state.isDbReady, state.settings.onboardingComplete]);

  const dismissGuideTip = useCallback(async () => {
    setShowGuideTip(false);
    await AsyncStorage.setItem(SIDE_MENU_TIP_KEY, "1");
  }, []);

  const openMenu = useCallback(() => {
    setMenuOpen(true);
    // Dismiss the tip if still showing
    if (showGuideTip) void dismissGuideTip();
  }, [showGuideTip, dismissGuideTip]);

  const closeMenu = useCallback(() => setMenuOpen(false), []);

  const tabBarH = isWeb ? 84 : 60 + insets.bottom;

  return (
    <View style={{ flex: 1 }}>
      <Tabs
        screenOptions={{
          tabBarActiveTintColor: COLORS.primary,
          tabBarInactiveTintColor: theme.muted,
          headerShown: false,
          tabBarStyle: {
            position: "absolute",
            backgroundColor: isDark
              ? theme.tabBar
              : isIOS
              ? "transparent"
              : theme.tabBar,
            borderTopWidth: isWeb || isDark ? 1 : 0,
            borderTopColor: theme.tabBarBorder,
            elevation: 8,
            height: tabBarH,
            paddingBottom: isWeb ? 34 : insets.bottom + 6,
            paddingTop: 8,
          },
          tabBarLabelStyle: {
            fontSize: 11,
            fontWeight: "600",
            color: theme.textSecondary,
          },
          tabBarBackground: () =>
            isIOS && !isDark ? (
              <BlurView
                intensity={100}
                tint="light"
                style={StyleSheet.absoluteFill}
              />
            ) : (
              <View
                style={[
                  StyleSheet.absoluteFill,
                  { backgroundColor: theme.tabBar },
                ]}
              />
            ),
        }}
      >
        <Tabs.Screen
          name="index"
          options={{
            title: "Schedule",
            tabBarIcon: ({ color, focused }) => (
              <Ionicons
                name={focused ? "calendar" : "calendar-outline"}
                size={22}
                color={color}
              />
            ),
          }}
        />
        <Tabs.Screen
          name="focus"
          options={{
            title: "Focus",
            tabBarIcon: ({ color, focused }) => (
              <Ionicons
                name={
                  focused ? "shield-checkmark" : "shield-checkmark-outline"
                }
                size={22}
                color={color}
              />
            ),
          }}
        />
        <Tabs.Screen
          name="stats"
          options={{
            title: "Stats",
            tabBarIcon: ({ color, focused }) => (
              <Ionicons
                name={focused ? "bar-chart" : "bar-chart-outline"}
                size={22}
                color={color}
              />
            ),
          }}
        />
        <Tabs.Screen
          name="settings"
          options={{
            title: "Settings",
            tabBarIcon: ({ color, focused }) => (
              <View>
                <Ionicons
                  name={focused ? "settings" : "settings-outline"}
                  size={22}
                  color={color}
                />
                {showPermBadge && <View style={styles.permBadge} />}
              </View>
            ),
          }}
        />
      </Tabs>

      {/* Floating dark mode toggle — top-right corner, above all tab content */}
      <View
        style={[
          styles.toggleContainer,
          { top: insets.top + 10, right: 14 },
        ]}
        pointerEvents="box-none"
      >
        <DarkModeToggle />
      </View>

      {/* Side menu toggle — "›" tab above bottom nav bar */}
      <SideMenuToggle
        onPress={menuOpen ? closeMenu : openMenu}
        isOpen={menuOpen}
        tabBarHeight={tabBarH}
      />

      {/* Guide tip — one-time hint pointing to the side menu button */}
      <SideMenuGuideTip
        visible={showGuideTip}
        onDismiss={dismissGuideTip}
        tabBarHeight={tabBarH}
      />

      {/* Side menu panel + backdrop */}
      <SideMenu
        visible={menuOpen}
        onOpen={openMenu}
        onClose={closeMenu}
        tabBarHeight={tabBarH}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  toggleContainer: {
    position: "absolute",
    zIndex: 999,
  },
  permBadge: {
    position: "absolute",
    top: -1,
    right: -3,
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: COLORS.red ?? "#ef4444",
  },
});
