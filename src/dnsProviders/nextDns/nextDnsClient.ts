import { CLIENT_ID, AUTH_SECRET } from '../../config/environmentVariables.js';
import { fail } from '../../utils/log.js';
import type {
  DenyDto,
  RewriteDto,
  CreateDenyDto,
  CreateRewriteDto,
  NextDnsResponse
} from './types.js';
import type { RequestInit, BodyInit } from 'node-fetch';

const BASE_URL = `https://api.nextdns.io/profiles/${CLIENT_ID}`;

async function request<T>(method: string, path: string, body: unknown = null): Promise<NextDnsResponse<T> | null> {
  const { default: fetch } = await import('node-fetch');
  const url = BASE_URL + (path || '');
  const options: RequestInit = {
    method,
    headers: {
      'X-Api-Key': AUTH_SECRET,
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
    
    if (response.status === 401 || response.status === 403) {
      fail('Invalid api key!');
      process.exit(1);
    }
    
    const error = new Error(errorMessage);
    (error as Error & { code: number }).code = response.status;
    throw error;
  }
  
  const text = await response.text();
  return text ? JSON.parse(text) as NextDnsResponse<T> : null;
}

// Denylist endpoints
export async function fetchDenylist(): Promise<DenyDto[]> {
  const response = await request<DenyDto[]>('GET', '/denylist');
  return response?.data || [];
}

export async function saveDeny(createDenyDto: CreateDenyDto): Promise<NextDnsResponse<DenyDto> | null> {
  return request<DenyDto>('POST', '/denylist', createDenyDto);
}

export async function deleteDenyById(id: string): Promise<NextDnsResponse<DenyDto> | null> {
  return request<DenyDto>('DELETE', `/denylist/${id}`);
}

// Rewrites endpoints
export async function fetchRewrites(): Promise<RewriteDto[]> {
  const response = await request<RewriteDto[]>('GET', '/rewrites');
  return response?.data || [];
}

export async function saveRewrite(createRewriteDto: CreateRewriteDto): Promise<NextDnsResponse<RewriteDto> | null> {
  return request<RewriteDto>('POST', '/rewrites', createRewriteDto);
}

export async function deleteRewriteById(id: string): Promise<NextDnsResponse<RewriteDto> | null> {
  return request<RewriteDto>('DELETE', `/rewrites/${id}`);
}
