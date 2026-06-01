/**
 * useReviewPrompt.ts
 *
 * Smart timing hook for the in-app review modal.
 *
 * Counts any of these as an "engagement action":
 *  - Focus session ends (active → null)
 *  - Standalone block toggled on or off (standaloneBlockUntil changes)
 *  - Always-on enforcement toggled on or off (alwaysOnEnforcementEnabled changes)
 *
 * Prompt rules:
 *  - First prompt: after 3 engagement actions.
 *  - Repeat:       no sooner than 21 days after the last prompt.
 *  - Silenced:     permanently after 2 "Maybe later" dismissals.
 *  - Never shown:  while a focus session is active.
 *
 * Dismiss vs. review:
 *  - "Maybe later" / close → counts as a dismissal (max 2, then silenced forever).
 *  - Submitting any rating → silences prompt permanently without burning a dismissal.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { useApp } from '@/context/AppContext';

const FIRST_PROMPT_ACTIONS = 3;
const COOLDOWN_DAYS        = 21;
const MAX_DISMISSALS       = 2;

function daysSince(isoString: string): number {
  return (Date.now() - new Date(isoString).getTime()) / (1000 * 60 * 60 * 24);
}

export function useReviewPrompt() {
  const { state, updateSettings } = useApp();
  const [visible, setVisible]     = useState(false);

  // Previous values for detecting transitions
  const prevFocusActiveRef       = useRef<boolean>(false);
  const prevStandaloneBlockRef   = useRef<string | null | undefined>(undefined);
  const prevAlwaysOnRef          = useRef<boolean | undefined>(undefined);

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

  /** User submitted a rating — silence permanently, don't burn a dismissal slot. */
  const dismissAfterReview = useCallback(async () => {
    setVisible(false);
    await updateSettings({
      ...state.settings,
      lastReviewPromptAt:  new Date().toISOString(),
      reviewDismissCount:  MAX_DISMISSALS,
    }).catch(() => {});
  }, [state.settings, updateSettings]);

  // ── Track engagement actions ─────────────────────────────────────────────
  useEffect(() => {
    const focusActive      = state.focusSession != null;
    const standaloneBlock  = state.settings.standaloneBlockUntil;
    const alwaysOnEnabled  = state.settings.alwaysOnEnforcementEnabled ?? false;

    const wasFocusActive    = prevFocusActiveRef.current;
    const prevStandalone    = prevStandaloneBlockRef.current;
    const prevAlwaysOn      = prevAlwaysOnRef.current;

    // Initialise refs on first run without counting anything
    const isFirstRun =
      prevStandaloneBlockRef.current === undefined &&
      prevAlwaysOnRef.current       === undefined;

    prevFocusActiveRef.current     = focusActive;
    prevStandaloneBlockRef.current = standaloneBlock;
    prevAlwaysOnRef.current        = alwaysOnEnabled;

    if (isFirstRun) return;

    // Detect transitions
    const focusSessionEnded       = wasFocusActive && !focusActive;
    const standaloneBlockToggled  = prevStandalone !== standaloneBlock;
    const alwaysOnToggled         = prevAlwaysOn !== undefined && prevAlwaysOn !== alwaysOnEnabled;

    const actionFired = focusSessionEnded || standaloneBlockToggled || alwaysOnToggled;
    if (!actionFired) return;

    // Don't interrupt an active focus session
    if (focusActive) return;

    const settings       = state.settings;
    const dismissCount   = settings.reviewDismissCount  ?? 0;
    const actionCount    = (settings.reviewSessionCount ?? 0) + 1;

    void updateSettings({ ...settings, reviewSessionCount: actionCount }).catch(() => {});

    // Permanently silenced
    if (dismissCount >= MAX_DISMISSALS) return;

    // Haven't hit the minimum action threshold yet
    if (actionCount < FIRST_PROMPT_ACTIONS) return;

    // Still within cooldown from last prompt
    const lastPrompt = settings.lastReviewPromptAt;
    if (lastPrompt && daysSince(lastPrompt) < COOLDOWN_DAYS) return;

    // All clear — show after a short delay so any UI transition settles
    const timer = setTimeout(() => setVisible(true), 2000);
    return () => clearTimeout(timer);
  }, [
    state.focusSession,
    state.settings.standaloneBlockUntil,
    state.settings.alwaysOnEnforcementEnabled,
    state.settings,
    updateSettings,
  ]);

  return { visible, dismissWithoutReview, dismissAfterReview };
}
