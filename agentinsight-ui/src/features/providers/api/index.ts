import { getJson, postJson } from '../../../lib/api';

export type ProviderType = 'CODEX' | 'CLAUDE_CODE' | 'GEMINI_CLI' | 'CUSTOM';
export type ProviderSupportStatus = 'SUPPORTED' | 'EXPERIMENTAL' | 'PLANNED' | 'UNAVAILABLE';
export type ProviderHealthStatus = 'READY' | 'PARTIAL' | 'NOT_FOUND' | 'INVALID' | 'UNSUPPORTED';

export type ProviderDescriptor = {
  providerType: ProviderType;
  displayName: string;
  supportStatus: ProviderSupportStatus;
  defaultHomePath: string;
  capabilities: string[];
};

export type ProviderHealth = {
  status: ProviderHealthStatus;
  sessionsFound: number;
  warnings: string[];
  errors: string[];
  capabilities: string[];
};

export type ProviderDiscoveryResult = {
  providerType: ProviderType;
  displayName: string;
  homePath: string;
  detected: boolean;
  supportStatus: ProviderSupportStatus;
  health: ProviderHealth;
};

export type ProviderInstance = {
  id: string;
  providerType: ProviderType;
  displayName: string;
  homePath: string;
  active: boolean;
  healthStatus: ProviderHealthStatus;
  supportStatus: ProviderSupportStatus;
  lastValidatedAtMs: number | null;
  createdAtMs: number;
  updatedAtMs: number;
};

export type ProviderSelectionRequest = {
  providerType: ProviderType;
  displayName?: string;
  homePath: string;
};

export async function getProviders(): Promise<ProviderDescriptor[]> {
  return getJson('/providers', 'Failed to load providers');
}

export async function getActiveProvider(): Promise<ProviderInstance | null> {
  return getJson('/providers/active', 'Failed to load active provider');
}

export async function discoverProviders(): Promise<ProviderDiscoveryResult[]> {
  return getJson('/providers/discover', 'Failed to discover providers');
}

export async function validateProvider(request: ProviderSelectionRequest): Promise<ProviderHealth> {
  return postJson('/providers/validate', request, 'Failed to validate provider');
}

export async function setActiveProvider(request: ProviderSelectionRequest): Promise<ProviderInstance> {
  return postJson('/providers/active', request, 'Failed to set active provider');
}
