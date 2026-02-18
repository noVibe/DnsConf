import { fetchWebsites as fetchBlockWebsites } from '../../dataSources/hostsBlockListsLoader.js';
import { fetchWebsites as fetchOverrideWebsites } from '../../dataSources/hostsOverrideListsLoader.js';
import type { BypassRoute } from '../../dataSources/hostsOverrideListsLoader.js';
import { parse } from '../../utils/envParser.js';
import { BLOCK, REDIRECT } from '../../config/environmentVariables.js';
import { global, step, io, common, fail } from '../../utils/log.js';
import {
  getLists,
  createList,
  deleteListById,
  getRules,
  createRule,
  deleteRuleById
} from './cloudflareClient.js';
import type { GatewayListDto, GatewayRuleDto, CreateListRequest, CreateRuleRequest, Item } from './types.js';

const BLOCK_LIST_NAME_PREFIX = 'Blocked websites by script';
const OVERRIDE_LIST_NAME_PREFIX = 'Override websites by script';
const RULES_LIST_NAME_PREFIX = 'Rules set by script';
const sessionId = `session_${Date.now()}`;

function chunkArray<T>(array: T[], chunkSize: number): T[][] {
  const chunks: T[][] = [];
  if (array.length <= chunkSize) {
    return [array];
  }
  for (let i = 0; i < array.length; i += chunkSize) {
    chunks.push(array.slice(i, i + chunkSize));
  }
  return chunks;
}

function websiteAsItem(urls: string[]): Item[] {
  return urls.map(url => ({ value: url, description: '' }));
}

async function saveNewLists(createListRequests: CreateListRequest[]): Promise<GatewayListDto[]> {
  io(`Saving ${createListRequests.length} lists...`);
  const results: GatewayListDto[] = [];
  let counter = 0;
  
  for (const request of createListRequests) {
    try {
      const response = await createList(request);
      if (response?.success && response.result) {
        counter++;
        process.stdout.write(`${counter}/${createListRequests.length}\r`);
        results.push(response.result);
      } else {
        fail(`Failed to save list: ${JSON.stringify(response?.errors)}`);
      }
    } catch (error) {
      fail(`Error saving list: ${(error as Error).message}`);
    }
  }
  
  console.log(`\n${counter} of ${createListRequests.length} new lists have been saved`);
  return results;
}

async function createNewBlockLists(websitesToBlock: string[]): Promise<GatewayListDto[]> {
  const websitesByChunks = chunkArray(websiteAsItem(websitesToBlock), 1000);
  
  common(`Total websites count: ${websitesToBlock.length}\nPrepared ${websitesByChunks.length} chunks of websites list to block.`);
  
  const createListRequests: CreateListRequest[] = websitesByChunks.map((items, index) => ({
    name: `${BLOCK_LIST_NAME_PREFIX} ${index + 1}`,
    type: 'DOMAIN',
    items,
    description: sessionId
  }));
  
  return saveNewLists(createListRequests);
}

async function createNewOverrideLists(routes: BypassRoute[]): Promise<Record<string, GatewayListDto[]>> {
  const result: Record<string, GatewayListDto[]> = {};
  
  // Priority of IP is provided by sources order
  const mergedWebsiteOnIp = new Map<string, string>();
  for (const route of routes) {
    if (!mergedWebsiteOnIp.has(route.website)) {
      mergedWebsiteOnIp.set(route.website, route.ip);
    }
  }
  
  // Group to lists by IP
  const ipForWebsites = new Map<string, string[]>();
  for (const [website, ip] of mergedWebsiteOnIp.entries()) {
    if (!ipForWebsites.has(ip)) {
      ipForWebsites.set(ip, []);
    }
    ipForWebsites.get(ip)!.push(website);
  }
  
  for (const [overrideIp, websites] of ipForWebsites.entries()) {
    io(`Posting override lists for IP: ${overrideIp}`);
    const chunks = chunkArray(websiteAsItem(websites), 1000);
    const createListRequests: CreateListRequest[] = chunks.map((items, index) => ({
      name: `${OVERRIDE_LIST_NAME_PREFIX} to IP ${overrideIp} ${index + 1}`,
      type: 'DOMAIN',
      items,
      description: sessionId
    }));
    const response = await saveNewLists(createListRequests);
    result[overrideIp] = response;
  }
  
  return result;
}

async function removeOldLists(): Promise<void> {
  const lists = await getLists();
  const oldIds = lists
    .filter(list => 
      list.name.startsWith(BLOCK_LIST_NAME_PREFIX) || 
      list.name.startsWith(OVERRIDE_LIST_NAME_PREFIX)
    )
    .filter(list => list.description !== sessionId)
    .map(list => list.id);
  
  if (oldIds.length === 0) {
    common('No lists found to remove');
    return;
  }
  
  io(`Removing ${oldIds.length} lists...`);
  let counter = 0;
  const errors: Array<{ id: string; error: unknown }> = [];
  
  for (const id of oldIds) {
    try {
      const response = await deleteListById(id);
      if (response?.success) {
        counter++;
        process.stdout.write(`${counter}/${oldIds.length}\r`);
      } else {
        errors.push({ id, error: response?.errors });
      }
    } catch (error) {
      errors.push({ id, error: (error as Error).message });
    }
  }
  
  if (errors.length > 0) {
    fail(`Failed to remove old lists (${errors.length} of ${oldIds.length}): ${JSON.stringify(errors)}`);
  } else {
    common(`\n${counter} of ${oldIds.length} old lists have been removed`);
  }
}

