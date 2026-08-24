import { describe, expect, it } from 'vitest';
import {
  COMMON_WEAK_PASSWORDS,
  generateRandomPassword,
  getPasswordStrength,
  hashPassword,
} from '@/utils/pinCrypto';

describe('pinCrypto', () => {
  it('produces the known SHA-256 digest without exposing plaintext', async () => {
    const digest = await hashPassword('correct horse battery staple');

    expect(digest).toBe('c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8a');
    expect(digest).not.toContain('correct');
  });

  it('classifies password length and character variety according to policy', () => {
    expect(getPasswordStrength('').valid).toBe(false);
    expect(getPasswordStrength('short').level).toBe('too-short');
    expect(getPasswordStrength('abcdefgh').level).toBe('weak');
    expect(getPasswordStrength('Abcd1234').level).toBe('fair');
    expect(getPasswordStrength('Abcd1234!xyz').level).toBe('strong');
    expect(getPasswordStrength('Abcd1234!xyz5678').level).toBe('very-strong');
    expect(COMMON_WEAK_PASSWORDS.has('password123')).toBe(true);
  });

  it('generates passwords of the requested length without ambiguous characters', () => {
    const generated = generateRandomPassword(32);

    expect(generated).toHaveLength(32);
    expect(generated).not.toMatch(/[0OlI1]/);
  });
});