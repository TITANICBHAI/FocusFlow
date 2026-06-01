/**
 * useReviewPrompt.ts
 *
 * Smart timing hook for the in-app review modal.
 *
 * Trigger rules:
 *  - First prompt: after the user completes their 3rd focus session.
 *  - Repeat:       no sooner than 21 days after the last prompt.
 *  - Silenced:     permanently after the user dismisses (without reviewing) twice.
 *  - Never shown:  during an active focus session.
 *
 * Dismiss vs. review:
 *  - "Maybe later" / close → counts as a dismissal (max 2, then silenced forever).
 *  - Submitting any rating → does NOT count as a dismissal; prompt won't resurface.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { useApp } from '@/context/AppContext';

const FIRST_PROMPT_SESSIONS = 3;
const COOLDOWN_DAYS         = 21;
const MAX_DISMISSALS        = 2;

function daysSince(isoString: string): number {
  return (Date.now() - new Date(isoString).getTime()) / (1000 * 60 * 60 * 24);
}

export function useReviewPrompt() {
  const { state, updateSettings } = useApp();
  const [visible, setVisible]     = useState(false);
  const prevSessionActiveRef      = useRef<boolean>(false);

  /** User tapped "Maybe later" or the X — counts against their 2-dismissal limit. */
  const dismissWithoutReview = useCallback(async () => {
    setVisible(false);
    const prev = state.settings.reviewDismissCount ?? 0;
    await updateSettings({
      ...state.settings,
      lastReviewPromptAt: new Date().toISOString(),
      reviewDismissCount: prev + 1,
    }).catch(() => {});
  }, [state.settings, updateSettings]);

  /** User submitted a rating — silence prompt permanently, don't burn a dismissal. */
  const dismissAfterReview = useCallback(async () => {
    setVisible(false);
    // Set dismissCount to MAX so it never surfaces again.
    await updateSettings({
      ...state.settings,
      lastReviewPromptAt:  new Date().toISOString(),
      reviewDismissCount:  MAX_DISMISSALS,
    }).catch(() => {});
  }, [state.settings, updateSettings]);

  useEffect(() => {
    const sessionActive = state.focusSession != null;
    const wasActive     = prevSessionActiveRef.current;
    prevSessionActiveRef.current = sessionActive;

    // Only fire when a session just ended (active → inactive transition).
    if (!wasActive || sessionActive) return;

    const settings           = state.settings;
    const dismissCount       = settings.reviewDismissCount ?? 0;
    const completedSessions  = (settings.reviewSessionCount ?? 0) + 1;

    // Persist incremented session count.
    void updateSettings({ ...settings, reviewSessionCount: completedSessions }).catch(() => {});

    // Permanently silenced after MAX_DISMISSALS without a review.
    if (dismissCount >= MAX_DISMISSALS) return;

    // Haven't hit the minimum session threshold yet.
    if (completedSessions < FIRST_PROMPT_SESSIONS) return;

    // Still within cooldown window from last prompt.
    const lastPrompt = settings.lastReviewPromptAt;
    if (lastPrompt && daysSince(lastPrompt) < COOLDOWN_DAYS) return;

    // All clear — show after a short delay so the session-end UI settles first.
    const timer = setTimeout(() => setVisible(true), 2000);
    return () => clearTimeout(timer);
  }, [state.focusSession, state.settings, updateSettings]);

  return { visible, dismissWithoutReview, dismissAfterReview };
}
