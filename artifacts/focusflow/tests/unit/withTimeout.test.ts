import { describe, expect, it, vi } from 'vitest';
import { withTimeout } from '@/utils/withTimeout';

describe('withTimeout', () => {
  it('rejects hanging native-style promises at the deadline', async () => {
    vi.useFakeTimers();
    const pending = new Promise<void>(() => {});
    const result = withTimeout(pending, 100, 'native call');
    const assertion = expect(result).rejects.toThrow('native call timed out after 100ms');
    await vi.advanceTimersByTimeAsync(100);
    await assertion;
    vi.useRealTimers();
  });

  it('preserves rejected native-style promises', async () => {
    await expect(withTimeout(Promise.reject(new Error('rejected')), 100, 'native call'))
      .rejects.toThrow('rejected');
  });
});
