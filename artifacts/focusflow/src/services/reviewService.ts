/**
 * reviewService.ts
 *
 * Sends user reviews to the server's /api/review endpoint.
 * The server (serve.js) proxies the request to Discord — the webhook
 * URL is never exposed to the app or included in the bundle.
 */

import Constants from 'expo-constants';

export interface ReviewPayload {
  stars: number;
  text: string;
}

function getApiBase(): string {
  const extra = Constants.expoConfig?.extra as Record<string, string> | undefined;
  if (extra?.apiUrl) return extra.apiUrl.replace(/\/$/, '');

  const debuggerHost = (Constants.expoConfig as any)?.debuggerHost as string | undefined;
  if (debuggerHost) {
    const host = debuggerHost.split(':')[0];
    return `http://${host}:3000`;
  }

  return '';
}

export async function submitReview(payload: ReviewPayload): Promise<void> {
  const base = getApiBase();
  const url = `${base}/api/review`;

  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`Review submission failed (${res.status}): ${body}`);
  }
}
