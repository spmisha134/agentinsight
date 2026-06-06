import { getJson } from '../../../lib/api';

export type ContextMetricSummary = {
  totalContextTokens: number;
  repeatedContextTokens: number;
  cacheableContextTokens: number;
  contextGrowthTokens: number;
  usefulContextRatio: number;
  repeatedContextRatio: number;
  metricCompleteness: string;
};

export type MemoryReference = {
  type: string;
  label: string;
  lineNumber: number;
  confidence: string;
};

export type ContextWarning = {
  severity: string;
  code: string;
  message: string;
  evidence: Record<string, unknown>;
};

export type ContextSessionAnalytics = {
  sessionId: string;
  title: string | null;
  cwd: string | null;
  repositoryId: string;
  model: string;
  createdAtMs: number;
  updatedAtMs: number;
  promptSegments: number;
  memoryReferenceCount: number;
  metrics: ContextMetricSummary;
  dataCompleteness: string;
  memoryReferences: MemoryReference[];
  warnings: ContextWarning[];
  evidence: Record<string, unknown>;
};

export type ContextAnalyticsSummary = {
  generatedAt: string;
  dataCompleteness: string;
  totalSessions: number;
  totalContextTokens: number;
  repeatedContextTokens: number;
  cacheableContextTokens: number;
  usefulContextRatio: number;
  repeatedContextRatio: number;
  warnings: string[];
  sessions: ContextSessionAnalytics[];
};

export type ContextDailyAnalytics = {
  day: string;
  sessions: number;
  totalContextTokens: number;
  repeatedContextTokens: number;
  cacheableContextTokens: number;
  repeatedContextRatio: number;
};

export async function getContextAnalyticsSummary(): Promise<ContextAnalyticsSummary> {
  return getJson('/context-memory-analytics/summary', 'Failed to load context analytics');
}

export async function getContextAnalyticsDaily(): Promise<ContextDailyAnalytics[]> {
  return getJson('/context-memory-analytics/daily', 'Failed to load context analytics trend');
}
