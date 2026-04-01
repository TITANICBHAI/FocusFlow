import React, { useEffect, useState, useMemo, useCallback } from 'react';
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
  ScrollView,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { InstalledAppsModule, type InstalledApp } from '@/native-modules/InstalledAppsModule';
import { COLORS, FONT, RADIUS, SPACING } from '@/styles/theme';
import type { AllowedAppPreset } from '@/data/types';

interface Props {
  visible: boolean;
  title?: string;
  // [] = "all apps allowed" sentinel (all apps pre-checked on open)
  // [...pkgs] = specific allowed list (only those pre-checked)
  initialSelected: string[];
  presets: AllowedAppPreset[];
  onSave: (packages: string[]) => void;
  onSavePreset: (preset: AllowedAppPreset) => void;
  onDeletePreset: (id: string) => void;
  onClose: () => void;
}

export function AppPickerSheet({
  visible,
  title = 'Allowed Apps',
  initialSelected,
  presets,
  onSave,
  onSavePreset,
  onDeletePreset,
  onClose,
}: Props) {
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPresetInput, setShowPresetInput] = useState(false);
  const [presetName, setPresetName] = useState('');

  useEffect(() => {
    if (!visible) {
      setShowPresetInput(false);
      setPresetName('');
      return;
    }
    setSearch('');
    setLoading(true);
    setApps([]);

    void (async () => {
      try {
        const result = await InstalledAppsModule.getInstalledApps();
        const sorted = result
          .slice()
          .sort((a, b) => a.appName.toLowerCase().localeCompare(b.appName.toLowerCase()));
        setApps(sorted);

        const allPkgs = new Set(sorted.map((a) => a.packageName));
        if (initialSelected.length === 0) {
          // [] sentinel → check ALL apps (all allowed by default)
          setSelected(new Set(allPkgs));
        } else {
          setSelected(new Set(initialSelected.filter((p) => allPkgs.has(p))));
        }
      } catch {
        setApps([]);
        setSelected(new Set(initialSelected));
      } finally {
        setLoading(false);
      }
    })();
  }, [visible]);

  const filtered = useMemo(() => {
    if (!search.trim()) return apps;
    const q = search.toLowerCase();
    return apps.filter(
      (a) =>
        a.appName.toLowerCase().includes(q) ||
        a.packageName.toLowerCase().includes(q),
    );
  }, [apps, search]);

  const toggle = useCallback((pkg: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(pkg)) next.delete(pkg);
      else next.add(pkg);
      return next;
    });
  }, []);

  const selectAll = useCallback(() => {
    setSelected(new Set(apps.map((a) => a.packageName)));
  }, [apps]);

  const deselectAll = useCallback(() => {
    setSelected(new Set());
  }, []);

  const applyPreset = useCallback(
    (preset: AllowedAppPreset) => {
      if (preset.packages.length === 0) {
        setSelected(new Set(apps.map((a) => a.packageName)));
      } else {
        setSelected(new Set(preset.packages));
      }
    },
    [apps],
  );

  const confirmDeletePreset = useCallback(
    (preset: AllowedAppPreset) => {
      Alert.alert('Delete Preset', `Delete "${preset.name}"?`, [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: () => onDeletePreset(preset.id) },
      ]);
    },
    [onDeletePreset],
  );

  const handleSavePreset = useCallback(() => {
    const name = presetName.trim();
    if (!name) return;
    const allChecked = apps.length > 0 && selected.size === apps.length;
    const packages = allChecked ? [] : Array.from(selected);
    onSavePreset({ id: Date.now().toString(), name, packages });
    setShowPresetInput(false);
    setPresetName('');
  }, [presetName, selected, apps, onSavePreset]);

  const handleSave = useCallback(() => {
    // If every app is checked → use [] sentinel (all allowed)
    const allChecked = apps.length > 0 && selected.size === apps.length;
    const packages = allChecked ? [] : Array.from(selected);
    onSave(packages);
    onClose();
  }, [selected, apps, onSave, onClose]);

  const allChecked = apps.length > 0 && selected.size === apps.length;

  const renderApp = ({ item }: { item: InstalledApp }) => {
    const checked = selected.has(item.packageName);
    return (
      <TouchableOpacity
        style={styles.row}
        onPress={() => toggle(item.packageName)}
        activeOpacity={0.7}
      >
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
          <Text style={styles.pkgName} numberOfLines={1}>{item.packageName}</Text>
        </View>
        <View style={[styles.checkbox, checked && styles.checkboxOn]}>
          {checked && <Ionicons name="checkmark" size={14} color="#fff" />}
        </View>
      </TouchableOpacity>
    );
  };

  const listHeader = (
    <>
      {/* Presets section */}
      <View style={styles.presetsSection}>
        <View style={styles.presetsTitleRow}>
          <Text style={styles.sectionLabel}>PRESETS</Text>
          {!showPresetInput && (
            <TouchableOpacity
              style={styles.savePresetBtn}
              onPress={() => setShowPresetInput(true)}
              activeOpacity={0.7}
            >
              <Ionicons name="bookmark-outline" size={13} color={COLORS.primary} />
              <Text style={styles.savePresetText}>Save current</Text>
            </TouchableOpacity>
          )}
        </View>

        {showPresetInput ? (
          <View style={styles.presetInputRow}>
            <TextInput
              style={styles.presetInput}
              placeholder="Preset name…"
              placeholderTextColor={COLORS.muted}
              value={presetName}
              onChangeText={setPresetName}
              autoFocus
              returnKeyType="done"
              onSubmitEditing={handleSavePreset}
              maxLength={32}
            />
            <TouchableOpacity
              style={[styles.presetInputBtn, !presetName.trim() && styles.presetInputBtnDim]}
              onPress={handleSavePreset}
              disabled={!presetName.trim()}
            >
              <Text style={styles.presetInputBtnText}>Save</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.presetInputCancel}
              onPress={() => { setShowPresetInput(false); setPresetName(''); }}
            >
              <Ionicons name="close" size={18} color={COLORS.muted} />
            </TouchableOpacity>
          </View>
        ) : presets.length === 0 ? (
          <Text style={styles.presetEmpty}>
            No presets yet — save the current selection as a named preset.
          </Text>
        ) : null}

        {presets.length > 0 && !showPresetInput && (
          <>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.presetsRow}
            >
              {presets.map((preset) => (
                <TouchableOpacity
                  key={preset.id}
                  style={styles.presetChip}
                  onPress={() => applyPreset(preset)}
                  onLongPress={() => confirmDeletePreset(preset)}
                  activeOpacity={0.7}
                >
                  <Ionicons
                    name="bookmark"
                    size={12}
                    color={COLORS.primary}
                    style={{ marginRight: 4 }}
                  />
                  <Text style={styles.presetChipText} numberOfLines={1}>
                    {preset.name}
                  </Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
            <Text style={styles.presetHint}>Tap to apply · Long-press to delete</Text>
          </>
        )}
      </View>

      {/* Select All / Deselect All + count */}
      <View style={styles.controlRow}>
        <Text style={styles.countText}>
          {allChecked
            ? `All ${apps.length} apps allowed`
            : `${selected.size} of ${apps.length} allowed`}
        </Text>
        <TouchableOpacity
          onPress={allChecked ? deselectAll : selectAll}
          style={styles.selectAllBtn}
        >
          <Text style={styles.selectAllText}>
            {allChecked ? 'Deselect All' : 'Select All'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Search bar */}
      <View style={styles.searchBar}>
        <Ionicons
          name="search"
          size={16}
          color={COLORS.muted}
          style={{ marginRight: SPACING.xs }}
        />
        <TextInput
          style={styles.searchInput}
          placeholder="Search by name or package…"
          placeholderTextColor={COLORS.muted}
          value={search}
          onChangeText={setSearch}
          autoCorrect={false}
          clearButtonMode="while-editing"
        />
      </View>

      <Text style={styles.hint}>Checked apps are allowed during Focus Mode</Text>

      {loading && (
        <View style={styles.loadingRow}>
          <ActivityIndicator size="small" color={COLORS.primary} />
          <Text style={styles.loadingText}>Loading installed apps…</Text>
        </View>
      )}
    </>
  );

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={onClose}
    >
      <SafeAreaView style={styles.container}>
        {/* Header */}
        <View style={styles.header}>
          <TouchableOpacity onPress={onClose} style={styles.headerBtn}>
            <Text style={styles.cancelText}>Cancel</Text>
          </TouchableOpacity>
          <Text style={styles.headerTitle}>{title}</Text>
          <TouchableOpacity onPress={handleSave} style={styles.headerBtn}>
            <Text style={styles.saveText}>Save</Text>
          </TouchableOpacity>
        </View>

        <FlatList
          data={filtered}
          keyExtractor={(item) => item.packageName}
          renderItem={renderApp}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          contentContainerStyle={styles.list}
          keyboardShouldPersistTaps="handled"
          ListHeaderComponent={listHeader}
          ListEmptyComponent={
            !loading ? (
              <View style={styles.empty}>
                <Text style={styles.emptyText}>
                  {search ? 'No apps match your search.' : 'No installed apps found.'}
                </Text>
              </View>
            ) : null
          }
        />
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
    paddingVertical: SPACING.sm,
    paddingHorizontal: SPACING.xs,
  },
  headerTitle: {
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
  list: {
    paddingHorizontal: SPACING.lg,
    paddingBottom: SPACING.xl,
  },
  presetsSection: {
    marginTop: SPACING.lg,
    marginBottom: SPACING.xs,
    gap: SPACING.sm,
  },
  presetsTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sectionLabel: {
    fontSize: FONT.xs,
    fontWeight: '700',
    color: COLORS.muted,
    letterSpacing: 0.8,
  },
  savePresetBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingVertical: 4,
    paddingHorizontal: SPACING.sm,
    borderRadius: RADIUS.full,
    backgroundColor: COLORS.primaryLight,
  },
  savePresetText: {
    fontSize: FONT.xs,
    fontWeight: '600',
    color: COLORS.primary,
  },
  presetInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
  },
  presetInput: {
    flex: 1,
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1.5,
    borderColor: COLORS.primary + '88',
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    fontSize: FONT.md,
    color: COLORS.text,
  },
  presetInputBtn: {
    backgroundColor: COLORS.primary,
    borderRadius: RADIUS.md,
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
  },
  presetInputBtnDim: {
    backgroundColor: COLORS.primaryLight,
  },
  presetInputBtnText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: FONT.sm,
  },
  presetInputCancel: {
    padding: SPACING.xs,
  },
  presetEmpty: {
    fontSize: FONT.xs,
    color: COLORS.muted,
    fontStyle: 'italic',
  },
  presetsRow: {
    gap: SPACING.sm,
    paddingVertical: 2,
  },
  presetChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.xs + 2,
    borderRadius: RADIUS.full,
    backgroundColor: COLORS.primaryLight,
    borderWidth: 1,
    borderColor: COLORS.primary + '44',
    maxWidth: 160,
  },
  presetChipText: {
    fontSize: FONT.sm,
    fontWeight: '600',
    color: COLORS.primary,
  },
  presetHint: {
    fontSize: FONT.xs,
    color: COLORS.muted,
    fontStyle: 'italic',
  },
  controlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: SPACING.md,
  },
  countText: {
    fontSize: FONT.sm,
    color: COLORS.textSecondary,
    fontWeight: '500',
  },
  selectAllBtn: {
    paddingVertical: SPACING.xs,
    paddingHorizontal: SPACING.sm,
    borderRadius: RADIUS.md,
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  selectAllText: {
    fontSize: FONT.sm,
    color: COLORS.primary,
    fontWeight: '600',
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    marginTop: SPACING.md,
    borderRadius: RADIUS.lg,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: COLORS.border,
    paddingHorizontal: SPACING.md,
    height: 42,
  },
  searchInput: {
    flex: 1,
    fontSize: FONT.md,
    color: COLORS.text,
    height: '100%',
  },
  hint: {
    fontSize: FONT.xs,
    color: COLORS.muted,
    marginTop: SPACING.sm,
    marginBottom: SPACING.xs,
  },
  loadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
    paddingVertical: SPACING.sm,
  },
  loadingText: {
    fontSize: FONT.sm,
    color: COLORS.muted,
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
  pkgName: {
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
  checkboxOn: {
    backgroundColor: COLORS.primary,
    borderColor: COLORS.primary,
  },
  separator: {
    height: SPACING.xs,
  },
  empty: {
    paddingTop: SPACING.xxl,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: FONT.sm,
    color: COLORS.muted,
  },
});
