import 'dotenv/config';
import { DNS } from './config/environmentVariables.js';

const provider = DNS.toUpperCase();

const dnsBasePackage: Record<string, string> = {
  'CLOUDFLARE': './dnsProviders/cloudflare/cloudflareTaskRunner.js',
  'NEXTDNS': './dnsProviders/nextDns/nextDnsTaskRunner.js'
};

const targetModule = dnsBasePackage[provider];

if (!targetModule) {
  console.error(`Unsupported DNS provider! Must be CLOUDFLARE or NEXTDNS. Was: ${provider}`);
  process.exit(1);
}

const { default: Runner } = await import(targetModule);
const runner = new Runner();
await runner.run();
