import React, { useEffect, useState, useMemo } from 'react';
import {
  View,
  Text,
  Modal,
  FlatList,
  TextInput,
  TouchableOpacity,
  Image,
  ActivityIndicator,
  StyleSheet,
  SafeAreaView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { InstalledAppsModule, InstalledApp } from '@/native-modules/InstalledAppsModule';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';

interface Props {
  visible: boolean;
  allowedPackages: string[];
  onSave: (packages: string[]) => void | Promise<void>;
  onClose: () => void;
}

export function AllowedAppsModal({ visible, allowedPackages, onSave, onClose }: Props) {
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set(allowedPackages));
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!visible) return;
    setSelected(new Set(allowedPackages));
    setSearch('');
    void loadApps();
  }, [visible]);

  const loadApps = async () => {
    setLoading(true);
    try {
      const result = await InstalledAppsModule.getInstalledApps();
      const sorted = result.slice().sort((a, b) =>
        a.appName.toLowerCase().localeCompare(b.appName.toLowerCase())
      );
      setApps(sorted);
    } catch (e) {
      console.warn('[AllowedAppsModal] Failed to load apps', e);
    } finally {
      setLoading(false);
    }
  };

  const filtered = useMemo(() => {
    if (!search.trim()) return apps;
    const q = search.toLowerCase();
    return apps.filter(
      (a) =>
        a.appName.toLowerCase().includes(q) ||
        a.packageName.toLowerCase().includes(q)
    );
  }, [apps, search]);

  const toggle = (packageName: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(packageName)) {
        next.delete(packageName);
      } else {
        next.add(packageName);
      }
      return next;
    });
  };

  const handleSave = async () => {
    try {
      await onSave(Array.from(selected));
      onClose();
    } catch (e) {
      console.error('[AllowedAppsModal] Failed to save allowed apps', e);
    }
  };

  const renderItem = ({ item }: { item: InstalledApp }) => {
    const checked = selected.has(item.packageName);
    return (
      <TouchableOpacity style={styles.row} onPress={() => toggle(item.packageName)} activeOpacity={0.7}>
        {item.iconBase64 ? (
          <Image
            source={{ uri: `data:image/png;base64,${item.iconBase64}` }}
            style={styles.icon}
          />
        ) : (
          <View style={styles.iconPlaceholder}>
            <Ionicons name="apps-outline" size={22} color={COLORS.muted} />
          </View>
        )}
        <View style={styles.appInfo}>
          <Text style={styles.appName} numberOfLines={1}>{item.appName}</Text>
          <Text style={styles.packageName} numberOfLines={1}>{item.packageName}</Text>
        </View>
        <View style={[styles.checkbox, checked && styles.checkboxChecked]}>
          {checked && <Ionicons name="checkmark" size={14} color="#fff" />}
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <TouchableOpacity onPress={onClose} style={styles.headerBtn}>
            <Text style={styles.cancelText}>Cancel</Text>
          </TouchableOpacity>
          <Text style={styles.title}>Allowed Apps</Text>
          <TouchableOpacity onPress={handleSave} style={styles.headerBtn}>
            <Text style={styles.saveText}>Save</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.searchContainer}>
          <Ionicons name="search" size={16} color={COLORS.muted} style={styles.searchIcon} />
          <TextInput
            style={styles.searchInput}
            placeholder="Search apps…"
            placeholderTextColor={COLORS.muted}
            value={search}
            onChangeText={setSearch}
            autoCorrect={false}
            clearButtonMode="while-editing"
          />
        </View>

        <Text style={styles.hint}>
          {selected.size} app{selected.size !== 1 ? 's' : ''} allowed — these won't be blocked during Focus Mode
        </Text>

        {loading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color={COLORS.primary} />
            <Text style={styles.loadingText}>Loading installed apps…</Text>
          </View>
        ) : (
          <FlatList
            data={filtered}
            keyExtractor={(item) => item.packageName}
            renderItem={renderItem}
            ItemSeparatorComponent={() => <View style={styles.separator} />}
            contentContainerStyle={styles.list}
            keyboardShouldPersistTaps="handled"
            ListEmptyComponent={
              <View style={styles.emptyContainer}>
                <Text style={styles.emptyText}>
                  {search ? 'No apps match your search.' : 'No user-installed apps found.'}
                </Text>
              </View>
            }
          />
        )}
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.md,
    backgroundColor: COLORS.card,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: COLORS.border,
  },
  headerBtn: {
    minWidth: 60,
  },
  title: {
    fontSize: FONT.md,
    fontWeight: '700',
    color: COLORS.text,
  },
  cancelText: {
    fontSize: FONT.md,
    color: COLORS.muted,
  },
  saveText: {
    fontSize: FONT.md,
    fontWeight: '700',
    color: COLORS.primary,
    textAlign: 'right',
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    marginHorizontal: SPACING.lg,
    marginVertical: SPACING.sm,
    borderRadius: RADIUS.lg,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: COLORS.border,
    paddingHorizontal: SPACING.md,
  },
  searchIcon: {
    marginRight: SPACING.xs,
  },
  searchInput: {
    flex: 1,
    height: 40,
    fontSize: FONT.md,
    color: COLORS.text,
  },
  hint: {
    fontSize: FONT.xs,
    color: COLORS.muted,
    paddingHorizontal: SPACING.lg,
    paddingBottom: SPACING.sm,
  },
  list: {
    paddingHorizontal: SPACING.lg,
    paddingBottom: SPACING.xl,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    gap: SPACING.sm,
  },
  icon: {
    width: 40,
    height: 40,
    borderRadius: RADIUS.sm,
  },
  iconPlaceholder: {
    width: 40,
    height: 40,
    borderRadius: RADIUS.sm,
    backgroundColor: COLORS.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  appInfo: {
    flex: 1,
  },
  appName: {
    fontSize: FONT.md,
    fontWeight: '600',
    color: COLORS.text,
  },
  packageName: {
    fontSize: FONT.xs,
    color: COLORS.muted,
    marginTop: 2,
  },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: RADIUS.sm,
    borderWidth: 2,
    borderColor: COLORS.border,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: COLORS.surface,
  },
  checkboxChecked: {
    backgroundColor: COLORS.primary,
    borderColor: COLORS.primary,
  },
  separator: {
    height: SPACING.xs,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.md,
  },
  loadingText: {
    fontSize: FONT.sm,
    color: COLORS.muted,
  },
  emptyContainer: {
    paddingTop: SPACING.xxl,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: FONT.sm,
    color: COLORS.muted,
  },
});
