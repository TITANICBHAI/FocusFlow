/**
 * Serializes task reads and writes that share the AppContext task snapshot.
 *
 * A database write queue alone is not enough: a refresh can read an older
 * snapshot while a write is in flight and dispatch it after the write's
 * optimistic state. Keeping reads and UI reconciliation in the same queue
 * preserves the ordering users expect from task actions.
 */
export interface TaskOperationQueue {
  enqueue<T>(operation: () => Promise<T>): Promise<T>;
}

export function createTaskOperationQueue(): TaskOperationQueue {
  let tail: Promise<void> = Promise.resolve();

  return {
    enqueue<T>(operation: () => Promise<T>): Promise<T> {
      const next = tail.then(operation, operation);
      tail = next.then(
        () => undefined,
        () => undefined,
      );
      return next;
    },
  };
}