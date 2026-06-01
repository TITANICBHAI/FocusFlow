import React, { useRef, useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  ScrollView,
  Platform,
  Linking,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { useApp } from '@/context/AppContext';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

const PRIVACY_URL = 'https://focusflowapp.pages.dev/privacy-policy/';
const TERMS_URL   = 'https://focusflowapp.pages.dev/terms-of-service/';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const SLIDES: { icon: IoniconName; label: string; title: string; body: string }[] = [
  {
    icon: 'time-outline',
    label: 'Why FocusFlow?',
    title: 'Open a social app.\nAn hour disappears.',
    body: 'Social media, short videos, notifications — they are engineered to keep you hooked. FocusFlow enforces the blocks so you do not have to rely on willpower.',
  },
  {
    icon: 'shield-checkmark-outline',
    label: 'How it works',
    title: 'Real blocking needs\ndeep Android access.',
    body: 'Most apps use timers you can dismiss. FocusFlow uses Android\'s Accessibility Service and local VPN — the same method as every serious app blocker — so blocks cannot be tapped away. You will be asked to grant 3 permissions on the next screen.',
  },
  {
    icon: 'bar-chart-outline',
    label: 'What you get',
    title: 'Focus sessions, streaks,\nand full visibility.',
    body: 'Schedule focus sessions, block any app instantly, and track your progress week over week. Everything stays on your device — nothing is sent to any server.',
  },
];

export default function WelcomeScreen() {
  const { state, updateSettings } = useApp();
  const scrollRef = useRef<ScrollView>(null);
  const [activeIndex, setActiveIndex] = useState(0);

  const goToSlide = (index: number) => {
    scrollRef.current?.scrollTo({ x: index * SCREEN_WIDTH, animated: true });
    setActiveIndex(index);
  };

  const handleScroll = (e: any) => {
    const index = Math.round(e.nativeEvent.contentOffset.x / SCREEN_WIDTH);
    if (index !== activeIndex) setActiveIndex(index);
  };

  const acceptAndProceed = async () => {
    try {
      const updated = { ...state.settings, privacyAccepted: true };
      await updateSettings(updated);
      try {
        await SharedPrefsModule.putString('privacy_accepted', 'true');
      } catch {
        // Non-fatal — DB save is the primary path
      }
    } catch {
      // Non-fatal — proceed anyway
    }
    router.replace('/onboarding');
  };

  const handleNext = () => {
    if (activeIndex < SLIDES.length - 1) {
      goToSlide(activeIndex + 1);
    } else {
      void acceptAndProceed();
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        ref={scrollRef}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        scrollEventThrottle={16}
        onMomentumScrollEnd={handleScroll}
        style={styles.pager}
      >
        {SLIDES.map((s, i) => (
          <View key={i} style={[styles.slide, { width: SCREEN_WIDTH }]}>
            <View style={styles.iconWrap}>
              <Ionicons name={s.icon} size={44} color={COLORS.primary} />
            </View>
            <Text style={styles.slideLabel}>{s.label}</Text>
            <Text style={styles.slideTitle}>{s.title}</Text>
            <Text style={styles.slideBody}>{s.body}</Text>
          </View>
        ))}
      </ScrollView>

      <View style={styles.footer}>
        <View style={styles.dots}>
          {SLIDES.map((_, i) => (
            <TouchableOpacity key={i} onPress={() => goToSlide(i)} hitSlop={10}>
              <View style={[styles.dot, i === activeIndex ? styles.dotActive : styles.dotInactive]} />
            </TouchableOpacity>
          ))}
        </View>

        <TouchableOpacity style={styles.primaryBtn} onPress={handleNext} activeOpacity={0.85}>
          <Text style={styles.primaryBtnText}>
            {activeIndex < SLIDES.length - 1 ? 'Next' : 'Get started'}
          </Text>
        </TouchableOpacity>

        {activeIndex < SLIDES.length - 1 && (
          <TouchableOpacity onPress={() => void acceptAndProceed()} hitSlop={12}>
            <Text style={styles.skipText}>Skip</Text>
          </TouchableOpacity>
        )}

        <Text style={styles.legalText}>
          {'By continuing you agree to our '}
          <Text style={styles.legalLink} onPress={() => Linking.openURL(PRIVACY_URL)}>
            Privacy Policy
          </Text>
          {' and '}
          <Text style={styles.legalLink} onPress={() => Linking.openURL(TERMS_URL)}>
            Terms of Service
          </Text>
          .
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: COLORS.card,
  },
  pager: {
    flex: 1,
  },
  slide: {
    flex: 1,
    paddingHorizontal: SPACING.xl ?? 32,
    justifyContent: 'center',
    gap: SPACING.sm ?? 10,
    backgroundColor: COLORS.card,
  },
  iconWrap: {
    width: 72,
    height: 72,
    borderRadius: RADIUS.lg ?? 16,
    backgroundColor: COLORS.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: SPACING.sm ?? 10,
  },
  slideLabel: {
    fontSize: FONT.xs ?? 11,
    fontWeight: '700',
    color: COLORS.primary,
    letterSpacing: 1.1,
    textTransform: 'uppercase',
  },
  slideTitle: {
    fontSize: FONT.xxl ?? 26,
    fontWeight: '900',
    color: COLORS.text,
    lineHeight: 34,
    letterSpacing: -0.4,
  },
  slideBody: {
    fontSize: FONT.md ?? 15,
    color: COLORS.textSecondary,
    lineHeight: 24,
    maxWidth: 310,
  },
  footer: {
    paddingHorizontal: SPACING.lg ?? 24,
    paddingBottom: Platform.OS === 'android' ? 24 : 12,
    paddingTop: SPACING.md ?? 16,
    gap: SPACING.sm ?? 12,
    alignItems: 'center',
    backgroundColor: COLORS.card,
    borderTopWidth: 1,
    borderTopColor: COLORS.border,
  },
  dots: {
    flexDirection: 'row',
    gap: 6,
    alignItems: 'center',
  },
  dot: {
    height: 7,
    borderRadius: 4,
  },
  dotActive: {
    width: 22,
    backgroundColor: COLORS.primary,
  },
  dotInactive: {
    width: 7,
    backgroundColor: COLORS.border,
  },
  primaryBtn: {
    width: '100%',
    paddingVertical: 15,
    borderRadius: RADIUS.lg ?? 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: COLORS.primary,
  },
  primaryBtnText: {
    color: '#fff',
    fontSize: FONT.md ?? 15,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  skipText: {
    fontSize: FONT.sm ?? 13,
    fontWeight: '500',
    color: COLORS.muted,
    paddingVertical: 2,
  },
  legalText: {
    fontSize: 11,
    textAlign: 'center',
    color: COLORS.muted,
    lineHeight: 17,
    paddingHorizontal: SPACING.md ?? 16,
  },
  legalLink: {
    color: COLORS.primary,
    fontWeight: '600',
  },
});
