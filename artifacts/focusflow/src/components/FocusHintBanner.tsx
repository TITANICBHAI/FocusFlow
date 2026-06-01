/**
 * FocusHintBanner — sequential first-run hints for the Focus tab.
 *
 * Usage:
 *   <FocusHintBanner hintKey="task"        step={hintStep} onDismiss={advanceHint} />
 *   <FocusHintBanner hintKey="alwaysOn"    step={hintStep} onDismiss={advanceHint} />
 *   <FocusHintBanner hintKey="standalone"  step={hintStep} onDismiss={advanceHint} />
 *
 * Each banner only renders when `step` matches its own key index.
 * Tapping "Got it" calls onDismiss, which advances the parent's step counter.
 */

import React, { useEffect, useRef } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Animated } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { useTheme } from '@/hooks/useTheme';

type HintKey = 'task' | 'alwaysOn' | 'standalone';
type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const HINT_INDEX: Record<HintKey, number> = {
  task:       0,
  alwaysOn:   1,
  standalone: 2,
};

interface HintConfig {
  icon: IoniconName;
  iconColor: string;
  iconBg: string;
  title: string;
  body: string;
  step: string;
}

const HINT_CONFIG: Record<HintKey, HintConfig> = {
  task: {
    icon:      'calendar-outline',
    iconColor: COLORS.primary,
    iconBg:    COLORS.primaryLight,
    title:     'Start here — create your first task',
    body:      'A task is what you want to get done. Add one, then come back to start a focus session with blocking enforced.',
    step:      '1 of 3',
  },
  alwaysOn: {
    icon:      'shield-checkmark-outline',
    iconColor: COLORS.orange,
    iconBg:    COLORS.orangeLight,
    title:     'Block apps 24/7 — no session needed',
    body:      'Always-On Enforcement blocks your chosen apps around the clock, even when you are not in a focus session. Tap "App List" to add the apps you want blocked permanently.',
    step:      '2 of 3',
  },
  standalone: {
    icon:      'ban-outline',
    iconColor: COLORS.red,
    iconBg:    COLORS.redLight,
    title:     'Block apps right now — no task required',
    body:      'Tap here to block distracting apps for a set time without creating a task first. Useful for a quick focus burst.',
    step:      '3 of 3',
  },
};

interface Props {
  hintKey: HintKey;
  step: number;
  onDismiss: () => void;
}

export function FocusHintBanner({ hintKey, step, onDismiss }: Props) {
  const { theme } = useTheme();
  const myIndex = HINT_INDEX[hintKey];
  const config  = HINT_CONFIG[hintKey];

  const fadeAnim  = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(8)).current;

  const isVisible = step === myIndex;

  useEffect(() => {
    if (isVisible) {
      Animated.parallel([
        Animated.timing(fadeAnim,  { toValue: 1, duration: 280, useNativeDriver: true }),
        Animated.timing(slideAnim, { toValue: 0, duration: 280, useNativeDriver: true }),
      ]).start();
    } else {
      fadeAnim.setValue(0);
      slideAnim.setValue(8);
    }
  }, [isVisible]);

  if (!isVisible) return null;

  return (
    <Animated.View style={{ opacity: fadeAnim, transform: [{ translateY: slideAnim }] }}>
      <View style={[styles.banner, { backgroundColor: theme.card, borderColor: config.iconColor + '33' }]}>
        {/* Top row: icon + step counter + dismiss */}
        <View style={styles.topRow}>
          <View style={[styles.iconWrap, { backgroundColor: config.iconBg }]}>
            <Ionicons name={config.icon} size={18} color={config.iconColor} />
          </View>
          <Text style={[styles.stepBadge, { color: config.iconColor }]}>{config.step}</Text>
          <TouchableOpacity onPress={onDismiss} hitSlop={12} activeOpacity={0.7}>
            <Ionicons name="close" size={16} color={theme.muted} />
          </TouchableOpacity>
        </View>

        {/* Content */}
        <Text style={[styles.title, { color: theme.text }]}>{config.title}</Text>
        <Text style={[styles.body, { color: theme.textSecondary }]}>{config.body}</Text>

        {/* Footer dismiss */}
        <TouchableOpacity style={[styles.gotItBtn, { borderColor: config.iconColor + '55' }]} onPress={onDismiss} activeOpacity={0.8}>
          <Text style={[styles.gotItText, { color: config.iconColor }]}>
            {myIndex < 2 ? 'Got it — show next tip →' : 'Got it — done'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Downward arrow pointing to the element below */}
      <View style={[styles.arrow, { borderTopColor: config.iconColor + '33' }]} />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  banner: {
    borderRadius: RADIUS.lg ?? 14,
    borderWidth: 1.5,
    padding: SPACING.md ?? 14,
    gap: SPACING.xs ?? 6,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.xs ?? 6,
    marginBottom: 2,
  },
  iconWrap: {
    width: 32, height: 32, borderRadius: RADIUS.md ?? 10,
    alignItems: 'center', justifyContent: 'center',
  },
  stepBadge: {
    flex: 1,
    fontSize: FONT.xs ?? 11,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  title: {
    fontSize: FONT.md ?? 15,
    fontWeight: '700',
    lineHeight: 21,
  },
  body: {
    fontSize: FONT.sm ?? 13,
    lineHeight: 19,
  },
  gotItBtn: {
    marginTop: SPACING.xs ?? 6,
    paddingVertical: 9,
    borderRadius: RADIUS.md ?? 10,
    borderWidth: 1,
    alignItems: 'center',
  },
  gotItText: {
    fontSize: FONT.sm ?? 13,
    fontWeight: '700',
  },
  arrow: {
    alignSelf: 'center',
    width: 0, height: 0,
    borderLeftWidth: 8,
    borderRightWidth: 8,
    borderTopWidth: 8,
    borderLeftColor: 'transparent',
    borderRightColor: 'transparent',
    marginTop: 0,
  },
});
