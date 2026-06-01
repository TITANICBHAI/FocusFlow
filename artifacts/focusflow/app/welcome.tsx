import React, { useRef, useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  ScrollView,
  Animated,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

const SLIDES = [
  {
    emoji: '🌸',
    title: 'Your focus, finally yours',
    body: "Social media and notifications pull you away from things that matter. FocusFlow gently puts you back on track — without stress or guilt.",
    accent: '#7C6AF7',
    bg: '#F5F3FF',
    dot: '#7C6AF7',
  },
  {
    emoji: '🌿',
    title: 'Build habits that actually stick',
    body: "Set your focus time, pick what to block, and let FocusFlow do the heavy lifting. Small sessions today grow into real discipline tomorrow.",
    accent: '#2EAD6C',
    bg: '#F0FBF6',
    dot: '#2EAD6C',
  },
  {
    emoji: '🌱',
    title: 'Watch yourself grow',
    body: "Your streaks, your stats, your wins — all in one place. See how your focus time grows week by week, and celebrate every milestone.",
    accent: '#E8882A',
    bg: '#FFF8F0',
    dot: '#E8882A',
  },
];

export default function WelcomeScreen() {
  const scrollRef = useRef<ScrollView>(null);
  const [activeIndex, setActiveIndex] = useState(0);
  const fadeAnim = useRef(new Animated.Value(1)).current;

  const goToSlide = (index: number) => {
    Animated.timing(fadeAnim, { toValue: 0, duration: 120, useNativeDriver: true }).start(() => {
      scrollRef.current?.scrollTo({ x: index * SCREEN_WIDTH, animated: false });
      setActiveIndex(index);
      Animated.timing(fadeAnim, { toValue: 1, duration: 200, useNativeDriver: true }).start();
    });
  };

  const handleScroll = (e: any) => {
    const index = Math.round(e.nativeEvent.contentOffset.x / SCREEN_WIDTH);
    if (index !== activeIndex) setActiveIndex(index);
  };

  const handleNext = () => {
    if (activeIndex < SLIDES.length - 1) {
      goToSlide(activeIndex + 1);
    } else {
      router.replace('/privacy-policy');
    }
  };

  const slide = SLIDES[activeIndex];

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: slide.bg }]}>
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
          <View key={i} style={[styles.slide, { width: SCREEN_WIDTH, backgroundColor: s.bg }]}>
            <View style={[styles.emojiCircle, { backgroundColor: s.accent + '18' }]}>
              <Text style={styles.emoji}>{s.emoji}</Text>
            </View>
            <Text style={[styles.title, { color: '#1A1A2E' }]}>{s.title}</Text>
            <Text style={[styles.body, { color: '#555' }]}>{s.body}</Text>
          </View>
        ))}
      </ScrollView>

      <View style={styles.footer}>
        <View style={styles.dots}>
          {SLIDES.map((s, i) => (
            <TouchableOpacity key={i} onPress={() => goToSlide(i)} hitSlop={10}>
              <View
                style={[
                  styles.dot,
                  {
                    backgroundColor: i === activeIndex ? slide.accent : slide.accent + '33',
                    width: i === activeIndex ? 24 : 8,
                  },
                ]}
              />
            </TouchableOpacity>
          ))}
        </View>

        <TouchableOpacity
          style={[styles.nextBtn, { backgroundColor: slide.accent }]}
          onPress={handleNext}
          activeOpacity={0.85}
        >
          <Text style={styles.nextBtnText}>
            {activeIndex < SLIDES.length - 1 ? 'Next' : "Let's go →"}
          </Text>
        </TouchableOpacity>

        {activeIndex < SLIDES.length - 1 && (
          <TouchableOpacity onPress={() => router.replace('/privacy-policy')} hitSlop={12}>
            <Text style={[styles.skipText, { color: slide.accent + '99' }]}>Skip</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
  },
  pager: {
    flex: 1,
  },
  slide: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: SPACING.xl ?? 32,
    gap: SPACING.lg ?? 24,
  },
  emojiCircle: {
    width: 140,
    height: 140,
    borderRadius: 70,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  emoji: {
    fontSize: 72,
  },
  title: {
    fontSize: FONT.xxl ?? 26,
    fontWeight: '900',
    textAlign: 'center',
    lineHeight: 34,
    letterSpacing: -0.5,
  },
  body: {
    fontSize: FONT.md ?? 16,
    textAlign: 'center',
    lineHeight: 24,
    maxWidth: 320,
  },
  footer: {
    paddingHorizontal: SPACING.lg ?? 24,
    paddingBottom: Platform.OS === 'android' ? 24 : 12,
    paddingTop: 16,
    gap: 14,
    alignItems: 'center',
  },
  dots: {
    flexDirection: 'row',
    gap: 6,
    alignItems: 'center',
  },
  dot: {
    height: 8,
    borderRadius: 4,
  },
  nextBtn: {
    width: '100%',
    paddingVertical: 16,
    borderRadius: RADIUS.lg ?? 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nextBtnText: {
    color: '#fff',
    fontSize: FONT.md ?? 16,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  skipText: {
    fontSize: FONT.sm ?? 14,
    fontWeight: '600',
    paddingVertical: 4,
  },
});
