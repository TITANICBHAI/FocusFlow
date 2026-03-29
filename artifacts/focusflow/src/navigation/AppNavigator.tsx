import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { NavigationContainer } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { View, StyleSheet } from 'react-native';
import ScheduleScreen from '@/screens/ScheduleScreen';
import FocusScreen from '@/screens/FocusScreen';
import StatsScreen from '@/screens/StatsScreen';
import SettingsScreen from '@/screens/SettingsScreen';
import OnboardingScreen from '@/screens/OnboardingScreen';
import { COLORS, FONT } from '@/styles/theme';
import { useApp } from '@/context/AppContext';
import { navigationRef } from '@/navigation/navigationRef';

const Tab = createBottomTabNavigator();

export default function AppNavigator() {
  const { activeTask, state } = useApp();
  const isFocusing = state.focusSession?.isActive ?? false;

  // First launch: show permission onboarding until complete
  if (!state.isLoading && !state.settings.onboardingComplete) {
    return (
      <NavigationContainer ref={navigationRef}>
        <OnboardingScreen />
      </NavigationContainer>
    );
  }

  return (
    <NavigationContainer ref={navigationRef}>
      <Tab.Navigator
        screenOptions={({ route }) => ({
          headerShown: false,
          tabBarStyle: styles.tabBar,
          tabBarActiveTintColor: COLORS.primary,
          tabBarInactiveTintColor: COLORS.muted,
          tabBarLabelStyle: styles.tabLabel,
          tabBarIcon: ({ focused, color, size }) => {
            let iconName: keyof typeof Ionicons.glyphMap = 'calendar-outline';

            if (route.name === 'Schedule') {
              iconName = focused ? 'calendar' : 'calendar-outline';
            } else if (route.name === 'Focus') {
              iconName = focused ? 'shield-checkmark' : 'shield-checkmark-outline';
            } else if (route.name === 'Stats') {
              iconName = focused ? 'bar-chart' : 'bar-chart-outline';
            } else if (route.name === 'Settings') {
              iconName = focused ? 'settings' : 'settings-outline';
            }

            return (
              <View style={styles.iconContainer}>
                <Ionicons name={iconName} size={size} color={color} />
                {route.name === 'Focus' && (isFocusing || activeTask) && (
                  <View style={[styles.badge, { backgroundColor: isFocusing ? COLORS.green : COLORS.orange }]} />
                )}
                {route.name === 'Schedule' && activeTask && (
                  <View style={[styles.badge, { backgroundColor: COLORS.primary }]} />
                )}
              </View>
            );
          },
        })}
      >
        <Tab.Screen name="Schedule" component={ScheduleScreen} />
        <Tab.Screen name="Focus" component={FocusScreen} />
        <Tab.Screen name="Stats" component={StatsScreen} />
        <Tab.Screen name="Settings" component={SettingsScreen} />
      </Tab.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    borderTopWidth: 0,
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.06,
    shadowRadius: 12,
    height: 80,
    paddingBottom: 16,
    paddingTop: 8,
    backgroundColor: '#fff',
  },
  tabLabel: {
    fontSize: FONT.xs,
    fontWeight: '600',
  },
  iconContainer: {
    position: 'relative',
  },
  badge: {
    position: 'absolute',
    top: -2,
    right: -4,
    width: 8,
    height: 8,
    borderRadius: 4,
    borderWidth: 1.5,
    borderColor: '#fff',
  },
});
