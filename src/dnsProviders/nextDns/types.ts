export interface DenyDto {
  id: string;
  active: boolean;
}

export interface RewriteDto {
  id: string;
  name: string;
  content: string;
}

export interface CreateDenyDto {
  id: string;
  active: boolean;
}

export interface CreateRewriteDto {
  name: string;
  content: string;
}

export interface NextDnsResponse<T> {
  data?: T;
  errors?: Array<{ code: number; message: string }>;
}
