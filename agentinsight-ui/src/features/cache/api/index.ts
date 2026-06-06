import { getJson } from '../../../lib/api';

export type CacheMetricSummary = {
  inputTokens: number;
  cachedInputTokens: number;
  uncachedInputTokens: number;
  outputTokens: number;
  totalTokens: number;
  cacheHitRatio: number;
  uncachedInputRatio: number;
  estimatedCacheSavings: number;
  estimatedAdditionalSavingsOpportunity: number;
  pricingStatus: string;
  metricCompleteness: string;
};

export type CacheWarning = {
  severity: string;
  code: string;
  message: string;
  evidence: Record<string, unknown>;
};

export type CacheSessionAnalytics = {
  sessionId: string;
  title: string | null;
  cwd: string | null;
  repositoryId: string;
  model: string;
  createdAtMs: number;
  updatedAtMs: number;
  tokenEventCount: number;
  metrics: CacheMetricSummary;
  dataCompleteness: string;
  warnings: CacheWarning[];
  evidence: Record<string, unknown>;
};

export type CachePerformanceSummary = {
  generatedAt: string;
  dataCompleteness: string;
  totalSessions: number;
  inputTokens: number;
  cachedInputTokens: number;
  uncachedInputTokens: number;
  totalTokens: number;
  cacheHitRatio: number;
  estimatedCacheSavings: number;
  estimatedAdditionalSavingsOpportunity: number;
  warnings: string[];
  sessions: CacheSessionAnalytics[];
};

export type CacheDailyAnalytics = {
  day: string;
  sessions: number;
  inputTokens: number;
  cachedInputTokens: number;
  uncachedInputTokens: number;
  cacheHitRatio: number;
  estimatedCacheSavings: number;
};

export type CacheImpactSummary = {
  currency: string;
  pricingProfileId: string;
  actualCost: number;
  withoutCacheCost: number;
  savings: number;
  savingsPercent: number;
  cacheEfficiency: number;
  inputTokens: number;
  cachedInputTokens: number;
  uncachedInputTokens: number;
  outputTokens: number;
  reasoningOutputTokens: number;
  estimatedTimeSavedMs: number | null;
  dataCompleteness: string;
  generatedAt: string;
};

export type CacheImpactDaily = {
  day: string;
  actualCost: number;
  withoutCacheCost: number;
  savings: number;
  savingsPercent: number;
  cacheEfficiency: number;
  cachedInputTokens: number;
  uncachedInputTokens: number;
};

export type CacheImpactRepository = {
  repositoryKey: string;
  repositoryLabel: string;
  sessions: number;
  actualCost: number;
  withoutCacheCost: number;
  savings: number;
  savingsPercent: number;
  cacheEfficiency: number;
  cachedInputTokens: number;
  uncachedInputTokens: number;
};

export type CacheImpactModel = {
  model: string;
  sessions: number;
  actualCost: number;
  withoutCacheCost: number;
  savings: number;
  savingsPercent: number;
  cacheEfficiency: number;
};

export type CacheImpactSession = {
  sessionId: string;
  title: string | null;
  repositoryLabel: string;
  model: string;
  actualCost: number;
  withoutCacheCost: number;
  savings: number;
  savingsPercent: number;
  cacheEfficiency: number;
  inputTokens: number;
  cachedInputTokens: number;
  uncachedInputTokens: number;
  updatedAt: number;
};

export type CacheImpactSessionPage = {
  items: CacheImpactSession[];
  total: number;
  limit: number;
  offset: number;
};

export type CacheImpactRecommendation = {
  id: string;
  category: string;
  severity: string;
  title: string;
  description: string;
  recommendation: string;
  impactCost: number;
  impactTokens: number;
  affectedSessionIds: string[];
  affectedRepositoryKeys: string[];
};

export async function getCachePerformanceSummary(): Promise<CachePerformanceSummary> {
  return getJson('/cache-performance/summary', 'Failed to load cache performance');
}

export async function getCachePerformanceDaily(): Promise<CacheDailyAnalytics[]> {
  return getJson('/cache-performance/daily', 'Failed to load cache performance trend');
}

export async function getCacheImpactSummary(): Promise<CacheImpactSummary> {
  return getJson('/cache-performance/impact/summary', 'Failed to load cache impact summary');
}

export async function getCacheImpactDaily(): Promise<CacheImpactDaily[]> {
  return getJson('/cache-performance/impact/daily', 'Failed to load cache impact trend');
}

export async function getCacheImpactRepositories(): Promise<CacheImpactRepository[]> {
  return getJson(
    '/cache-performance/impact/repositories?sort=savings&direction=desc&limit=20',
    'Failed to load cache repository impact',
  );
}

export async function getCacheImpactModels(): Promise<CacheImpactModel[]> {
  return getJson('/cache-performance/impact/models', 'Failed to load cache model impact');
}

export async function getCacheImpactSessions(
  sort = 'savings',
  direction = 'desc',
): Promise<CacheImpactSessionPage> {
  const params = new URLSearchParams({
    sort,
    direction,
    limit: '30',
    offset: '0',
  });

  return getJson(
    `/cache-performance/impact/sessions?${params}`,
    'Failed to load cache session impact',
  );
}

export async function getCacheImpactRecommendations(): Promise<CacheImpactRecommendation[]> {
  return getJson(
    '/cache-performance/impact/recommendations',
    'Failed to load cache impact recommendations',
  );
}
