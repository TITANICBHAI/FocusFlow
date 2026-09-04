import { describe, expect, it } from 'vitest';
import { createTaskOperationQueue } from '@/services/taskOperationQueue';

describe('task operation queue', () => {
  it('runs reads and writes in enqueue order', async () => {
    const queue = createTaskOperationQueue();
    const events: string[] = [];

    const first = queue.enqueue(async () => {
      events.push('first:start');
      await new Promise((resolve) => setTimeout(resolve, 10));
      events.push('first:end');
      return 'first';
    });
    const second = queue.enqueue(async () => {
      events.push('second');
      return 'second';
    });

    await expect(Promise.all([first, second])).resolves.toEqual(['first', 'second']);
    expect(events).toEqual(['first:start', 'first:end', 'second']);
  });

  it('continues with the next operation after a failure', async () => {
    const queue = createTaskOperationQueue();
    const events: string[] = [];

    const failed = queue.enqueue(async () => {
      events.push('failed');
      throw new Error('expected');
    });
    const recovered = queue.enqueue(async () => {
      events.push('recovered');
      return 'ok';
    });

    await expect(failed).rejects.toThrow('expected');
    await expect(recovered).resolves.toBe('ok');
    expect(events).toEqual(['failed', 'recovered']);
  });
});