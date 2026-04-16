/**
 * LauncherSetupModal
 *
 * Lets the user enable the FocusFlow launcher and choose which apps
 * appear in its scrollable grid.  The four permanent dock apps
 * (Phone, WhatsApp, VLC, Settings) are always shown by the launcher
 * and are not listed here.
 *
 * When enabled, the user must set FocusFlow as the default home app
 * through their Android system settings — we open the relevant screen
 * with a deep-link Intent.
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Image,
  Linking,
  Modal,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import { useTheme } from '@/hooks/useTheme';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import { InstalledAppsModule, type InstalledApp as NativeInstalledApp } from '@/native-modules/InstalledAppsModule';

// ─── Types ────────────────────────────────────────────────────────────────────

interface InstalledApp {
  packageName: string;
  appName: string;
  icon?: string;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

// Packages permanently pinned to the dock — always shown, cannot be removed.
const PINNED_PKGS = new Set([
  // ── Phone / Dialer (all major brands) ─────────────────────────────────────
  'com.google.android.dialer',        // Pixel / stock Android
  'com.android.dialer',               // AOSP (Nokia, Sony, LG, etc.)
  'com.samsung.android.app.telephonyui', // Samsung One UI
  'com.miui.dialer',                  // Xiaomi / MIUI
  'com.oneplus.dialer',               // OnePlus OxygenOS
  'com.coloros.dialer',               // Oppo / Realme ColorOS
  'com.vivo.phone',                   // Vivo
  'com.motorola.dialer',              // Motorola
  'com.huawei.contacts',              // Huawei / Honor
  'com.asus.contacts',                // Asus ROG / Zenfone
  'com.transsion.contacts',           // Tecno / Infinix / Itel

  // ── WhatsApp ──────────────────────────────────────────────────────────────
  'com.whatsapp',
  'com.whatsapp.w4b',                 // WhatsApp Business

  // ── VLC ───────────────────────────────────────────────────────────────────
  'org.videolan.vlc',
  'org.videolan.vlc.betav',

  // ── System Settings (all major brands) ────────────────────────────────────
  'com.android.settings',
  'com.samsung.android.app.settings',
  'com.miui.settings',
  'com.coloros.settings',             // Oppo / Realme
  'com.vivo.settings',
  'com.huawei.systemmanager',
  'com.asus.settings',

  // ── Education: Allen ─────────────────────────────────────────────────────
  'in.allen.online',
  'in.allen.parent',

  // ── Education: Physics Wallah (PW) ────────────────────────────────────────
  'com.physicswallah.live',
  'com.pw.live',

  // ── Education: Gurukripa / GCI ────────────────────────────────────────────
  'in.schoollog.testseries.gcisikar', // Gurukripa e-Learning (student app)
  'com.gurukripa.publicapp',          // Gurukripa Parent App
]);

// ─── Component ────────────────────────────────────────────────────────────────

interface Props {
  visible: boolean;
  onClose: () => void;
  currentLauncherApps: string[];
  onSave: (packages: string[]) => void;
}

export function LauncherSetupModal({ visible, onClose, currentLauncherApps, onSave }: Props) {
  const { theme } = useTheme();

  const [loading, setLoading]         = useState(false);
  const [apps, setApps]               = useState<InstalledApp[]>([]);
  const [selected, setSelected]       = useState<Set<string>>(new Set(currentLauncherApps));
  const [saving, setSaving]           = useState(false);

  // Reset selection when modal opens
  useEffect(() => {
    if (visible) {
      setSelected(new Set(currentLauncherApps));
      if (Platform.OS === 'android') loadInstalledApps();
    }
  }, [visible]);

  const loadInstalledApps = async () => {
    setLoading(true);
    try {
      const nativeApps: NativeInstalledApp[] = await InstalledAppsModule.getInstalledApps();
      if (nativeApps.length > 0) {
        // Filter out pinned packages — they're always in the dock and can't be toggled
        const filtered = nativeApps
          .filter((a) => !PINNED_PKGS.has(a.packageName))
          .map((a) => ({
            packageName: a.packageName,
            appName: a.appName,
            icon: a.iconBase64 ? `data:image/png;base64,${a.iconBase64}` : undefined,
          }))
          .sort((a, b) => a.appName.localeCompare(b.appName));
        setApps(filtered);
      } else {
        // Expo Go / simulator: native module unavailable — show curated fallback
        setApps(POPULAR_APPS_FALLBACK);
      }
    } catch (_) {
      setApps(POPULAR_APPS_FALLBACK);
    } finally {
      setLoading(false);
    }
  };

  const toggleApp = useCallback((pkg: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(pkg)) next.delete(pkg);
      else next.add(pkg);
      return next;
    });
  }, []);

  const handleSave = async () => {
    setSaving(true);
    try {
      const pkgs = [...selected];
      await SharedPrefsModule.setLauncherApps(pkgs);
      onSave(pkgs);
      onClose();
    } catch (e) {
      Alert.alert('Error', 'Could not save launcher apps. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const handleSetDefault = () => {
    // Go directly to Android "Default apps → Home app" screen.
    // No intermediate alert — same one-tap behaviour as Accessibility / Battery permission setup.
    Linking.sendIntent('android.settings.HOME_SETTINGS').catch(() =>
      Linking.openSettings()
    );
  };

  const renderApp = ({ item }: { item: InstalledApp }) => {
    const isSelected  = selected.has(item.packageName);
    const isPinned    = PINNED_PKGS.has(item.packageName);
    if (isPinned) return null;

    return (
      <TouchableOpacity
        style={[
          styles.appRow,
          {
            backgroundColor: theme.surface,
            borderColor: isSelected ? COLORS.primary : theme.border,
            borderWidth: isSelected ? 1.5 : 1,
          },
        ]}
        onPress={() => toggleApp(item.packageName)}
        activeOpacity={0.75}
      >
        <View style={styles.appIcon}>
          {item.icon
            ? <Image source={{ uri: item.icon }} style={styles.iconImg} />
            : <View style={[styles.iconPlaceholder, { backgroundColor: theme.border }]}>
                <Text style={[styles.iconLetter, { color: theme.text }]}>
                  {item.appName[0]?.toUpperCase() ?? '?'}
                </Text>
              </View>
          }
        </View>

        <View style={styles.appMeta}>
          <Text style={[styles.appName, { color: theme.text }]} numberOfLines={1}>
            {item.appName}
          </Text>
          <Text style={[styles.appPkg, { color: theme.textMuted }]} numberOfLines={1}>
            {item.packageName}
          </Text>
        </View>

        <View style={[
          styles.checkCircle,
          {
            backgroundColor: isSelected ? COLORS.primary : 'transparent',
            borderColor: isSelected ? COLORS.primary : theme.border,
          }
        ]}>
          {isSelected && <Ionicons name="checkmark" size={14} color="#fff" />}
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={[styles.root, { backgroundColor: theme.background }]}>

        {/* ── Header ── */}
        <View style={[styles.header, { borderBottomColor: theme.border, backgroundColor: theme.card }]}>
          <TouchableOpacity onPress={onClose} hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}>
            <Text style={[styles.cancel, { color: COLORS.primary }]}>Cancel</Text>
          </TouchableOpacity>
          <Text style={[styles.title, { color: theme.text }]}>Launcher Apps</Text>
          <TouchableOpacity onPress={handleSave} disabled={saving} hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}>
            {saving
              ? <ActivityIndicator size="small" color={COLORS.primary} />
              : <Text style={[styles.save, { color: COLORS.primary }]}>Save</Text>
            }
          </TouchableOpacity>
        </View>

        {/* ── Info banner ── */}
        <View style={[styles.banner, { backgroundColor: COLORS.primary + '18', borderColor: COLORS.primary + '44' }]}>
          <Ionicons name="home-outline" size={18} color={COLORS.primary} style={{ marginRight: SPACING.sm }} />
          <Text style={[styles.bannerText, { color: theme.text }]}>
            During a block session the launcher shows ONLY the apps you pick here, plus the permanent dock (Phone, WhatsApp, VLC, Settings, Allen, PW, Gurukripa). You can add any of your other installed apps to the grid below.
          </Text>
        </View>

        {/* ── Set as default button ── */}
        <TouchableOpacity
          style={[styles.defaultBtn, { backgroundColor: COLORS.primary, marginHorizontal: SPACING.md, marginTop: SPACING.sm }]}
          onPress={handleSetDefault}
        >
          <Ionicons name="settings-outline" size={16} color="#fff" style={{ marginRight: SPACING.xs }} />
          <Text style={styles.defaultBtnText}>Set FocusFlow as Default Launcher</Text>
        </TouchableOpacity>

        {/* ── Permanent dock preview ── */}
        <View style={[styles.pinnedSection, { borderColor: theme.border }]}>
          <Text style={[styles.pinnedLabel, { color: theme.textMuted }]}>ALWAYS IN DOCK (permanent)</Text>
          <View style={styles.pinnedRow}>
            {PINNED_DISPLAY.map((item) => (
              <View key={item.label} style={styles.pinnedItem}>
                <View style={[styles.pinnedIcon, { backgroundColor: item.color }]}>
                  <Ionicons name={item.icon as any} size={20} color="#fff" />
                </View>
                <Text style={[styles.pinnedName, { color: theme.textMuted }]}>{item.label}</Text>
              </View>
            ))}
          </View>
        </View>

        <Text style={[styles.sectionLabel, { color: theme.textMuted }]}>SELECT GRID APPS</Text>

        {/* ── App list ── */}
        {loading
          ? <ActivityIndicator size="large" color={COLORS.primary} style={{ marginTop: 40 }} />
          : <FlatList
              data={apps}
              keyExtractor={(item) => item.packageName}
              renderItem={renderApp}
              contentContainerStyle={{ paddingHorizontal: SPACING.md, paddingBottom: 40 }}
              showsVerticalScrollIndicator={false}
              ListEmptyComponent={
                <View style={styles.emptyContainer}>
                  <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                    The launcher's built-in app picker handles app selection natively.
                    Apps you pick there are saved automatically.
                  </Text>
                  <Text style={[styles.emptyNote, { color: theme.textMuted }]}>
                    This screen pre-seeds the list before you first open the launcher.
                  </Text>
                </View>
              }
            />
        }
      </View>
    </Modal>
  );
}