async function removeOldRules(existingRules: GatewayRuleDto[]): Promise<GatewayRuleDto[]> {
  const rules = existingRules || [];
  const removeList = rules
    .filter(rule => rule.name.startsWith(RULES_LIST_NAME_PREFIX))
    .filter(rule => rule.description !== sessionId);
  
  io(`Removing ${removeList.length} old rules...`);
  let counter = 0;
  
  for (const rule of removeList) {
    try {
      const response = await deleteRuleById(rule.id);
      if (response?.success) {
        counter++;
        process.stdout.write(`${counter}/${removeList.length}\r`);
        const index = rules.findIndex(r => r.id === rule.id);
        if (index > -1) rules.splice(index, 1);
      } else {
        fail(`Failed to remove old rule with id ${rule.id}: ${JSON.stringify(response?.errors)}`);
      }
    } catch (error) {
      fail(`Error removing rule ${rule.id}: ${(error as Error).message}`);
    }
  }
  
  common(`\n${counter} of ${removeList.length} old rules have been removed`);
  return rules;
}

function makeTrafficExpression(lists: GatewayListDto[]): string {
  const listIds = lists.map(list => list.id);
  return listIds
    .map(id => `any(dns.domains[*] in $${id})`)
    .join(' or ');
}

async function createNewBlockingRule(lists: GatewayListDto[]): Promise<void> {
  const traffic = makeTrafficExpression(lists);
  const rule: CreateRuleRequest = {
    name: RULES_LIST_NAME_PREFIX,
    action: 'block',
    description: sessionId,
    filters: ['dns'],
    enabled: true,
    traffic
  };
  
  io('Posting new blocking rule');
  const result = await createRule(rule);
  if (!result?.success) {
    fail(`Failed to set blocking rule: ${JSON.stringify(result?.errors)}`);
  }
}

class PrecedenceCounter {
  private skipSet: Set<number>;
  private number: number;

  constructor(existingValues: Set<number>) {
    this.skipSet = existingValues;
    this.number = 1;
  }
  
  next(): number {
    while (this.skipSet.has(this.number)) {
      this.number++;
    }
    return this.number++;
  }
}

async function createNewOverrideRulesCollisionAware(
  lists: Record<string, GatewayListDto[]>,
  existingRules: GatewayRuleDto[]
): Promise<void> {
  const existingPrecedences = new Set(existingRules.map(r => r.precedence));
  const precedenceCounter = new PrecedenceCounter(existingPrecedences);
  
  const promises = Object.entries(lists).map(async ([overrideIp, list]) => {
    const traffic = makeTrafficExpression(list);
    const rule: CreateRuleRequest = {
      name: `${RULES_LIST_NAME_PREFIX} override to IP -> ${overrideIp}`,
      precedence: precedenceCounter.next(),
      action: 'override',
      description: sessionId,
      filters: ['dns'],
      enabled: true,
      traffic,
      rule_settings: {
        override_ips: [overrideIp]
      }
    };
    
    io(`Posting new override rule for IP: ${overrideIp}`);
    const result = await createRule(rule);
    if (!result?.success) {
      fail(`Failed to set override rule: ${JSON.stringify(result?.errors)}`);
    }
  });
  
  await Promise.all(promises);
}

export default class CloudflareTaskRunner {
  async run(): Promise<void> {
    global('CLOUDFLARE');
    common(`Script behaviour: previously generated data is always about to be removed.
If you want to clear Cloudflare block/redirect settings, launch this script without providing sources in related environment variables.`);
    
    const blockUrls = parse(BLOCK);
    const redirectUrls = parse(REDIRECT);
    
    const blocks = blockUrls.length > 0 ? await fetchBlockWebsites(blockUrls) : [];
    const overrides = redirectUrls.length > 0 ? await fetchOverrideWebsites(redirectUrls) : [];
    
    step('Remove old rules.');
    const gatewayRulesResponse = await getRules();
    const gatewayRuleDtos = gatewayRulesResponse?.result || [];
    const remainingRules = await removeOldRules(gatewayRuleDtos);
    
    step('Remove old lists.');
    await removeOldLists();
    
    step('Creating new block lists');
    if (blocks.length > 0) {
      const gatewayListDtos = await createNewBlockLists(blocks);
      step('Creating new blocking rule');
      await createNewBlockingRule(gatewayListDtos);
    } else {
      fail('Websites to block were not provided');
    }
    
    step('Creating new override lists');
    if (overrides.length > 0) {
      const newOverrideLists = await createNewOverrideLists(overrides);
      step('Creating new override rules');
      await createNewOverrideRulesCollisionAware(newOverrideLists, remainingRules);
    } else {
      fail('Websites to override were not provided');
    }
    
    global('FINISHED');
  }
}
