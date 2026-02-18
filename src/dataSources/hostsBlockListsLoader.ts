const BLOCK_PREFIXES = ['0.0.0.0 ', '127.0.0.1 ', '::1 '];
const LOCALHOST_NAMES = ['localhost', 'ip6-localhost'];

export function isBlock(line: string): boolean {
  return BLOCK_PREFIXES.some(prefix => line.startsWith(prefix));
}

function isLocalhost(line: string): boolean {
  return LOCALHOST_NAMES.some(localhost => line.endsWith(localhost));
}

function removeIp(line: string): string {
  for (const prefix of BLOCK_PREFIXES) {
    if (line.startsWith(prefix)) {
      return line.substring(prefix.length).trim();
    }
  }
  return line;
}

function removeWww(domain: string): string {
  if (domain.startsWith('www.')) {
    return domain.substring('www.'.length);
  }
  return domain;
}

async function fetchList(url: string): Promise<string> {
  const { default: fetch } = await import('node-fetch');
  console.log(`\x1b[0;33m>>> \x1b[0;35mLoading Block list from url: ${url}\x1b[0m`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch ${url}: ${response.statusText}`);
  }
  return response.text();
}

export async function fetchWebsites(urls: string[]): Promise<string[]> {
  const results = await Promise.all(urls.map(url => fetchList(url)));
  
  return results
    .flatMap(content => content.split(/\r?\n/))
    .filter(line => line.trim() !== '')
    .filter(line => !line.startsWith('#'))
    .map(line => line.toLowerCase().trim())
    .filter(line => isBlock(line) && !isLocalhost(line))
    .map(line => removeWww(removeIp(line)))
    .filter((value, index, self) => self.indexOf(value) === index);
}
