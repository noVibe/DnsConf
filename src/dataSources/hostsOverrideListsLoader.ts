import { isBlock } from './hostsBlockListsLoader.js';

export interface BypassRoute {
  ip: string;
  website: string;
}

async function fetchList(url: string): Promise<string> {
  const { default: fetch } = await import('node-fetch');
  console.log(`\x1b[0;33m>>> \x1b[0;35mLoading Override list from url: ${url}\x1b[0m`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch ${url}: ${response.statusText}`);
  }
  return response.text();
}

function removeWww(domain: string): string {
  if (domain.startsWith('www.')) {
    return domain.substring('www.'.length);
  }
  return domain;
}

export async function fetchWebsites(urls: string[]): Promise<BypassRoute[]> {
  const results = await Promise.all(urls.map(url => fetchList(url)));
  
  return results
    .flatMap(content => content.split(/\r?\n/))
    .filter(line => line.trim() !== '')
    .filter(line => !line.startsWith('#'))
    .map(line => line.toLowerCase().trim())
    .filter(line => !isBlock(line))
    .map(line => {
      const delimiter = line.indexOf(' ');
      const ip = line.substring(0, delimiter);
      const website = removeWww(line.substring(delimiter + 1).trim());
      return { ip, website };
    })
    .filter((value, index, self) => 
      index === self.findIndex(t => t.website === value.website)
    );
}
