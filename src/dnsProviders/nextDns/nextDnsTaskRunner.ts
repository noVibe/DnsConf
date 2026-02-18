import { fetchWebsites as fetchBlockWebsites } from '../../dataSources/hostsBlockListsLoader.js';
import { fetchWebsites as fetchOverrideWebsites } from '../../dataSources/hostsOverrideListsLoader.js';
import type { BypassRoute } from '../../dataSources/hostsOverrideListsLoader.js';
import { parse } from '../../utils/envParser.js';
import { BLOCK, REDIRECT } from '../../config/environmentVariables.js';
import { global, step, io, common, fail } from '../../utils/log.js';
import {
  fetchDenylist,
  saveDeny,
  deleteDenyById,
  fetchRewrites,
  saveRewrite,
  deleteRewriteById
} from './nextDnsClient.js';
import { callApi } from './nextDnsRateLimitedApiProcessor.js';
import type { CreateDenyDto, CreateRewriteDto, RewriteDto } from './types.js';

export default class NextDnsTaskRunner {
  async run(): Promise<void> {
    global('NextDNS');
    common(`Script behaviour: old block/redirect settings are about to be updated via provided block/redirect sources.
If no sources provided, then all NextDNS settings will be removed.
If provided only one type of sources, related settings will be updated; another type remain untouched.
NextDNS API rate limiter reset config: 60 seconds after the last request`);
    
    const blockSources = parse(BLOCK);
    const rewriteSources = parse(REDIRECT);
    
    if (blockSources.length > 0) {
      step(`Obtain block lists from ${blockSources.length} sources`);
      const blocks = await fetchBlockWebsites(blockSources);
      step('Prepare denylist');
      const filteredBlocklist = await this.dropExistingDenys(blocks);
      common(`Prepared ${filteredBlocklist.length} domains to block`);
      step('Save denylist');
      await this.saveDenyList(filteredBlocklist);
    } else {
      fail('No block sources provided');
    }
    
    if (rewriteSources.length > 0) {
      step(`Obtain rewrite lists from ${rewriteSources.length} sources`);
      const overrides = await fetchOverrideWebsites(rewriteSources);
      step('Prepare rewrites');
      const requests = this.buildNewRewrites(overrides);
      const createRewriteDtos = await this.cleanupOutdated(requests);
      step('Save rewrites');
      await this.saveRewrites(createRewriteDtos);
    } else {
      fail('No rewrite sources provided');
    }
    
    if (blockSources.length === 0 && rewriteSources.length === 0) {
      step('Remove settings');
      await this.removeAll();
    }
    
    global('FINISHED');
  }
  
  async dropExistingDenys(newDenyList: string[]): Promise<string[]> {
    io('Fetching existing denylist from NextDNS');
    const existingDenyList = await fetchDenylist();
    const existingDomainsSet = new Set(
      existingDenyList
        .filter(deny => deny.active)
        .map(deny => deny.id)
    );
    return newDenyList.filter(domain => !existingDomainsSet.has(domain));
  }
  
  async saveDenyList(newDenylist: string[]): Promise<void> {
    const createRequests: CreateDenyDto[] = newDenylist.map(domain => ({
      id: domain,
      active: true
    }));
    io('Saving new denylist to NextDNS...');
    await callApi(createRequests, saveDeny);
  }
  
  async removeAll(): Promise<void> {
    io('Fetching existing denylist from NextDNS');
    const existing = await fetchDenylist();
    const ids = existing.map(d => d.id);
    io('Removing denylist from NextDNS');
    await callApi(ids, deleteDenyById);
    
    io('Fetching existing rewrites from NextDNS');
    const rewrites = await fetchRewrites();
    const rewriteIds = rewrites.map(r => r.id);
    io('Removing rewrites from NextDNS');
    await callApi(rewriteIds, deleteRewriteById);
  }
  
  buildNewRewrites(overrides: BypassRoute[]): Map<string, CreateRewriteDto> {
    const rewriteDtos = new Map<string, CreateRewriteDto>();
    for (const route of overrides) {
      if (!rewriteDtos.has(route.website)) {
        rewriteDtos.set(route.website, {
          name: route.website,
          content: route.ip
        });
      }
    }
    return rewriteDtos;
  }
  
  async cleanupOutdated(newRewriteRequests: Map<string, CreateRewriteDto>): Promise<CreateRewriteDto[]> {
    const existingRewrites = await this.getExistingRewrites();
    const outdatedIds: string[] = [];
    
    for (const existingRewrite of existingRewrites) {
      const domain = existingRewrite.name;
      const oldIp = existingRewrite.content;
      const request = newRewriteRequests.get(domain);
      
      if (request && request.content !== oldIp) {
        outdatedIds.push(existingRewrite.id);
      } else if (request) {
        newRewriteRequests.delete(domain);
      }
    }
    
    if (outdatedIds.length > 0) {
      io(`Removing ${outdatedIds.length} outdated rewrites from NextDNS`);
      await callApi(outdatedIds, deleteRewriteById);
    }
    
    return Array.from(newRewriteRequests.values());
  }
  
  async getExistingRewrites(): Promise<RewriteDto[]> {
    io('Fetching existing rewrites from NextDNS');
    return fetchRewrites();
  }
  
  async saveRewrites(createRewriteDtos: CreateRewriteDto[]): Promise<void> {
    io(`Saving ${createRewriteDtos.length} new rewrites to NextDNS...`);
    await callApi(createRewriteDtos, saveRewrite);
  }
}
