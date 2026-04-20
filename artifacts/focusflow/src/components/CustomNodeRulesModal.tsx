/**
 * CustomNodeRulesModal
 *
 * Lets the user import NodeSpyCaptureV1 JSON exports and manage the resulting
 * custom node-blocking rules. Rules are stored in AppSettings.customNodeRules
 * and pushed to native SharedPreferences via SharedPrefsModule.setCustomNodeRules().
 *
 * Usage: block a specific UI node (e.g. the Shorts tab in YouTube) without
 * blocking the whole app — surgical blocking powered by NodeSpy captures.
 */

import React, { useState, useCallback } from 'react';
import {
  Modal,
  View,
  Text,
  TextInput,
  ScrollView,
  TouchableOpacity,
  Switch,
  Alert,
  StyleSheet,
  Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { CustomNodeRule } from '@/data/types';

// ─── Palette (matches FocusFlow dark-mode colours) ────────────────────────────
const C = {
  bg:        '#0F0F14',
  surface:   '#1A1A24',
  surfaceVar:'#242430',
  border:    '#2E2E3E',
  text:      '#E8E8F0',
  muted:     '#6B6B80',
  primary:   '#6366f1',
  green:     '#22c55e',
  red:       '#ef4444',
  orange:    '#f97316',
  yellow:    '#eab308',
};

// ─── NodeSpyCaptureV1 minimal shape ───────────────────────────────────────────
interface NodeSpyCaptureV1 {
  format: string;
  version: number;
  timestamp: number;
  pkg: string;
  nodes: Array<{
    id: string;
    cls: string;
    resId?: string;
    text?: string;
    desc?: string;
    flags: { visible: boolean };
    depth: number;
  }>;
  pinnedNodeIds: string[];
  ruleQuality?: {
    totalPinned: number;
    exportableRules: number;
    strongRules: number;
    mediumRules: number;
    weakRules: number;
    averageConfidence: number;
    warnings?: string[];
  };
  recommendedRules?: Array<{
    nodeId: string;
    label: string;
    pkg: string;
    selector: {
      matchResId?: string;
      matchText?: string;
      matchCls?: string;
    };
    selectorType?: string;
    confidence?: number;
    tier?: 'strong' | 'medium' | 'weak';
    stability?: number;
    warnings?: string[];
  }>;
}

// ─── Props ────────────────────────────────────────────────────────────────────
interface Props {
  visible: boolean;
  rules: CustomNodeRule[];
  onClose: () => void;
  onSave: (rules: CustomNodeRule[]) => void;
}

// ─── Component ────────────────────────────────────────────────────────────────
export default function CustomNodeRulesModal({ visible, rules, onClose, onSave }: Props) {
  const [tab, setTab] = useState<'rules' | 'import'>('rules');
  const [importJson, setImportJson] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const [importPreview, setImportPreview] = useState<NodeSpyCaptureV1 | null>(null);
  const [selectedAction, setSelectedAction] = useState<'overlay' | 'home'>('overlay');

  const handleJsonChange = useCallback((text: string) => {
    setImportJson(text);
    setImportError(null);
    setImportPreview(null);
    if (!text.trim()) return;
    try {
      const parsed = JSON.parse(text) as NodeSpyCaptureV1;
      if (parsed.format !== 'NodeSpyCaptureV1') {
        setImportError('Not a valid NodeSpyCaptureV1 export. Copy the JSON directly from NodeSpy.');
        return;
      }
      if (!parsed.pkg || !Array.isArray(parsed.nodes) || !Array.isArray(parsed.pinnedNodeIds)) {
        setImportError('Malformed export — missing required fields (pkg, nodes, pinnedNodeIds).');
        return;
      }
      setImportPreview(parsed);
    } catch {
      setImportError('Invalid JSON — paste the full NodeSpy export.');
    }
  }, []);

  const handleImport = useCallback(() => {
    if (!importPreview) return;
    const { pkg, nodes, pinnedNodeIds, timestamp, recommendedRules } = importPreview;

    if (pinnedNodeIds.length === 0) {
      setImportError('No pinned nodes in this export. Pin at least one node in NodeSpy before exporting.');
      return;
    }

    const now = new Date().toISOString();
    const hasRecommendedRulesField = Array.isArray(recommendedRules);
    const recommended = hasRecommendedRulesField ? recommendedRules : null;
    const newRules: CustomNodeRule[] = recommended
      ? recommended.map(rec => ({
          id: `cnr_${Date.now()}_${rec.nodeId}`,
          label: rec.label || rec.selector.matchResId?.split('/').pop() || rec.nodeId,
          pkg: rec.pkg || pkg,
          matchResId: rec.selector.matchResId || undefined,
          matchText: rec.selector.matchText || undefined,
          matchCls: rec.selector.matchCls || undefined,
          action: selectedAction,
          enabled: true,
          confidence: rec.confidence,
          qualityTier: rec.tier,
          selectorType: rec.selectorType,
          stability: rec.stability,
          warnings: rec.warnings,
          importedAt: now,
          captureTimestamp: timestamp,
        } as CustomNodeRule))
      : pinnedNodeIds
          .map(id => nodes.find(n => n.id === id))
          .filter(Boolean)
          .map(n => n!)
          .map(node => {
            const label =
              node.text?.slice(0, 40) ||
              node.desc?.slice(0, 40) ||
              node.resId?.split('/').pop()?.slice(0, 40) ||
              node.cls.split('.').pop() ||
              node.id;

            return {
              id: `cnr_${Date.now()}_${node.id}`,
              label,
              pkg,
              matchResId: node.resId || undefined,
              matchText: (node.text || node.desc) ? (node.text || node.desc)?.slice(0, 100) : undefined,
              matchCls: undefined,
              action: selectedAction,
              enabled: true,
              importedAt: now,
              captureTimestamp: timestamp,
            } as CustomNodeRule;
          });

    if (hasRecommendedRulesField && newRules.length === 0) {
      setImportError('NodeSpy did not find any high-confidence rules in this export. Capture another app state or pin nodes with a resource ID/text label.');
      return;
    }

    const merged = dedupeRules([...rules, ...newRules]);
    onSave(merged);
    setImportJson('');
    setImportPreview(null);
    setImportError(null);
    setTab('rules');
  }, [importPreview, rules, selectedAction, onSave]);

  const toggleRule = useCallback((id: string) => {
    onSave(rules.map(r => r.id === id ? { ...r, enabled: !r.enabled } : r));
  }, [rules, onSave]);

  const deleteRule = useCallback((id: string) => {
    Alert.alert('Delete Rule', 'Remove this blocking rule?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => onSave(rules.filter(r => r.id !== id)) },
    ]);
  }, [rules, onSave]);

  const clearAll = useCallback(() => {
    Alert.alert('Clear All Rules', 'Remove all custom node rules?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Clear All', style: 'destructive', onPress: () => onSave([]) },
    ]);
  }, [onSave]);

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={s.root}>
        {/* Header */}
        <View style={s.header}>
          <TouchableOpacity onPress={onClose} style={s.closeBtn}>
            <Ionicons name="close" size={22} color={C.muted} />
          </TouchableOpacity>
          <Text style={s.title}>Custom Node Rules</Text>
          {rules.length > 0 && (
            <TouchableOpacity onPress={clearAll} style={s.clearBtn}>
              <Ionicons name="trash-outline" size={18} color={C.red} />
            </TouchableOpacity>
          )}
        </View>

        {/* Subtitle */}
        <Text style={s.subtitle}>
          Import NodeSpy captures to block specific in-app UI elements — without blocking the whole app.
        </Text>

        {/* Tab bar */}
        <View style={s.tabBar}>
          <TouchableOpacity
            style={[s.tab, tab === 'rules' && s.tabActive]}
            onPress={() => setTab('rules')}
          >
            <Text style={[s.tabText, tab === 'rules' && s.tabTextActive]}>
              Rules ({rules.length})
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[s.tab, tab === 'import' && s.tabActive]}
            onPress={() => setTab('import')}
          >
            <Text style={[s.tabText, tab === 'import' && s.tabTextActive]}>
              Import from NodeSpy
            </Text>
          </TouchableOpacity>
        </View>

        {/* Content */}
        {tab === 'rules' ? (
          <RulesTab
            rules={rules}
            onToggle={toggleRule}
            onDelete={deleteRule}
            onImport={() => setTab('import')}
          />
        ) : (
          <ImportTab
            json={importJson}
            error={importError}
            preview={importPreview}
            action={selectedAction}
            onJsonChange={handleJsonChange}
            onActionChange={setSelectedAction}
            onImport={handleImport}
          />
        )}
      </View>
    </Modal>
  );
}

