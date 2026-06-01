import React, { useEffect, useRef, useState } from 'react';
import {
  Modal,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Animated,
  Easing,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Dimensions,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { useTheme } from '@/hooks/useTheme';
import { submitReview } from '@/services/reviewService';

interface Props {
  visible: boolean;
  /** User tapped "Maybe later" or the X — counts as a dismissal. */
  onDismiss: () => void;
  /** User submitted a rating — does NOT count as a dismissal. */
  onReviewed: () => void;
}

const STAR_COPY: Record<number, { headline: string; placeholder: string; cta: string }> = {
  1: {
    headline: "We hear you — that's on us.",
    placeholder: "What went wrong? We read every word and fix fast.",
    cta: 'Send feedback',
  },
  2: {
    headline: 'Something isn\'t clicking. Tell us.',
    placeholder: 'What could feel better? Your take shapes the next update.',
    cta: 'Send feedback',
  },
  3: {
    headline: "You're onto something. What's missing?",
    placeholder: 'What would take this from okay to great for you?',
    cta: 'Share thoughts',
  },
  4: {
    headline: "Almost there — what would make it perfect?",
    placeholder: 'One thing that would make you rate it 5 stars?',
    cta: 'Share thoughts',
  },
  5: {
    headline: 'You made our day. Seriously.',
    placeholder: "What's your favourite part? (We'll brag about it in standups 😄)",
    cta: 'Send review',
  },
};

const DEFAULT_COPY = {
  headline: 'How is FocusFlow treating you?',
  placeholder: 'Any thoughts? Good or bad — we\'re listening.',
  cta: 'Send',
};

const STAR_COLORS: Record<number, string> = {
  1: COLORS.red,
  2: '#f97316',
  3: COLORS.orange,
  4: '#84cc16',
  5: COLORS.green,
};

export function ReviewModal({ visible, onDismiss, onReviewed }: Props) {
  const { theme } = useTheme();
  const [stars, setStars] = useState(0);
  const [hoveredStar, setHoveredStar] = useState(0);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  const cardTranslate = useRef(new Animated.Value(80)).current;
  const cardOpacity = useRef(new Animated.Value(0)).current;
  const overlayOpacity = useRef(new Animated.Value(0)).current;
  const sentScale = useRef(new Animated.Value(0.6)).current;
  const sentOpacity = useRef(new Animated.Value(0)).current;

  const starScales = useRef(
    Array.from({ length: 5 }, () => new Animated.Value(1))
  ).current;

  useEffect(() => {
    if (visible) {
      setSent(false);
      setStars(0);
      setText('');
      cardTranslate.setValue(80);
      cardOpacity.setValue(0);
      overlayOpacity.setValue(0);

      Animated.parallel([
        Animated.timing(overlayOpacity, {
          toValue: 1, duration: 250, useNativeDriver: true,
        }),
        Animated.spring(cardTranslate, {
          toValue: 0, friction: 7, tension: 60, useNativeDriver: true,
        }),
        Animated.timing(cardOpacity, {
          toValue: 1, duration: 280, useNativeDriver: true,
        }),
      ]).start();
    }
  }, [visible, cardTranslate, cardOpacity, overlayOpacity]);

  function animateStar(idx: number) {
    Animated.sequence([
      Animated.timing(starScales[idx], {
        toValue: 1.45, duration: 120, easing: Easing.out(Easing.quad), useNativeDriver: true,
      }),
      Animated.spring(starScales[idx], {
        toValue: 1, friction: 4, tension: 80, useNativeDriver: true,
      }),
    ]).start();
  }

  function handleStarPress(n: number) {
    setStars(n);
    animateStar(n - 1);
  }

  async function handleSubmit() {
    if (stars === 0) return;
    setSending(true);
    try {
      await submitReview({ stars, text: text.trim() });
    } catch {
      // fire and forget — don't block user
    } finally {
      setSending(false);
      setSent(true);
      sentScale.setValue(0.6);
      sentOpacity.setValue(0);
      Animated.parallel([
        Animated.spring(sentScale, {
          toValue: 1, friction: 5, tension: 70, useNativeDriver: true,
        }),
        Animated.timing(sentOpacity, {
          toValue: 1, duration: 300, useNativeDriver: true,
        }),
      ]).start();
      setTimeout(onReviewed, 1800);
    }
  }

  const displayStars = hoveredStar || stars;
  const copy = stars > 0 ? STAR_COPY[stars] : DEFAULT_COPY;
  const starColor = stars > 0 ? STAR_COLORS[stars] : COLORS.orange;

  if (!visible) return null;

  return (
    <Modal transparent visible animationType="none" onRequestClose={onDismiss}>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <Animated.View style={[styles.overlay, { opacity: overlayOpacity }]}>
          <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={onDismiss} />

          <Animated.View
            style={[
              styles.card,
              {
                backgroundColor: theme.card,
                borderColor: theme.border,
                transform: [{ translateY: cardTranslate }],
                opacity: cardOpacity,
              },
            ]}
          >
            {sent ? (
              <Animated.View
                style={[styles.sentContainer, { transform: [{ scale: sentScale }], opacity: sentOpacity }]}
              >
                <Text style={styles.sentEmoji}>🎉</Text>
                <Text style={[styles.sentTitle, { color: theme.text }]}>Thanks!</Text>
                <Text style={[styles.sentSub, { color: theme.muted }]}>Your feedback is in — it means a lot.</Text>
              </Animated.View>
            ) : (
              <ScrollView
                keyboardShouldPersistTaps="handled"
                contentContainerStyle={styles.scrollContent}
                showsVerticalScrollIndicator={false}
              >
                {/* Header */}
                <View style={styles.header}>
                  <View style={[styles.iconCircle, { backgroundColor: COLORS.primary + '18' }]}>
                    <Ionicons name="shield-checkmark" size={26} color={COLORS.primary} />
                  </View>
                  <TouchableOpacity onPress={onDismiss} style={styles.closeBtn} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
                    <Ionicons name="close" size={20} color={theme.muted} />
                  </TouchableOpacity>
                </View>

                <Text style={[styles.label, { color: theme.textSecondary }]}>QUICK REVIEW</Text>
                <Text style={[styles.headline, { color: theme.text }]}>{copy.headline}</Text>

                {/* Stars */}
                <View style={styles.starsRow}>
                  {[1, 2, 3, 4, 5].map((n) => (
                    <Animated.View key={n} style={{ transform: [{ scale: starScales[n - 1] }] }}>
                      <TouchableOpacity
                        onPress={() => handleStarPress(n)}
                        onPressIn={() => setHoveredStar(n)}
                        onPressOut={() => setHoveredStar(0)}
                        activeOpacity={0.7}
                        hitSlop={{ top: 6, bottom: 6, left: 4, right: 4 }}
                      >
                        <Ionicons
                          name={n <= displayStars ? 'star' : 'star-outline'}
                          size={38}
                          color={n <= displayStars ? starColor : theme.border}
                        />
                      </TouchableOpacity>
                    </Animated.View>
                  ))}
                </View>

                {/* Star label */}
                {stars > 0 && (
                  <Text style={[styles.starLabel, { color: starColor }]}>
                    {['', 'Not great', 'Needs work', 'It\'s okay', 'Pretty good', 'Love it!'][stars]}
                  </Text>
                )}

                {/* Text input */}
                <TextInput
                  style={[
                    styles.input,
                    {
                      backgroundColor: theme.surface,
                      borderColor: theme.border,
                      color: theme.text,
                    },
                  ]}
                  placeholder={copy.placeholder}
                  placeholderTextColor={theme.muted}
                  value={text}
                  onChangeText={setText}
                  multiline
                  numberOfLines={3}
                  maxLength={500}
                  textAlignVertical="top"
                />

                <Text style={[styles.charCount, { color: theme.muted }]}>{text.length}/500</Text>

                {/* Buttons */}
                <View style={styles.buttonRow}>
                  <TouchableOpacity
                    onPress={onDismiss}
                    style={[styles.skipBtn, { borderColor: theme.border }]}
                    activeOpacity={0.7}
                  >
                    <Text style={[styles.skipText, { color: theme.muted }]}>Maybe later</Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    onPress={handleSubmit}
                    style={[
                      styles.submitBtn,
                      {
                        backgroundColor: stars > 0 ? COLORS.primary : theme.border,
                        opacity: sending ? 0.7 : 1,
                      },
                    ]}
                    disabled={stars === 0 || sending}
                    activeOpacity={0.85}
                  >
                    {sending ? (
                      <Ionicons name="hourglass-outline" size={16} color="#fff" />
                    ) : (
                      <Ionicons name="send" size={16} color="#fff" />
                    )}
                    <Text style={styles.submitText}>{sending ? 'Sending…' : copy.cta}</Text>
                  </TouchableOpacity>
                </View>

                <Text style={[styles.privacy, { color: theme.muted }]}>
                  Your feedback goes directly to the team — no accounts, no tracking.
                </Text>
              </ScrollView>
            )}
          </Animated.View>
        </Animated.View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const { width } = Dimensions.get('window');

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'flex-end',
  },
  card: {
    borderTopLeftRadius: RADIUS.xl,
    borderTopRightRadius: RADIUS.xl,
    borderWidth: 1,
    borderBottomWidth: 0,
    maxHeight: '90%',
    width: '100%',
  },
  scrollContent: {
    padding: SPACING.xl,
    paddingBottom: SPACING.xxl + 8,
    gap: SPACING.md,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: SPACING.xs,
  },
  iconCircle: {
    width: 46,
    height: 46,
    borderRadius: 23,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeBtn: {
    padding: SPACING.xs,
  },
  label: {
    fontSize: FONT.xs,
    fontWeight: '700',
    letterSpacing: 1.4,
  },
  headline: {
    fontSize: FONT.xl,
    fontWeight: '800',
    lineHeight: 28,
    letterSpacing: -0.3,
  },
  starsRow: {
    flexDirection: 'row',
    gap: SPACING.sm,
    marginTop: SPACING.xs,
    alignItems: 'center',
  },
  starLabel: {
    fontSize: FONT.sm,
    fontWeight: '700',
    marginTop: -SPACING.xs,
  },
  input: {
    borderWidth: 1,
    borderRadius: RADIUS.md,
    padding: SPACING.md,
    fontSize: FONT.md,
    minHeight: 90,
    lineHeight: 22,
  },
  charCount: {
    fontSize: FONT.xs,
    textAlign: 'right',
    marginTop: -SPACING.xs,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: SPACING.sm,
    marginTop: SPACING.xs,
  },
  skipBtn: {
    flex: 1,
    borderWidth: 1,
    borderRadius: RADIUS.lg,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: SPACING.md,
  },
  skipText: {
    fontSize: FONT.sm,
    fontWeight: '600',
  },
  submitBtn: {
    flex: 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.xs,
    borderRadius: RADIUS.lg,
    paddingVertical: SPACING.md,
  },
  submitText: {
    color: '#fff',
    fontSize: FONT.md,
    fontWeight: '700',
  },
  privacy: {
    fontSize: FONT.xs,
    textAlign: 'center',
    lineHeight: 17,
    marginTop: SPACING.xs,
  },
  sentContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    padding: SPACING.xxl,
    gap: SPACING.sm,
  },
  sentEmoji: {
    fontSize: 52,
  },
  sentTitle: {
    fontSize: FONT.xl,
    fontWeight: '800',
  },
  sentSub: {
    fontSize: FONT.md,
    textAlign: 'center',
    lineHeight: 22,
  },
});
