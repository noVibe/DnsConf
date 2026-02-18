export interface Item {
  value: string;
  description: string;
  created_at?: string;
}

export interface GatewayListDto {
  id: string;
  count: number;
  created_at: string;
  description: string;
  items: Item[];
  name: string;
  type: string;
  updated_at: string;
}

export interface GatewayRuleDto {
  id: string;
  name: string;
  description: string;
  created_at: string;
  traffic: string;
  precedence: number;
  enabled: boolean;
}

export interface CloudflareApiResponse<T> {
  result: T;
  success: boolean;
  errors: Array<{ code: number; message: string }>;
  messages: string[];
}

export interface CreateListRequest {
  name: string;
  type: string;
  description: string;
  items: Item[];
}

export interface RuleSettings {
  override_ips: string[];
}

export interface CreateRuleRequest {
  name: string;
  description: string;
  action: string;
  filters: string[];
  traffic: string;
  precedence?: number;
  rule_settings?: RuleSettings;
  enabled: boolean;
}
