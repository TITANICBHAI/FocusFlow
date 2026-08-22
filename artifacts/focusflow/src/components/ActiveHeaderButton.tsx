import React from 'react';
import { Ionicons } from '@expo/vector-icons';
import { TouchableOpacity } from 'react-native';
import { router } from 'expo-router';
import { useTheme } from '@/hooks/useTheme';

/**
 * Shared entry point for the live Active dashboard.
 * Kept as a small header action so every bottom-nav screen reaches the same
 * status surface without adding Active as a sixth tab.
 */
export function ActiveHeaderButton() {
  const { theme } = useTheme();

  return (
    <TouchableOpacity
      onPress={() => router.push('/active')}
      accessibilityRole="button"
      accessibilityLabel="Open Active blocks"
      hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
      style={{ padding: 4 }}
    >
      <Ionicons name="pulse-outline" size={22} color={theme.text} />
    </TouchableOpacity>
  );
}