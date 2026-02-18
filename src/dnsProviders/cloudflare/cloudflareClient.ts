import { CLIENT_ID, AUTH_SECRET } from '../../config/environmentVariables.js';
import { fail } from '../../utils/log.js';
import type {
  GatewayListDto,
  GatewayRuleDto,
  CreateListRequest,
  CreateRuleRequest,
  CloudflareApiResponse
} from './types.js';
import type { RequestInit, BodyInit } from 'node-fetch';

const BASE_URL = `https://api.cloudflare.com/client/v4/accounts/${CLIENT_ID}/gateway`;

async function request<T>(method: string, path: string, body: unknown = null): Promise<T | null> {
  const { default: fetch } = await import('node-fetch');
  const url = BASE_URL + (path || '');
  const options: RequestInit = {
    method,
    headers: {
      'Authorization': `Bearer ${AUTH_SECRET}`,
      'Content-Type': 'application/json'
    }
  };
  
  if (body !== null) {
    options.body = JSON.stringify(body) as BodyInit;
  }
  
  const response = await fetch(url, options);
  
  if (response.status > 299) {
    const errorData = await response.text();
    const errorMessage = `HTTP ${response.status}: ${errorData}`;
    fail(errorMessage);
    
    if (response.status === 401) {
      fail('Invalid API Token!');
      process.exit(1);
    }
    if (response.status === 403) {
      fail(`Token doesn't have necessary permissions!
Generate a token with permissions:
1) Zero Trust:Edit
2) Account Firewall Access Rules:Edit`);
      process.exit(1);
    }
    throw new Error(errorMessage);
  }
  
  const text = await response.text();
  return text ? JSON.parse(text) as T : null;
}

export async function getLists(): Promise<GatewayListDto[]> {
  const response = await request<CloudflareApiResponse<GatewayListDto[]>>('GET', '/lists');
  return response?.result || [];
}

export async function createList(createListRequest: CreateListRequest): Promise<CloudflareApiResponse<GatewayListDto> | null> {
  return request<CloudflareApiResponse<GatewayListDto>>('POST', '/lists', createListRequest);
}

export async function deleteListById(listId: string): Promise<CloudflareApiResponse<GatewayListDto> | null> {
  return request<CloudflareApiResponse<GatewayListDto>>('DELETE', `/lists/${listId}`);
}

export async function getRules(): Promise<CloudflareApiResponse<GatewayRuleDto[]> | null> {
  return request<CloudflareApiResponse<GatewayRuleDto[]>>('GET', '/rules');
}

export async function createRule(rule: CreateRuleRequest): Promise<CloudflareApiResponse<GatewayRuleDto> | null> {
  return request<CloudflareApiResponse<GatewayRuleDto>>('POST', '/rules', rule);
}

export async function deleteRuleById(ruleId: string): Promise<CloudflareApiResponse<GatewayRuleDto> | null> {
  return request<CloudflareApiResponse<GatewayRuleDto>>('DELETE', `/rules/${ruleId}`);
}
