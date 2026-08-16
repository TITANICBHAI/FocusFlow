import Constants from 'expo-constants';
import { Platform } from 'react-native';

const REPORT_PATH = '/api/diagnostics/report';
const MAX_DESCRIPTION_LENGTH = 2_000;
const MAX_LOG_LENGTH = 18_000;

export type DiagnosticsReportInput = {
  description: string;
  logs: string;
};

function getReportUrl(): string | null {
  const configuredUrl = process.env.EXPO_PUBLIC_DIAGNOSTICS_REPORT_URL?.trim();
  if (configuredUrl) return configuredUrl;

  if (Platform.OS === 'web') return REPORT_PATH;

  const domain = process.env.EXPO_PUBLIC_DOMAIN?.trim();
  if (!domain) return null;
  return `https://${domain.replace(/^https?:\/\//i, '').replace(/\/+$/, '')}${REPORT_PATH}`;
}

function sanitize(value: string, maxLength: number): string {
  return value
    .replace(/\bhttps?:\/\/\S+/gi, '[redacted-url]')
    .replace(/\bwww\.\S+/gi, '[redacted-url]')
    .replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, '[redacted-email]')
    .replace(/\b(password|passwd|token|secret|api[_-]?key)\s*[:=]\s*\S+/gi, '$1=[redacted]')
    .slice(0, maxLength);
}

export async function sendDiagnosticsReport(
  input: DiagnosticsReportInput,
): Promise<{ ok: true } | { ok: false; error: string }> {
  const endpoint = getReportUrl();
  if (!endpoint) {
    return {
      ok: false,
      error: 'Reporting is not configured for this build yet.',
    };
  }

  const payload = {
    description: sanitize(input.description.trim(), MAX_DESCRIPTION_LENGTH),
    logs: sanitize(input.logs, MAX_LOG_LENGTH),
    app: {
      name: 'FocusFlow',
      version: Constants.expoConfig?.version ?? 'unknown',
      platform: Platform.OS,
      osVersion: String(Platform.Version),
    },
  };

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });

    if (!response.ok) {
      return {
        ok: false,
        error: 'The report could not be sent. Please try again later.',
      };
    }

    return { ok: true };
  } catch {
    return {
      ok: false,
      error: 'The report could not be sent. Check your connection and try again.',
    };
  } finally {
    clearTimeout(timeout);
  }
}