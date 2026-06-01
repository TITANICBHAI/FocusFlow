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

interface Slide {
  icon: IoniconName;
  iconColor?: string;
  label: string;
  title: string;
  body: string;
  trustBadge?: string;
}

const SLIDES: Slide[] = [
  {
    icon: 'time-outline',
    label: 'Sound familiar?',
    title: 'Open one app.\nAn hour disappears.',
    body: 'Social media, short videos, and notifications are designed to keep you hooked. FocusFlow enforces the limits so you never have to white-knuckle it.',
  },
  {
    icon: 'shield-checkmark-outline',
    label: 'How it works',
    title: 'Blocks that can\'t\nbe tapped away.',
    body: 'The moment you open a blocked app, FocusFlow covers it and brings you back. No timers you can dismiss. No sneaky workarounds. Just a firm, quiet redirect.',
    trustBadge: 'Your data never leaves your device.',
  },
  {
    icon: 'lock-closed-outline',
    iconColor: '#34C759',
    label: 'One quick note',
    title: 'Android will ask\nabout "Accessibility".',
    body: 'To catch a blocked app the instant it opens, FocusFlow uses Android\'s Accessibility feature.\n\nIt only checks one thing: which app is on screen. It never reads your messages, passwords, or anything inside your apps.',
    trustBadge: 'Same method used by every serious blocker on Android.',
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

  const isLastSlide = activeIndex === SLIDES.length - 1;

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
            <View style={[styles.iconWrap, s.iconColor ? { backgroundColor: s.iconColor + '18' } : null]}>
              <Ionicons name={s.icon} size={44} color={s.iconColor ?? COLORS.primary} />
            </View>
            <Text style={styles.slideLabel}>{s.label}</Text>
            <Text style={styles.slideTitle}>{s.title}</Text>
            <Text style={styles.slideBody}>{s.body}</Text>

            {s.trustBadge ? (
              <View style={styles.trustBadge}>
                <Ionicons name="checkmark-circle" size={14} color={COLORS.green} />
                <Text style={styles.trustBadgeText}>{s.trustBadge}</Text>
              </View>
            ) : null}
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

        <TouchableOpacity style={[styles.primaryBtn, isLastSlide && styles.primaryBtnReady]} onPress={handleNext} activeOpacity={0.85}>
          <Text style={styles.primaryBtnText}>
            {isLastSlide ? 'Got it — set up FocusFlow' : 'Next'}
          </Text>
          {isLastSlide && <Ionicons name="arrow-forward" size={16} color="#fff" style={{ marginLeft: 6 }} />}
        </TouchableOpacity>

        {!isLastSlide && (
          <TouchableOpacity onPress={() => void acceptAndProceed()} hitSlop={12}>
            <Text style={styles.skipText}>Skip intro</Text>
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
  trustBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: SPACING.xs ?? 6,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: RADIUS.md ?? 10,
    backgroundColor: COLORS.greenLight,
    alignSelf: 'flex-start',
  },
  trustBadgeText: {
    fontSize: FONT.xs ?? 12,
    fontWeight: '600',
    color: COLORS.green,
    flexShrink: 1,
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
    flexDirection: 'row',
    backgroundColor: COLORS.primary,
  },
  primaryBtnReady: {
    backgroundColor: COLORS.primary,
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 6,
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
