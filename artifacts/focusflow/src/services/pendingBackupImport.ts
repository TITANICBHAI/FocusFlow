/**
 * Short-lived handoff for backups opened by Android file managers.
 *
 * The backup contents stay in memory and never travel through Expo Router
 * params. Only the opaque id is placed in navigation state. Staging a new
 * import clears older staged contents so a cancelled/abandoned import cannot
 * accumulate private backup data in the JS process.
 */

const pendingBackups = new Map<string, string>();

function createId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export function stageBackupImport(content: string): string {
  pendingBackups.clear();
  const id = createId();
  pendingBackups.set(id, content);
  return id;
}

export function peekBackupImport(id: string): string | null {
  return pendingBackups.get(id) ?? null;
}

export function consumeBackupImport(id: string): string | null {
  const content = pendingBackups.get(id) ?? null;
  pendingBackups.delete(id);
  return content;
}

export function discardBackupImport(id: string): void {
  pendingBackups.delete(id);
}