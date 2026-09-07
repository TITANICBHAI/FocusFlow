import { beforeEach, describe, expect, it } from 'vitest';
import {
  consumeBackupImport,
  discardBackupImport,
  peekBackupImport,
  stageBackupImport,
} from '@/services/pendingBackupImport';

describe('pending backup import handoff', () => {
  beforeEach(() => {
    const id = stageBackupImport('cleanup');
    discardBackupImport(id);
  });

  it('stores content behind an opaque id and removes it on consume', () => {
    const id = stageBackupImport('{"kind":"FocusFlowBackupV1"}');

    expect(id).not.toContain('FocusFlow');
    expect(peekBackupImport(id)).toBe('{"kind":"FocusFlowBackupV1"}');
    expect(consumeBackupImport(id)).toBe('{"kind":"FocusFlowBackupV1"}');
    expect(peekBackupImport(id)).toBeNull();
  });

  it('clears older staged content when a new file is opened', () => {
    const firstId = stageBackupImport('first');
    const secondId = stageBackupImport('second');

    expect(peekBackupImport(firstId)).toBeNull();
    expect(peekBackupImport(secondId)).toBe('second');
  });

  it('discards cancelled imports', () => {
    const id = stageBackupImport('cancelled');

    discardBackupImport(id);

    expect(consumeBackupImport(id)).toBeNull();
  });
});