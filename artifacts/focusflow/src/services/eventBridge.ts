/**
 * Event Bridge — JS ↔ Native Real-Time Sync
 *
 * Handles bidirectional events between:
 *   - Native foreground service → JS (task timer ticks, task end, violation detected)
 *   - JS → Native (start/stop service, update task, user actions)
 *
 * Native side emits events via DeviceEventManagerModule.RCTDeviceEventEmitter (Android).
 * This module listens for them on the JS side.
 *
 * Pattern:
 *   - Native fires: "FocusDayEvent" with { type, payload }
 *   - JS handlers registered per-event-type via subscribe()
 */

import { NativeEventEmitter, TurboModuleRegistry, TurboModule, EmitterSubscription } from 'react-native';

export type NativeEventType =
  | 'TASK_ENDED'        // foreground service: task timer reached zero
  | 'TASK_TICK'         // foreground service: every 30s update
  | 'APP_BLOCKED'       // app blocker: forbidden app was detected
  | 'FOCUS_START'       // native side started focus mode
  | 'FOCUS_STOP'        // native side stopped focus mode
  | 'SERVICE_RESTART'   // foreground service restarted after being killed
  | 'BOOT_COMPLETED'    // device rebooted, schedule needs restoring
  | 'PERMISSION_RESULT' // user responded to a permission prompt
  | 'BATTERY_LOW';      // battery optimization is blocking the service

export interface NativeEvent {
  type: NativeEventType;
  taskId?: string;
  taskName?: string;
  remainingSeconds?: number;
  blockedApp?: string;
  granted?: boolean;
}

type EventHandler = (event: NativeEvent) => void;

interface FocusDayBridgeSpec extends TurboModule {
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

class EventBridgeClass {
  private emitter: NativeEventEmitter | null = null;
  private subscription: EmitterSubscription | null = null;
  private handlers = new Map<NativeEventType, Set<EventHandler>>();

  init(): void {
    // Use TurboModuleRegistry.get for correct resolution under RN 0.76 New Architecture
    // bridgeless mode. NativeModules.FocusDayBridge may return an empty proxy that is
    // truthy but whose methods are no-ops; TurboModuleRegistry.get returns null if unlinked.
    const bridge = TurboModuleRegistry.get<FocusDayBridgeSpec>('FocusDayBridge');
    if (!bridge) {
      console.error('[EventBridge] FocusDayBridge native module not found. Ensure FocusDayPackage is registered and an EAS build was used. Running in stub mode — native events will not fire.');
      return;
    }

    this.emitter = new NativeEventEmitter(bridge);
    this.subscription = this.emitter.addListener('FocusDayEvent', (event: NativeEvent) => {
      this.dispatch(event);
    });
  }

  subscribe(type: NativeEventType, handler: EventHandler): () => void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, new Set());
    }
    this.handlers.get(type)!.add(handler);

    return () => {
      this.handlers.get(type)?.delete(handler);
    };
  }

  private dispatch(event: NativeEvent): void {
    const handlers = this.handlers.get(event.type);
    if (!handlers) return;
    handlers.forEach((h) => {
      try {
        h(event);
      } catch (e) {
        console.error('[EventBridge] Handler error for', event.type, e);
      }
    });
  }

  destroy(): void {
    this.subscription?.remove();
    this.subscription = null;
    this.handlers.clear();
  }
}

export const EventBridge = new EventBridgeClass();

// ─── React hook for subscribing to native events ──────────────────────────────

import { useEffect } from 'react';

export function useNativeEvent(type: NativeEventType, handler: EventHandler): void {
  useEffect(() => {
    const unsub = EventBridge.subscribe(type, handler);
    return unsub;
  }, [type, handler]);
}
