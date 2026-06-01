import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

interface Props {
  visible: boolean;
  /** "Not now" — permanently marks the nudge done without navigating.
   *  The user will be prompted for the required permissions anyway the next
   *  time they try to use any blocking feature. */
  onNotNow: () => void;
  /** "Yes, let's go" — navigates to the Permissions screen and permanently marks done */
  onConfirm: () => void;
}

export function PermNudgeBanner({ visible, onNotNow, onConfirm }: Props) {
  if (!visible) return null;

  const handleYes = () => {
    onConfirm();
    router.push('/permissions');
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.iconWrap}>
          <Ionicons name="shield-half-outline" size={16} color={COLORS.orange} />
        </View>
        <Text style={styles.title}>Blocking isn't fully active yet</Text>
      </View>

      <Text style={styles.body}>
        Want to take 2 minutes to finish your setup? Granting the remaining permissions is what makes blocking actually work.
      </Text>

      <View style={styles.actions}>
        <TouchableOpacity style={styles.notNowBtn} onPress={onNotNow} activeOpacity={0.7}>
          <Text style={styles.notNowText}>Not now</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.yesBtn} onPress={handleYes} activeOpacity={0.8}>
          <Text style={styles.yesText}>Yes, let's go</Text>
          <Ionicons name="arrow-forward-outline" size={14} color="#fff" />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
    backgroundColor: COLORS.orange + '10',
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.orange + '35',
    padding: SPACING.md,
    marginBottom: SPACING.md,
    gap: SPACING.sm,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.xs,
  },
  iconWrap: {
    width: 26,
    height: 26,
    borderRadius: RADIUS.sm,
    backgroundColor: COLORS.orange + '20',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    flex: 1,
    fontSize: FONT.sm,
    fontWeight: '700',
    color: COLORS.text,
  },
  body: {
    fontSize: FONT.xs,
    color: COLORS.textSecondary,
    lineHeight: 18,
  },
  actions: {
    flexDirection: 'row',
    gap: SPACING.sm,
    justifyContent: 'flex-end',
    alignItems: 'center',
    paddingTop: 2,
  },
  notNowBtn: {
    paddingVertical: 7,
    paddingHorizontal: SPACING.md,
    borderRadius: RADIUS.sm,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  notNowText: {
    fontSize: FONT.sm,
    fontWeight: '500',
    color: COLORS.textSecondary,
  },
  yesBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingVertical: 7,
    paddingHorizontal: SPACING.md,
    borderRadius: RADIUS.sm,
    backgroundColor: COLORS.orange,
  },
  yesText: {
    fontSize: FONT.sm,
    fontWeight: '600',
    color: '#fff',
  },
});
