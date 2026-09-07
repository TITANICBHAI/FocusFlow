/**
 * Home Launcher configuration.
 *
 * The native launcher owns layout, drawer editing, and the visual theme.
 * This screen owns the durable user choices that need to be shared with the
 * React Native settings experience: theme, Focus Tools, wallpaper, drawer
 * visibility, and launcher protections.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  AppState,
  Image,
  Linking,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';

import { useApp } from '@/context/AppContext';
import { useTheme } from '@/hooks/useTheme';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import { InstalledAppsModule, InstalledApp } from '@/native-modules/InstalledAppsModule';
import { NativeImagePickerModule } from '@/native-modules/NativeImagePickerModule';

export default function HomeLauncherScreen() {
  const insets = useSafeAreaInsets();
  const { theme } = useTheme();
  const { state, updateSettings } = useApp();
  const { settings } = state;
  const [isDefault, setIsDefault] = useState<boolean | null>(null);
  const [checkingDefault, setCheckingDefault] = useState(true);
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [loadingApps, setLoadingApps] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');

  const standaloneActive = Boolean(
    settings.standaloneBlockUntil &&
      (settings.standaloneBlockPackages ?? []).length > 0 &&
      new Date(settings.standaloneBlockUntil).getTime() > Date.now(),
  );
  const blockedPackages = useMemo(
    () => new Set([...(settings.standaloneBlockPackages ?? []), ...(settings.alwaysOnPackages ?? [])]),
    [settings.standaloneBlockPackages, settings.alwaysOnPackages],
  );
  const focusTools = useMemo(() => new Set(settings.focusToolPackages ?? []), [settings.focusToolPackages]);
  const hiddenPackages = useMemo(
    () => new Set(settings.launcherHiddenPackages ?? []),
    [settings.launcherHiddenPackages],
  );

  const checkDefault = useCallback(async () => {
    setCheckingDefault(true);
    try {
      setIsDefault(await SharedPrefsModule.isDefaultLauncher());
    } catch {
      setIsDefault(false);
    } finally {
      setCheckingDefault(false);
    }
  }, []);

  useEffect(() => {
    void checkDefault();
    InstalledAppsModule.getInstalledApps()
      .then(setApps)
      .catch(() => {})
      .finally(() => setLoadingApps(false));
  }, [checkDefault]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (value) => {
      if (value === 'active') void checkDefault();
    });
    return () => sub.remove();
  }, [checkDefault]);

  const update = useCallback(
    async (partial: Partial<typeof settings>) => {
      try {
        await updateSettings({ ...settings, ...partial });
      } catch {
        Alert.alert('Error', 'Failed to save this setting. Please try again.');
      }
    },
    [settings, updateSettings],
  );

  const toggleFocusTool = useCallback(
    (pkg: string) => {
      const next = new Set(settings.focusToolPackages ?? []);
      if (next.has(pkg)) next.delete(pkg);
      else next.add(pkg);
      void update({ focusToolPackages: Array.from(next) });
    },
    [settings.focusToolPackages, update],
  );

  const toggleHidden = useCallback(
    (pkg: string) => {
      const next = new Set(settings.launcherHiddenPackages ?? []);
      if (next.has(pkg)) {
        next.delete(pkg);
      } else {
        if (!blockedPackages.has(pkg)) {
          Alert.alert(
            'Only blocked apps can be hidden',
            'Add this app to your standalone block list or Always-On list first, then hide it from the drawer.',
          );
          return;
        }
        next.add(pkg);
      }
      void update({ launcherHiddenPackages: Array.from(next) });
    },
    [blockedPackages, settings.launcherHiddenPackages, update],
  );

  const filteredApps = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return apps;
    return apps.filter(
      (app) =>
        app.appName.toLowerCase().includes(query) ||
        app.packageName.toLowerCase().includes(query),
    );
  }, [apps, searchQuery]);

  const handleSetDefault = () => {
    Linking.sendIntent('android.settings.HOME_SETTINGS').catch(() =>
      Linking.sendIntent('android.settings.MANAGE_DEFAULT_APPS_SETTINGS').catch(() =>
        Linking.openSettings(),
      ),
    );
  };

  const handlePickWallpaper = useCallback(async () => {
    try {
      const uri = await NativeImagePickerModule.pickImage();
      if (!uri) return;
      const path = uri.startsWith('file://') ? uri.slice('file://'.length) : uri;
      await update({ launcherWallpaperUri: path });
      await SharedPrefsModule.putString('launcher_wallpaper', path);
    } catch {
      Alert.alert(
        'Could Not Pick Image',
        'Please grant photo access in Settings, then try again.',
        [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Open Settings', onPress: () => Linking.openSettings() },
        ],
      );
    }
  }, [update]);

  const clearWallpaper = useCallback(async () => {
    await update({ launcherWallpaperUri: null });
    await SharedPrefsModule.putString('launcher_wallpaper', '');
  }, [update]);

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <View style={[styles.header, { backgroundColor: theme.card, borderBottomColor: theme.border }]}>
        <TouchableOpacity
          onPress={() => router.back()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <Ionicons name="chevron-back" size={24} color={theme.text} />
        </TouchableOpacity>
        <View style={{ flex: 1, marginLeft: SPACING.sm }}>
          <Text style={[styles.title, { color: theme.text }]}>Home Launcher</Text>
          <Text style={[styles.subtitle, { color: theme.muted }]}>
            FocusFlow as your default home screen
          </Text>
        </View>
      </View>

      {standaloneActive ? (
        <View style={[styles.lockedScreen, { backgroundColor: theme.background }]}>
          <View style={[styles.lockedCard, { backgroundColor: theme.card, borderColor: COLORS.orange + '55' }]}>
            <View style={styles.lockedIconRing}>
              <Ionicons name="lock-closed" size={32} color={COLORS.orange} />
            </View>
            <Text style={[styles.lockedHeading, { color: theme.text }]}>Launcher Locked</Text>
            <Text style={[styles.lockedBody, { color: theme.muted }]}>
              Launcher settings are disabled while a standalone block is active.{'\n\n'}
              Stop the current block to change launcher configuration.
            </Text>
            <TouchableOpacity style={styles.lockedBackBtn} onPress={() => router.back()}>
              <Ionicons name="chevron-back" size={16} color="#fff" />
              <Text style={styles.lockedBackText}>Go Back</Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={[styles.content, { paddingBottom: 40 + insets.bottom }]}
        >
          <View style={[styles.statusCard, {
            backgroundColor: isDefault ? COLORS.green + '12' : theme.card,
            borderColor: isDefault ? COLORS.green + '44' : theme.border,
          }]}>
            <View style={styles.statusRow}>
              <View style={[styles.statusIcon, {
                backgroundColor: (isDefault ? COLORS.green : COLORS.orange) + '20',
              }]}>
                {checkingDefault ? (
                  <ActivityIndicator size="small" color={COLORS.primary} />
                ) : (
                  <Ionicons
                    name={isDefault ? 'checkmark-circle' : 'alert-circle-outline'}
                    size={24}
                    color={isDefault ? COLORS.green : COLORS.orange}
                  />
                )}
              </View>
              <View style={{ flex: 1, gap: 2 }}>
                <Text style={[styles.statusTitle, { color: theme.text }]}>
                  {checkingDefault
                    ? 'Checking...'
                    : isDefault
                    ? 'FocusFlow is your default home app'
                    : 'FocusFlow is not the default home app'}
                </Text>
                <Text style={[styles.statusDesc, { color: theme.muted }]}>
                  {isDefault
                    ? 'Every app tap routes through FocusFlow — zero reaction delay.'
                    : 'Set FocusFlow as your home app to get instant interception.'}
                </Text>
              </View>
            </View>
            {!isDefault && (
              <TouchableOpacity style={styles.setDefaultBtn} onPress={handleSetDefault}>
                <Ionicons name="home-outline" size={16} color="#fff" />
                <Text style={styles.setDefaultBtnText}>Set as Default Home App</Text>
              </TouchableOpacity>
            )}
          </View>

          <SectionHeader
            icon="color-palette-outline"
            title="Appearance"
            description="Choose the visual treatment for the native launcher."
            theme={theme}
          />
          <View style={styles.themeRow}>
            <ThemePreviewCard
              label="Classic"
              active={(settings.launcherTheme ?? 'glassy') === 'classic'}
              onPress={() => void update({ launcherTheme: 'classic' })}
              variant="classic"
            />
            <ThemePreviewCard
              label="Glassy"
              active={(settings.launcherTheme ?? 'glassy') === 'glassy'}
              onPress={() => void update({ launcherTheme: 'glassy' })}
              variant="glassy"
            />
          </View>
          <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <View style={styles.settingRow}>
              <View style={{ flex: 1, gap: 3 }}>
                <Text style={[styles.settingLabel, { color: theme.text }]}>Launcher theme</Text>
                <Text style={[styles.settingDesc, { color: theme.muted }]}>
                  Glassy uses wallpaper and frosted surfaces. Classic uses a flat dark layout.
                </Text>
              </View>
            </View>
            {(settings.launcherTheme ?? 'glassy') === 'glassy' && (
              <TouchableOpacity style={styles.settingRow} onPress={() => void handlePickWallpaper()}>
                <View style={{ flex: 1, gap: 3 }}>
                  <Text style={[styles.settingLabel, { color: theme.text }]}>
                    {settings.launcherWallpaperUri ? 'Custom wallpaper' : 'Wallpaper'}
                  </Text>
                  <Text style={[styles.settingDesc, { color: theme.muted }]}>
                    {settings.launcherWallpaperUri
                      ? 'Tap to choose a different image'
                      : 'Uses the system wallpaper by default — tap to choose an image'}
                  </Text>
                </View>
                {settings.launcherWallpaperUri ? (
                  <TouchableOpacity onPress={() => void clearWallpaper()} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
                    <Ionicons name="close-circle-outline" size={20} color={theme.muted} />
                  </TouchableOpacity>
                ) : (
                  <Ionicons name="image-outline" size={18} color={theme.muted} />
                )}
              </TouchableOpacity>
            )}
          </View>

          <SectionHeader
            icon="sparkles-outline"
            title="Focus Tools"
            description="Choose the apps shown by the Focus Tools chip in the native drawer."
            theme={theme}
          />
          <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <AppList
              apps={filteredApps}
              loading={loadingApps}
              checked={focusTools}
              onToggle={toggleFocusTool}
              theme={theme}
              emptyText="No installed apps match this search."
              badge={(app) => blockedPackages.has(app.packageName) ? 'blocked' : undefined}
            />
          </View>

          <View style={[styles.searchBox, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Ionicons name="search-outline" size={18} color={theme.muted} />
            <TextInput
              value={searchQuery}
              onChangeText={setSearchQuery}
              placeholder="Search installed apps"
              placeholderTextColor={theme.muted}
              autoCapitalize="none"
              autoCorrect={false}
              style={[styles.searchInput, { color: theme.text }]}
            />
            {searchQuery.length > 0 && (
              <TouchableOpacity onPress={() => setSearchQuery('')}>
                <Ionicons name="close-circle" size={18} color={theme.muted} />
              </TouchableOpacity>
            )}
          </View>

          <SectionHeader
            icon="eye-off-outline"
            title="App Drawer Visibility"
            description="Only blocked apps can be hidden from the drawer. Glassy also supports hiding directly in Edit Mode."
            theme={theme}
          />
          <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
            {Array.from(blockedPackages).length === 0 ? (
              <View style={styles.emptyRow}>
                <Ionicons name="information-circle-outline" size={18} color={theme.muted} />
                <Text style={[styles.emptyText, { color: theme.muted }]}>
                  No blocked apps yet. Add apps to Standalone or Always-On first.
                </Text>
              </View>
            ) : (
              Array.from(blockedPackages).map((pkg) => {
                const app = apps.find((candidate) => candidate.packageName === pkg) ?? {
                  packageName: pkg,
                  appName: pkg,
                  isIme: false,
                };
                return (
                  <AppToggleRow
                    key={pkg}
                    app={app}
                    checked={hiddenPackages.has(pkg)}
                    onToggle={() => toggleHidden(pkg)}
                    theme={theme}
                    badge="blocked"
                  />
                );
              })
            )}
          </View>

          <SectionHeader
            icon="shield-checkmark-outline"
            title="Launcher Protections"
            description="Extra guards that apply specifically because FocusFlow is your home screen."
            theme={theme}
          />
          <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <SwitchRow
              label="Lock launcher during standalone block"
              description="Prevent switching away from FocusFlow while a standalone block is running."
              value={settings.launcherLockDuringStandalone ?? true}
              onValueChange={(value) => void update({ launcherLockDuringStandalone: value })}
              theme={theme}
              isLast
            />
          </View>

          <View style={[styles.tipCard, { backgroundColor: theme.card, borderColor: COLORS.primary + '33' }]}>
            <Ionicons name="bulb-outline" size={16} color={COLORS.primary} />
            <Text style={[styles.tipText, { color: theme.muted }]}>
              <Text style={{ fontWeight: '700', color: theme.text }}>How it works: </Text>
              The native launcher reads block state directly from SharedPreferences, so blocked apps can be intercepted before they start.
            </Text>
          </View>
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

function ThemePreviewCard({
  label,
  active,
  onPress,
  variant,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
  variant: 'classic' | 'glassy';
}) {
  const isGlassy = variant === 'glassy';
  return (
    <TouchableOpacity
      style={[
        styles.themePreviewCard,
        { backgroundColor: isGlassy ? '#243457' : '#18181A' },
        active && styles.themePreviewCardActive,
      ]}
      onPress={onPress}
      activeOpacity={0.85}
    >
      <View style={styles.themePreviewHeader}>
        <Text style={styles.themePreviewLabel}>{label}</Text>
        {active && <Ionicons name="checkmark-circle" size={16} color="#A5B4FC" />}
      </View>
      <View style={[styles.themeMiniHome, !isGlassy && styles.themeMiniHomeClassic]}>
        <Text style={styles.themeMiniDate}>SAT, 5 SEP</Text>
        <Text style={styles.themeMiniClock}>8:31</Text>
        <View style={styles.themeMiniPill}><Text style={styles.themeMiniPillText}>2 blocked • Ready</Text></View>
        <View style={styles.themeMiniCard}><Text style={styles.themeMiniCardHeading}>Current Task</Text><Text style={styles.themeMiniCardTitle}>Deep work</Text></View>
        <View style={styles.themeMiniCard}><Text style={styles.themeMiniCardHeading}>Today's Limits</Text><Text style={styles.themeMiniCardText}>YouTube · 12m used</Text></View>
        <View style={styles.themeMiniActions}>
          <View style={[styles.themeMiniCircle, !isGlassy && styles.themeMiniCircleClassic]}><Text style={styles.themeMiniDots}>•••</Text></View>
          <View style={[styles.themeMiniCircle, { backgroundColor: isGlassy ? '#6366F1' : '#1E1E1E' }]}><Text style={styles.themeMiniF}>F</Text></View>
        </View>
      </View>
    </TouchableOpacity>
  );
}

function SectionHeader({
  icon,
  title,
  description,
  theme,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  description: string;
  theme: ReturnType<typeof useTheme>['theme'];
}) {
  return (
    <View style={styles.sectionHeader}>
      <View style={styles.sectionHeaderRow}>
        <View style={[styles.sectionIcon, { backgroundColor: COLORS.primary + '18' }]}>
          <Ionicons name={icon} size={16} color={COLORS.primary} />
        </View>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>{title}</Text>
      </View>
      <Text style={[styles.sectionDesc, { color: theme.muted }]}>{description}</Text>
    </View>
  );
}

function AppList({
  apps,
  loading,
  checked,
  onToggle,
  theme,
  emptyText,
  badge,
}: {
  apps: InstalledApp[];
  loading: boolean;
  checked: Set<string>;
  onToggle: (packageName: string) => void;
  theme: ReturnType<typeof useTheme>['theme'];
  emptyText: string;
  badge: (app: InstalledApp) => 'blocked' | undefined;
}) {
  if (loading) {
    return (
      <View style={styles.loadingRow}>
        <ActivityIndicator size="small" color={COLORS.primary} />
        <Text style={[styles.loadingText, { color: theme.muted }]}>Loading installed apps…</Text>
      </View>
    );
  }
  if (apps.length === 0) {
    return <View style={styles.emptyRow}><Text style={[styles.emptyText, { color: theme.muted }]}>{emptyText}</Text></View>;
  }
  return (
    <>
      {apps.map((app) => (
        <AppToggleRow
          key={app.packageName}
          app={app}
          checked={checked.has(app.packageName)}
          onToggle={() => onToggle(app.packageName)}
          theme={theme}
          badge={badge(app)}
        />
      ))}
    </>
  );
}

function AppToggleRow({
  app,
  checked,
  onToggle,
  theme,
  badge,
}: {
  app: { packageName: string; appName: string; isIme: boolean; iconBase64?: string };
  checked: boolean;
  onToggle: () => void;
  theme: ReturnType<typeof useTheme>['theme'];
  badge?: 'blocked';
}) {
  return (
    <TouchableOpacity style={styles.appRow} onPress={onToggle} activeOpacity={0.7}>
      {app.iconBase64 ? (
        <Image source={{ uri: `data:image/png;base64,${app.iconBase64}` }} style={styles.appIcon} />
      ) : (
        <View style={[styles.appIconPlaceholder, { backgroundColor: COLORS.primary + '18' }]}>
          <Ionicons name="apps-outline" size={18} color={COLORS.primary} />
        </View>
      )}
      <View style={{ flex: 1, gap: 1 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: SPACING.xs }}>
          <Text style={[styles.appName, { color: theme.text }]} numberOfLines={1}>{app.appName}</Text>
          {badge === 'blocked' && (
            <View style={styles.blockedBadge}>
              <Text style={styles.blockedBadgeText}>blocked</Text>
            </View>
          )}
        </View>
        <Text style={[styles.appPkg, { color: theme.muted }]} numberOfLines={1}>{app.packageName}</Text>
      </View>
      <Switch
        value={checked}
        onValueChange={onToggle}
        trackColor={{ false: COLORS.border, true: COLORS.primary + '88' }}
        thumbColor={checked ? COLORS.primary : COLORS.muted}
      />
    </TouchableOpacity>
  );
}

function SwitchRow({
  label,
  description,
  value,
  onValueChange,
  theme,
  isLast = false,
}: {
  label: string;
  description: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
  theme: ReturnType<typeof useTheme>['theme'];
  isLast?: boolean;
}) {
  return (
    <View style={[styles.switchRow, !isLast && { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: theme.border }]}>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={[styles.switchLabel, { color: theme.text }]}>{label}</Text>
        <Text style={[styles.switchDesc, { color: theme.muted }]}>{description}</Text>
      </View>
      <Switch
        value={value}
        onValueChange={onValueChange}
        trackColor={{ false: COLORS.border, true: COLORS.primary + '88' }}
        thumbColor={value ? COLORS.primary : COLORS.muted}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  title: { fontSize: FONT.lg, fontWeight: '800' },
  subtitle: { fontSize: FONT.xs, marginTop: 2 },
  scroll: { flex: 1 },
  content: { padding: SPACING.lg, gap: SPACING.md },
  statusCard: { borderRadius: RADIUS.md, borderWidth: 1, padding: SPACING.md, gap: SPACING.sm },
  statusRow: { flexDirection: 'row', alignItems: 'flex-start', gap: SPACING.sm },
  statusIcon: { width: 44, height: 44, borderRadius: RADIUS.md, alignItems: 'center', justifyContent: 'center' },
  statusTitle: { fontSize: FONT.sm, fontWeight: '700' },
  statusDesc: { fontSize: FONT.xs, lineHeight: 17 },
  setDefaultBtn: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACING.xs,
    backgroundColor: COLORS.primary, borderRadius: RADIUS.md, paddingVertical: SPACING.sm + 2,
  },
  setDefaultBtnText: { color: '#fff', fontSize: FONT.sm, fontWeight: '700' },
  themeRow: { flexDirection: 'row', gap: SPACING.sm },
  themePreviewCard: {
    flex: 1,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: '#FFFFFF22',
    padding: SPACING.sm,
  },
  themePreviewCardActive: { borderColor: '#A5B4FC', borderWidth: 2 },
  themePreviewHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: SPACING.xs,
  },
  themePreviewLabel: { color: '#FFFFFF', fontSize: FONT.sm, fontWeight: '800' },
  themeMiniHome: {
    minHeight: 190,
    borderRadius: 14,
    backgroundColor: '#31426B',
    padding: 8,
    justifyContent: 'space-between',
  },
  themeMiniHomeClassic: { backgroundColor: '#0E0E0E' },
  themeMiniDate: { color: '#CBD5F5', fontSize: 7, textAlign: 'center' },
  themeMiniClock: { color: '#FFFFFF', fontSize: 24, fontWeight: '300', textAlign: 'center' },
  themeMiniPill: {
    alignSelf: 'center',
    backgroundColor: '#44FFFFFF',
    borderRadius: 9,
    paddingHorizontal: 5,
    paddingVertical: 3,
  },
  themeMiniPillText: { color: '#E8ECFF', fontSize: 6 },
  themeMiniCard: { backgroundColor: '#55FFFFFF', borderRadius: 8, padding: 6, gap: 2 },
  themeMiniCardHeading: { color: '#E5E7EB', fontSize: 6 },
  themeMiniCardTitle: { color: '#FFFFFF', fontSize: 8, fontWeight: '700' },
  themeMiniCardText: { color: '#D7DDF0', fontSize: 6 },
  themeMiniActions: { flexDirection: 'row', justifyContent: 'space-evenly' },
  themeMiniCircle: {
    width: 27, height: 27, borderRadius: 14, backgroundColor: '#44FFFFFF',
    alignItems: 'center', justifyContent: 'center',
  },
  themeMiniCircleClassic: { backgroundColor: '#1E1E1E' },
  themeMiniDots: { color: '#FFFFFF', fontSize: 7, letterSpacing: 1 },
  themeMiniF: { color: '#FFFFFF', fontSize: 12, fontWeight: '800' },
  previewCard: { borderRadius: RADIUS.md, borderWidth: 1, padding: SPACING.md, gap: SPACING.sm },
  previewHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  layoutTitle: { color: '#fff', fontSize: FONT.sm, fontWeight: '700' },
  previewSubtitle: { color: '#CBD5F5', fontSize: FONT.xs, marginTop: 2 },
  phoneFrame: {
    width: '72%', aspectRatio: 0.54, maxHeight: 370, alignSelf: 'center',
    overflow: 'hidden', borderRadius: 24, borderWidth: 5, borderColor: '#111827',
    backgroundColor: '#28365D', position: 'relative',
  },
  previewWallpaper: { ...StyleSheet.absoluteFillObject },
  previewWallpaperFallback: { ...StyleSheet.absoluteFillObject, backgroundColor: '#28365D' },
  previewScrim: { ...StyleSheet.absoluteFillObject, backgroundColor: '#55000000' },
  previewContent: { flex: 1, paddingHorizontal: SPACING.sm, paddingTop: SPACING.lg, paddingBottom: SPACING.sm, justifyContent: 'space-between' },
  previewDate: { color: '#D7DDF0', fontSize: 8, textAlign: 'center', letterSpacing: 0.8 },
  previewClock: { color: '#FFFFFF', fontSize: 30, fontWeight: '800', textAlign: 'center', marginTop: -SPACING.md },
  previewStatus: { alignSelf: 'center', backgroundColor: '#44FFFFFF', borderRadius: 14, paddingHorizontal: 8, paddingVertical: 4 },
  previewStatusText: { color: '#E8ECFF', fontSize: 8 },
  previewTask: { backgroundColor: '#55FFFFFF', borderRadius: 12, padding: 9 },
  previewTaskHeading: { color: '#EFF2FF', fontSize: 8 },
  previewTaskTitle: { color: '#fff', fontSize: 12, fontWeight: '700', marginTop: 4 },
  previewProgress: { height: 3, backgroundColor: '#D9E1F2', borderRadius: 2, marginTop: 7, width: '45%' },
  previewTaskMeta: { color: '#D7DDF0', fontSize: 7, marginTop: 5 },
  previewLimits: { backgroundColor: '#55FFFFFF', borderRadius: 12, padding: 9, gap: 4 },
  previewLimitsHeading: { color: '#EFF2FF', fontSize: 8, marginBottom: 2 },
  previewLimitRow: { color: '#D7DDF0', fontSize: 7 },
  previewActions: { flexDirection: 'row', justifyContent: 'space-around', alignItems: 'center' },
  previewActionCircle: { width: 34, height: 34, borderRadius: 17, backgroundColor: '#3B3B40', alignItems: 'center', justifyContent: 'center' },
  previewDots: { color: '#fff', letterSpacing: 2, fontSize: 10 },
  previewF: { color: '#fff', fontSize: 17, fontWeight: '800' },
  previewActionLabel: { color: '#E4E7F2', fontSize: 7, textAlign: 'center', marginTop: 4 },
  sectionHeader: { gap: 4, marginBottom: SPACING.xs },
  sectionHeaderRow: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm },
  sectionIcon: { width: 28, height: 28, borderRadius: RADIUS.sm, alignItems: 'center', justifyContent: 'center' },
  sectionTitle: { fontSize: FONT.md, fontWeight: '700' },
  sectionDesc: { fontSize: FONT.xs, lineHeight: 18, paddingLeft: 28 + SPACING.sm },
  card: { borderRadius: RADIUS.md, borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  settingRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: SPACING.md, paddingVertical: SPACING.sm + 2, gap: SPACING.sm },
  settingLabel: { fontSize: FONT.sm, fontWeight: '600' },
  settingDesc: { fontSize: FONT.xs, lineHeight: 17 },
  segmentControl: { flexDirection: 'row', borderRadius: RADIUS.sm, overflow: 'hidden', borderWidth: 1, borderColor: COLORS.border },
  segmentBtn: { paddingHorizontal: SPACING.sm, paddingVertical: SPACING.xs + 2 },
  segmentBtnActive: { backgroundColor: COLORS.primary },
  segmentText: { fontSize: FONT.xs, fontWeight: '600', color: COLORS.muted },
  segmentTextActive: { color: '#fff' },
  searchBox: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, borderRadius: RADIUS.md, borderWidth: 1, paddingHorizontal: SPACING.md, minHeight: 48 },
  searchInput: { flex: 1, fontSize: FONT.sm, paddingVertical: 0 },
  appRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: SPACING.md, paddingVertical: SPACING.sm, gap: SPACING.sm, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: COLORS.border },
  appIconPlaceholder: { width: 36, height: 36, borderRadius: RADIUS.sm, alignItems: 'center', justifyContent: 'center' },
  appIcon: { width: 36, height: 36, borderRadius: RADIUS.sm },
  appName: { fontSize: FONT.sm, fontWeight: '600' },
  appPkg: { fontSize: 11 },
  blockedBadge: { backgroundColor: COLORS.orange + '22', borderRadius: 4, paddingHorizontal: 5, paddingVertical: 1, borderWidth: 1, borderColor: COLORS.orange + '55' },
  blockedBadgeText: { fontSize: 9, color: COLORS.orange, fontWeight: '700' },
  switchRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: SPACING.md, paddingVertical: SPACING.sm, gap: SPACING.sm },
  switchLabel: { fontSize: FONT.sm, fontWeight: '600' },
  switchDesc: { fontSize: FONT.xs, lineHeight: 17 },
  loadingRow: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, padding: SPACING.md },
  loadingText: { fontSize: FONT.sm },
  emptyRow: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, padding: SPACING.md },
  emptyText: { fontSize: FONT.xs, flex: 1, lineHeight: 17 },
  tipCard: { flexDirection: 'row', alignItems: 'flex-start', gap: SPACING.sm, padding: SPACING.md, borderRadius: RADIUS.md, borderWidth: 1 },
  tipText: { flex: 1, fontSize: FONT.xs, lineHeight: 18 },
  lockedScreen: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: SPACING.xl },
  lockedCard: { width: '100%', maxWidth: 340, borderRadius: RADIUS.xl, borderWidth: 1, padding: SPACING.xl, alignItems: 'center', gap: SPACING.md },
  lockedIconRing: { width: 72, height: 72, borderRadius: 36, backgroundColor: COLORS.orange + '18', alignItems: 'center', justifyContent: 'center' },
  lockedHeading: { fontSize: FONT.xl, fontWeight: '800', textAlign: 'center' },
  lockedBody: { fontSize: FONT.sm, textAlign: 'center', lineHeight: 21 },
  lockedBackBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: COLORS.primary, paddingVertical: SPACING.sm + 2, paddingHorizontal: SPACING.lg, borderRadius: RADIUS.md },
  lockedBackText: { color: '#fff', fontSize: FONT.sm, fontWeight: '700' },
});