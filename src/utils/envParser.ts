export function parse(envValue: string | undefined): string[] {
  if (!envValue) return [];
  envValue = envValue.trim();
  if (envValue === '') return [];
  return envValue.split(',').map(s => s.trim());
}
