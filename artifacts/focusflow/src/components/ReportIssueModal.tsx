import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@/hooks/useTheme';
import { formatLogsForShare } from '@/services/startupLogger';
import { sendDiagnosticsReport } from '@/services/diagnosticsReporter';

type Props = {
  visible: boolean;
  onClose: () => void;
  error?: Error | null;
};

export default function ReportIssueModal({ visible, onClose, error }: Props) {
  const { theme, isDark } = useTheme();
  const insets = useSafeAreaInsets();
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [logs, setLogs] = useState('');

  useEffect(() => {
    if (!visible) return;
    setStatus(null);
    setDescription('');
    void formatLogsForShare().then(setLogs).catch(() => setLogs('(logs unavailable)'));
  }, [visible]);

  const handleSend = async () => {
    if (busy) return;
    setBusy(true);
    setStatus(null);

    const errorDetails = error
      ? `Error screen message: ${error.message}\nStack trace:\n${error.stack ?? '(unavailable)'}\n\n`
      : '';
    const result = await sendDiagnosticsReport({
      description,
      logs: `${errorDetails}${logs}`,
    });

    setBusy(false);
    if (!result.ok) {
      setStatus(result.error);
      return;
    }

    setStatus('Report sent. Thank you for helping improve FocusFlow.');
    setDescription('');
  };

  return (
    <Modal
      visible={visible}
      animationType="slide"
      transparent
      onRequestClose={onClose}
      statusBarTranslucent
    >
      <View style={styles.overlay}>
        <View
          style={[
            styles.sheet,
            {
              backgroundColor: theme.background,
              paddingBottom: insets.bottom + 16,
            },
          ]}
        >
          <View style={[styles.header, { borderBottomColor: theme.border }]}>
            <View style={styles.headerTitle}>
              <Ionicons name="paper-plane-outline" size={20} color="#6366F1" />
              <Text style={[styles.title, { color: theme.text }]}>Report this issue</Text>
            </View>
            <Pressable onPress={onClose} style={styles.closeButton} accessibilityLabel="Close report form">
              <Ionicons name="close" size={24} color={theme.text} />
            </Pressable>
          </View>

          <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            <View
              style={[
                styles.notice,
                {
                  backgroundColor: isDark ? 'rgba(99,102,241,0.14)' : '#EEF2FF',
                  borderColor: isDark ? 'rgba(129,140,248,0.35)' : '#C7D2FE',
                },
              ]}
            >
              <Ionicons name="information-circle-outline" size={21} color="#6366F1" />
              <Text style={[styles.noticeText, { color: theme.text }]}>
                This report will be intentionally sent from your phone to FocusFlow support. Nothing is sent unless you tap “Send report”.
              </Text>
            </View>

            <Text style={[styles.label, { color: theme.text }]}>What happened?</Text>
            <Text style={[styles.helper, { color: theme.textSecondary ?? '#888' }]}>
              Tell us what you were doing and how the error appeared. This is optional, but it helps us reproduce the problem.
            </Text>
            <TextInput
              value={description}
              onChangeText={setDescription}
              placeholder="For example: I opened Settings, tapped Manage Permissions, and the app showed an error."
              placeholderTextColor={theme.muted ?? '#888'}
              multiline
              maxLength={2_000}
              textAlignVertical="top"
              style={[
                styles.input,
                {
                  color: theme.text,
                  backgroundColor: isDark ? '#1C1C1E' : '#F2F2F7',
                  borderColor: theme.border,
                },
              ]}
            />

            <Text style={[styles.included, { color: theme.textSecondary ?? '#888' }]}>
              Included: the error details, app version, Android version, and recent diagnostic logs. Personal files, contacts, installed-app lists, and location are not included.
            </Text>

            {status ? (
              <View style={styles.statusRow}>
                <Ionicons
                  name={status.startsWith('Report sent') ? 'checkmark-circle' : 'alert-circle'}
                  size={18}
                  color={status.startsWith('Report sent') ? '#34C759' : '#FF9500'}
                />
                <Text style={[styles.statusText, { color: theme.text }]}>{status}</Text>
              </View>
            ) : null}

            <Pressable
              onPress={handleSend}
              disabled={busy}
              style={({ pressed }) => [
                styles.sendButton,
                { opacity: busy ? 0.6 : pressed ? 0.85 : 1 },
              ]}
              accessibilityRole="button"
              accessibilityLabel="Send diagnostic report"
            >
              {busy ? <ActivityIndicator color="#fff" /> : <Ionicons name="send" size={17} color="#fff" />}
              <Text style={styles.sendText}>{busy ? 'Sending…' : 'Send report'}</Text>
            </Pressable>

            <Pressable onPress={onClose} disabled={busy} style={styles.cancelButton}>
              <Text style={[styles.cancelText, { color: theme.textSecondary ?? '#888' }]}>Cancel</Text>
            </Pressable>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.52)',
    justifyContent: 'flex-end',
  },
  sheet: {
    maxHeight: '92%',
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 18,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerTitle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
  },
  closeButton: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    padding: 18,
    gap: 12,
  },
  notice: {
    flexDirection: 'row',
    gap: 10,
    padding: 13,
    borderRadius: 12,
    borderWidth: 1,
  },
  noticeText: {
    flex: 1,
    fontSize: 13,
    lineHeight: 19,
  },
  label: {
    fontSize: 15,
    fontWeight: '700',
    marginTop: 4,
  },
  helper: {
    fontSize: 13,
    lineHeight: 18,
    marginTop: -5,
  },
  input: {
    minHeight: 112,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 13,
    paddingVertical: 12,
    fontSize: 14,
    lineHeight: 20,
  },
  included: {
    fontSize: 12,
    lineHeight: 17,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
    paddingTop: 2,
  },
  statusText: {
    flex: 1,
    fontSize: 13,
    lineHeight: 18,
  },
  sendButton: {
    minHeight: 48,
    borderRadius: 12,
    backgroundColor: '#6366F1',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginTop: 4,
  },
  sendText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '700',
  },
  cancelButton: {
    alignItems: 'center',
    paddingVertical: 8,
  },
  cancelText: {
    fontSize: 14,
    fontWeight: '600',
  },
});