// ─── Static data ──────────────────────────────────────────────────────────────

const PINNED_DISPLAY = [
  { label: 'Phone',    icon: 'call-outline',       color: '#22C55E' },
  { label: 'WhatsApp', icon: 'chatbubble-outline', color: '#25D366' },
  { label: 'VLC',      icon: 'play-outline',       color: '#FF8800' },
  { label: 'Settings', icon: 'settings-outline',   color: '#6B7280' },
  { label: 'Allen',    icon: 'school-outline',      color: '#E11D48' },
  { label: 'PW',       icon: 'flask-outline',       color: '#7C3AED' },
  { label: 'GCI',      icon: 'library-outline',     color: '#0EA5E9' },
];

// Curated list of common apps shown in the picker while in Expo Go / before
// a real device app enumeration is available.
const POPULAR_APPS_FALLBACK: InstalledApp[] = [
  { packageName: 'com.google.android.gm',          appName: 'Gmail' },
  { packageName: 'com.google.android.apps.maps',   appName: 'Google Maps' },
  { packageName: 'com.google.android.youtube',     appName: 'YouTube' },
  { packageName: 'com.spotify.music',              appName: 'Spotify' },
  { packageName: 'com.netflix.mediaclient',        appName: 'Netflix' },
  { packageName: 'com.amazon.mShop.android.shopping', appName: 'Amazon' },
  { packageName: 'com.google.android.apps.photos', appName: 'Google Photos' },
  { packageName: 'com.discord',                    appName: 'Discord' },
  { packageName: 'com.instagram.android',          appName: 'Instagram' },
  { packageName: 'org.telegram.messenger',         appName: 'Telegram' },
  { packageName: 'com.twitter.android',            appName: 'X (Twitter)' },
  { packageName: 'com.reddit.frontpage',           appName: 'Reddit' },
  { packageName: 'com.zhiliaoapp.musically',       appName: 'TikTok' },
  { packageName: 'com.snapchat.android',           appName: 'Snapchat' },
  { packageName: 'com.linkedin.android',           appName: 'LinkedIn' },
  { packageName: 'com.facebook.katana',            appName: 'Facebook' },
  { packageName: 'com.google.android.apps.docs',   appName: 'Google Docs' },
  { packageName: 'com.google.android.apps.spreadsheets', appName: 'Google Sheets' },
  { packageName: 'com.microsoft.office.outlook',   appName: 'Outlook' },
  { packageName: 'com.microsoft.teams',            appName: 'Teams' },
  { packageName: 'com.slack',                      appName: 'Slack' },
  { packageName: 'com.notion.id',                  appName: 'Notion' },
  { packageName: 'com.todoist.android.Todoist',    appName: 'Todoist' },
  { packageName: 'com.google.android.keep',        appName: 'Google Keep' },
  { packageName: 'com.amazon.kindle',              appName: 'Kindle' },
  { packageName: 'com.duolingo',                   appName: 'Duolingo' },
];

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm + 4,
    borderBottomWidth: 1,
  },
  cancel: {
    fontSize: FONT.sm,
    fontWeight: '400',
    minWidth: 60,
  },
  title: {
    fontSize: FONT.md,
    fontWeight: '600',
  },
  save: {
    fontSize: FONT.sm,
    fontWeight: '600',
    minWidth: 60,
    textAlign: 'right',
  },
  banner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginHorizontal: SPACING.md,
    marginTop: SPACING.md,
    padding: SPACING.sm,
    borderRadius: RADIUS.md,
    borderWidth: 1,
  },
  bannerText: {
    fontSize: FONT.xs,
    flex: 1,
    lineHeight: 18,
  },
  defaultBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: SPACING.sm + 2,
    borderRadius: RADIUS.md,
    marginBottom: SPACING.sm,
  },
  defaultBtnText: {
    color: '#fff',
    fontSize: FONT.sm,
    fontWeight: '600',
  },
  pinnedSection: {
    marginHorizontal: SPACING.md,
    marginTop: SPACING.sm,
    padding: SPACING.sm,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    marginBottom: SPACING.xs,
  },
  pinnedLabel: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.8,
    marginBottom: SPACING.sm,
  },
  pinnedRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: SPACING.sm,
    justifyContent: 'flex-start',
  },
  pinnedItem: {
    alignItems: 'center',
    gap: 4,
  },
  pinnedIcon: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pinnedName: {
    fontSize: 10,
    fontWeight: '500',
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.8,
    marginHorizontal: SPACING.md + SPACING.xs,
    marginTop: SPACING.md,
    marginBottom: SPACING.xs,
  },
  appRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: SPACING.sm,
    borderRadius: RADIUS.md,
    marginBottom: SPACING.xs,
    gap: SPACING.sm,
  },
  appIcon: {
    width: 42,
    height: 42,
    borderRadius: 10,
    overflow: 'hidden',
  },
  iconImg: {
    width: 42,
    height: 42,
  },
  iconPlaceholder: {
    width: 42,
    height: 42,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconLetter: {
    fontSize: 20,
    fontWeight: '700',
  },
  appMeta: {
    flex: 1,
    gap: 2,
  },
  appName: {
    fontSize: FONT.sm,
    fontWeight: '500',
  },
  appPkg: {
    fontSize: 10,
  },
  checkCircle: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingTop: 32,
    paddingHorizontal: SPACING.md,
    gap: SPACING.sm,
  },
  emptyText: {
    fontSize: FONT.sm,
    textAlign: 'center',
    lineHeight: 20,
  },
  emptyNote: {
    fontSize: FONT.xs,
    textAlign: 'center',
    lineHeight: 18,
    fontStyle: 'italic',
  },
});