// ─── Rules tab ────────────────────────────────────────────────────────────────
function RulesTab({
  rules,
  onToggle,
  onDelete,
  onImport,
}: {
  rules: CustomNodeRule[];
  onToggle: (id: string) => void;
  onDelete: (id: string) => void;
  onImport: () => void;
}) {
  if (rules.length === 0) {
    return (
      <View style={s.emptyState}>
        <Ionicons name="scan-outline" size={52} color={C.muted} />
        <Text style={s.emptyTitle}>No rules yet</Text>
        <Text style={s.emptyBody}>
          Capture a node tree with NodeSpy, pin the elements you want to block, export the JSON, then import it here.
        </Text>
        <TouchableOpacity style={s.importBtn} onPress={onImport}>
          <Ionicons name="download-outline" size={18} color="#fff" />
          <Text style={s.importBtnText}>Import from NodeSpy</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={s.list}>
      {rules.map(rule => (
        <RuleRow key={rule.id} rule={rule} onToggle={onToggle} onDelete={onDelete} />
      ))}
      <TouchableOpacity style={[s.importBtn, { marginTop: 16 }]} onPress={onImport}>
        <Ionicons name="add-outline" size={18} color="#fff" />
        <Text style={s.importBtnText}>Import More Rules</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

function RuleRow({
  rule,
  onToggle,
  onDelete,
}: {
  rule: CustomNodeRule;
  onToggle: (id: string) => void;
  onDelete: (id: string) => void;
}) {
  return (
    <View style={s.ruleCard}>
      <View style={s.ruleHeader}>
        <View style={[s.actionBadge, { backgroundColor: rule.action === 'overlay' ? C.primary + '33' : C.orange + '33' }]}>
          <Text style={[s.actionBadgeText, { color: rule.action === 'overlay' ? C.primary : C.orange }]}>
            {rule.action === 'overlay' ? 'OVERLAY' : 'HOME'}
          </Text>
        </View>
        <Text style={s.ruleLabel} numberOfLines={1}>{rule.label}</Text>
        {typeof rule.confidence === 'number' && (
          <View style={[s.qualityBadge, { backgroundColor: qualityColor(rule.qualityTier) + '22' }]}>
            <Text style={[s.qualityBadgeText, { color: qualityColor(rule.qualityTier) }]}>
              {rule.confidence} {rule.qualityTier?.toUpperCase() ?? 'SCORE'}
            </Text>
          </View>
        )}
      </View>
      <Text style={s.rulePkg} numberOfLines={1}>{rule.pkg}</Text>
      <View style={s.ruleSelectors}>
        {rule.matchResId && <SelectorChip label="id" value={rule.matchResId.split('/').pop() || rule.matchResId} />}
        {rule.matchText   && <SelectorChip label="text" value={rule.matchText} />}
        {rule.matchCls    && <SelectorChip label="class" value={rule.matchCls} />}
      </View>
      {rule.warnings?.[0] && (
        <Text style={s.ruleWarning} numberOfLines={2}>{rule.warnings[0]}</Text>
      )}
      <View style={s.ruleFooter}>
        <Text style={s.ruleDate}>{new Date(rule.importedAt).toLocaleDateString()}</Text>
        <View style={s.ruleActions}>
          <Switch
            value={rule.enabled}
            onValueChange={() => onToggle(rule.id)}
            trackColor={{ false: C.border, true: C.primary + '88' }}
            thumbColor={rule.enabled ? C.primary : C.muted}
          />
          <TouchableOpacity onPress={() => onDelete(rule.id)} style={s.deleteBtn}>
            <Ionicons name="trash-outline" size={16} color={C.red} />
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

function SelectorChip({ label, value }: { label: string; value: string }) {
  return (
    <View style={s.chip}>
      <Text style={s.chipLabel}>{label}:</Text>
      <Text style={s.chipValue} numberOfLines={1}>{value}</Text>
    </View>
  );
}

// ─── Import tab ───────────────────────────────────────────────────────────────
function ImportTab({
  json,
  error,
  preview,
  action,
  onJsonChange,
  onActionChange,
  onImport,
}: {
  json: string;
  error: string | null;
  preview: NodeSpyCaptureV1 | null;
  action: 'overlay' | 'home';
  onJsonChange: (t: string) => void;
  onActionChange: (a: 'overlay' | 'home') => void;
  onImport: () => void;
}) {
  return (
    <ScrollView contentContainerStyle={s.importContent}>
      <Text style={s.importHint}>
        In NodeSpy, pin the nodes you want to block → tap "Export Pinned" → share or copy the JSON → paste it below.
      </Text>

      <TextInput
        style={[s.jsonInput, error ? s.jsonInputError : null]}
        multiline
        numberOfLines={8}
        placeholder='Paste NodeSpyCaptureV1 JSON here…'
        placeholderTextColor={C.muted}
        value={json}
        onChangeText={onJsonChange}
        autoCapitalize="none"
        autoCorrect={false}
      />

      {error && (
        <View style={s.errorBox}>
          <Ionicons name="alert-circle-outline" size={16} color={C.red} />
          <Text style={s.errorText}>{error}</Text>
        </View>
      )}

      {preview && (
        <View style={s.previewBox}>
          <Text style={s.previewTitle}>Ready to import</Text>
          <PreviewRow icon="phone-portrait-outline" label="App" value={preview.pkg} />
          <PreviewRow icon="layers-outline" label="Total nodes" value={`${preview.nodes.length}`} />
          <PreviewRow icon="pin-outline" label="Pinned nodes" value={`${preview.pinnedNodeIds.length}`} color={C.green} />
          {preview.ruleQuality && (
            <>
              <PreviewRow
                icon="shield-checkmark-outline"
                label="Recommended rules"
                value={`${preview.ruleQuality.exportableRules} / ${preview.ruleQuality.totalPinned}`}
                color={preview.ruleQuality.weakRules > 0 ? C.yellow : C.green}
              />
              <PreviewRow
                icon="analytics-outline"
                label="Quality"
                value={`${preview.ruleQuality.strongRules} strong · ${preview.ruleQuality.mediumRules} medium · ${preview.ruleQuality.weakRules} weak`}
              />
              {preview.ruleQuality.warnings?.map((warning, i) => (
                <Text key={i} style={s.importWarning}>{warning}</Text>
              ))}
            </>
          )}

          <Text style={[s.sectionLabel, { marginTop: 16 }]}>Block action</Text>
          <View style={s.actionToggleRow}>
            <ActionToggle
              label="Show overlay"
              desc="Display FocusFlow block screen"
              selected={action === 'overlay'}
              onSelect={() => onActionChange('overlay')}
            />
            <ActionToggle
              label="Press HOME"
              desc="Silently send user to home screen"
              selected={action === 'home'}
              onSelect={() => onActionChange('home')}
            />
          </View>

          <TouchableOpacity
            style={[s.importConfirmBtn, (preview.recommendedRules && preview.recommendedRules.length === 0) ? s.importConfirmBtnDisabled : null]}
            onPress={onImport}
            disabled={!!preview.recommendedRules && preview.recommendedRules.length === 0}
          >
            <Ionicons name="checkmark-circle-outline" size={20} color="#fff" />
            <Text style={s.importConfirmText}>
              Import {preview.recommendedRules ? preview.recommendedRules.length : preview.pinnedNodeIds.length} rule{(preview.recommendedRules ? preview.recommendedRules.length : preview.pinnedNodeIds.length) !== 1 ? 's' : ''}
            </Text>
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
}

function PreviewRow({ icon, label, value, color }: { icon: string; label: string; value: string; color?: string }) {
  return (
    <View style={s.previewRow}>
      <Ionicons name={icon as any} size={16} color={C.muted} style={{ marginRight: 6 }} />
      <Text style={s.previewLabel}>{label}</Text>
      <Text style={[s.previewValue, color ? { color } : null]}>{value}</Text>
    </View>
  );
}

function ActionToggle({ label, desc, selected, onSelect }: { label: string; desc: string; selected: boolean; onSelect: () => void }) {
  return (
    <TouchableOpacity
      style={[s.actionToggle, selected && s.actionToggleSelected]}
      onPress={onSelect}
    >
      <View style={s.actionToggleCheck}>
        {selected && <Ionicons name="checkmark" size={14} color={C.primary} />}
      </View>
      <View style={{ flex: 1 }}>
        <Text style={[s.actionToggleLabel, selected && { color: C.primary }]}>{label}</Text>
        <Text style={s.actionToggleDesc}>{desc}</Text>
      </View>
    </TouchableOpacity>
  );
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function dedupeRules(rules: CustomNodeRule[]): CustomNodeRule[] {
  const seen = new Set<string>();
  return rules.filter(r => {
    const key = `${r.pkg}|${r.matchResId ?? ''}|${r.matchText ?? ''}|${r.matchCls ?? ''}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function qualityColor(tier?: string) {
  if (tier === 'strong') return C.green;
  if (tier === 'medium') return C.yellow;
  return C.orange;
}

// ─── Styles ───────────────────────────────────────────────────────────────────
const s = StyleSheet.create({
  root:          { flex: 1, backgroundColor: C.bg, paddingTop: Platform.OS === 'android' ? 24 : 0 },
  header:        { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border },
  closeBtn:      { marginRight: 12, padding: 4 },
  title:         { flex: 1, color: C.text, fontSize: 17, fontWeight: '700' },
  clearBtn:      { padding: 6 },
  subtitle:      { color: C.muted, fontSize: 12, paddingHorizontal: 16, paddingVertical: 8, lineHeight: 18 },
  tabBar:        { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: C.border },
  tab:           { flex: 1, paddingVertical: 10, alignItems: 'center' },
  tabActive:     { borderBottomWidth: 2, borderBottomColor: C.primary },
  tabText:       { color: C.muted, fontSize: 13, fontWeight: '500' },
  tabTextActive: { color: C.primary },
  list:          { padding: 12, gap: 10 },
  ruleCard:      { backgroundColor: C.surface, borderRadius: 10, padding: 12, borderWidth: 1, borderColor: C.border },
  ruleHeader:    { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 4 },
  actionBadge:   { paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  actionBadgeText:{ fontSize: 10, fontWeight: '700', fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace' },
  ruleLabel:     { flex: 1, color: C.text, fontSize: 14, fontWeight: '600' },
  qualityBadge:  { paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  qualityBadgeText:{ fontSize: 10, fontWeight: '700', fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace' },
  rulePkg:       { color: C.muted, fontSize: 11, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', marginBottom: 6 },
  ruleSelectors: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 8 },
  ruleWarning:   { color: C.orange, fontSize: 11, lineHeight: 16, marginBottom: 8 },
  chip:          { flexDirection: 'row', backgroundColor: C.surfaceVar, borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  chipLabel:     { color: C.muted, fontSize: 11, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace' },
  chipValue:     { color: C.text, fontSize: 11, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', maxWidth: 160, marginLeft: 2 },
  ruleFooter:    { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  ruleDate:      { color: C.muted, fontSize: 11 },
  ruleActions:   { flexDirection: 'row', alignItems: 'center', gap: 8 },
  deleteBtn:     { padding: 6 },
  emptyState:    { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32, gap: 12 },
  emptyTitle:    { color: C.text, fontSize: 18, fontWeight: '700' },
  emptyBody:     { color: C.muted, fontSize: 14, textAlign: 'center', lineHeight: 20 },
  importBtn:     { flexDirection: 'row', alignItems: 'center', backgroundColor: C.primary, borderRadius: 10, paddingHorizontal: 18, paddingVertical: 12, gap: 8 },
  importBtnText: { color: '#fff', fontSize: 15, fontWeight: '600' },
  importContent: { padding: 16, gap: 14 },
  importHint:    { color: C.muted, fontSize: 13, lineHeight: 20 },
  jsonInput:     { backgroundColor: C.surface, borderRadius: 8, borderWidth: 1, borderColor: C.border, color: C.text, fontSize: 12, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', padding: 12, textAlignVertical: 'top', minHeight: 140 },
  jsonInputError:{ borderColor: C.red },
  errorBox:      { flexDirection: 'row', gap: 8, alignItems: 'flex-start', backgroundColor: C.red + '18', borderRadius: 8, padding: 10 },
  errorText:     { color: C.red, fontSize: 13, flex: 1, lineHeight: 18 },
  previewBox:    { backgroundColor: C.surface, borderRadius: 10, padding: 14, borderWidth: 1, borderColor: C.border },
  previewTitle:  { color: C.green, fontWeight: '700', fontSize: 14, marginBottom: 10 },
  previewRow:    { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  previewLabel:  { color: C.muted, fontSize: 13, flex: 1 },
  previewValue:  { color: C.text, fontSize: 13, fontWeight: '500' },
  importWarning: { color: C.orange, fontSize: 12, lineHeight: 17, marginTop: 4 },
  sectionLabel:  { color: C.muted, fontSize: 12, fontWeight: '600', textTransform: 'uppercase', letterSpacing: 0.8, marginBottom: 8 },
  actionToggleRow:{ gap: 8, marginBottom: 14 },
  actionToggle:  { flexDirection: 'row', alignItems: 'flex-start', backgroundColor: C.surfaceVar, borderRadius: 8, padding: 12, gap: 10, borderWidth: 1, borderColor: C.border },
  actionToggleSelected: { borderColor: C.primary },
  actionToggleCheck:{ width: 18, height: 18, borderRadius: 9, borderWidth: 1, borderColor: C.muted, alignItems: 'center', justifyContent: 'center' },
  actionToggleLabel:{ color: C.text, fontSize: 14, fontWeight: '600' },
  actionToggleDesc: { color: C.muted, fontSize: 12, marginTop: 2 },
  importConfirmBtn:{ flexDirection: 'row', alignItems: 'center', backgroundColor: C.green, borderRadius: 10, paddingHorizontal: 18, paddingVertical: 13, gap: 8, justifyContent: 'center' },
  importConfirmBtnDisabled:{ backgroundColor: C.border },
  importConfirmText:{ color: '#fff', fontSize: 15, fontWeight: '700' },
});
