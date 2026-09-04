import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const nativeRoot = path.resolve(__dirname, '../../android-native/app/src/main/java/com/tbtechs/focusflow');
const sharedPrefsKotlin = readFileSync(path.join(nativeRoot, 'modules/SharedPrefsModule.kt'), 'utf8');
const sharedPrefsTs = readFileSync(path.resolve(__dirname, '../../src/native-modules/SharedPrefsModule.ts'), 'utf8');
const focusService = readFileSync(path.resolve(__dirname, '../../src/services/focusService.ts'), 'utf8');
const appContext = readFileSync(path.resolve(__dirname, '../../src/context/AppContext.tsx'), 'utf8');
const vpnCoordinator = readFileSync(path.join(nativeRoot, 'services/VpnPolicyCoordinator.kt'), 'utf8');

function methodBody(source: string, methodName: string): string {
  const start = source.indexOf(`fun ${methodName}(`);
  expect(start).toBeGreaterThanOrEqual(0);
  const next = source.indexOf('\n    @ReactMethod', start + 1);
  return source.slice(start, next < 0 ? source.length : next);
}

describe('Phase 3 native persistence contracts', () => {
  it('escapes every native ReadableArray through JSONArray', () => {
    expect(sharedPrefsKotlin).toContain('private fun ReadableArray.toJsonArrayString()');
    expect(sharedPrefsKotlin).toContain('val array = org.json.JSONArray()');
    expect(sharedPrefsKotlin).not.toMatch(/map\s*\{\s*"\\?"\$\{/);

    for (const method of [
      'setAllowedPackages',
      'setStandaloneBlock',
      'setAlwaysBlockActive',
      'setDailyAllowancePackages',
      'setBlockedWords',
    ]) {
      expect(methodBody(sharedPrefsKotlin, method)).toContain('toJsonArrayString()');
    }
  });

  it('commits focus active and inactive states as one native snapshot', () => {
    const body = methodBody(sharedPrefsKotlin, 'publishFocusSnapshot');
    expect(body).toContain('val editor = prefs().edit()');
    expect(body).toContain('.putBoolean("focus_active", true)');
    expect(body).toContain('.putBoolean("focus_active", false)');
    expect(body).toContain('if (!editor.commit())');
    expect(body).not.toContain('.apply()');
    expect(sharedPrefsTs).toContain('publishFocusSnapshot(');
    expect(focusService).toContain('SharedPrefsModule.publishFocusSnapshot(');
    expect(focusService).not.toContain('SharedPrefsModule.setFocusActive(');
    expect(focusService).not.toContain('SharedPrefsModule.setAllowedPackages(');
    expect(focusService).not.toContain('SharedPrefsModule.setActiveTask(');
  });

  it('commits standalone blocking state atomically and leaves VPN policy ownership intact', () => {
    const body = methodBody(sharedPrefsKotlin, 'publishStandaloneSnapshot');
    expect(body).toContain('val editor = prefs().edit()');
    expect(body).toContain('.putBoolean("standalone_block_active", true)');
    expect(body).toContain('.putBoolean("standalone_block_active", false)');
    expect(body).toContain('if (!editor.commit())');
    expect(body).not.toContain('.apply()');
    expect(sharedPrefsTs).toContain('publishStandaloneSnapshot(');
    expect(appContext).toContain('SharedPrefsModule.publishStandaloneSnapshot(');
    expect(vpnCoordinator).not.toContain('publishFocusSnapshot');
    expect(vpnCoordinator).not.toContain('publishStandaloneSnapshot');
  });
});