/**
 * navigationRef.ts
 *
 * A shared navigation reference that lets non-component code (notification
 * handlers, background task callbacks) navigate to screens without needing
 * a prop chain.
 *
 * Usage:
 *   import { navigationRef, navigate } from '@/navigation/navigationRef';
 *   navigate('Schedule');   // works from anywhere, including task managers
 */

import { createNavigationContainerRef } from '@react-navigation/native';

export type RootParamList = {
  Schedule: { highlightTaskId?: string } | undefined;
  Focus:    { taskId?: string } | undefined;
  Stats:    undefined;
  Settings: undefined;
};

export const navigationRef = createNavigationContainerRef<RootParamList>();

/**
 * Navigate to a screen from outside React components.
 * Safe to call even before the navigator is mounted — it silently no-ops.
 */
export function navigate(name: keyof RootParamList, params?: RootParamList[keyof RootParamList]) {
  if (navigationRef.isReady()) {
    navigationRef.navigate(name as any, params as any);
  }
}

/**
 * Navigate to the Schedule tab and highlight a specific task card.
 */
export function navigateToTask(taskId: string) {
  navigate('Schedule', { highlightTaskId: taskId });
}
