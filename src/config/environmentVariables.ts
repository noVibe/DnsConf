import { fail } from '../utils/log.js';

function extractMandatoryVariable(key: string): string {
  const env = process.env[key];
  if (!env || env.trim() === '') {
    fail(`Не обнаружена обязательная переменная среды: ${key}`);
    process.exit(1);
  }
  return env;
}

export const DNS = extractMandatoryVariable('DNS');
export const CLIENT_ID = extractMandatoryVariable('CLIENT_ID');
export const AUTH_SECRET = extractMandatoryVariable('AUTH_SECRET');
export const BLOCK = process.env.BLOCK;
export const REDIRECT = process.env.REDIRECT;
