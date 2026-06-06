import { getJson } from '../../../lib/api';

export type ToolMetricSummary = {
  totalToolCalls: number;
  successfulToolCalls: number;
  failedToolCalls: number;
  partialToolCalls: number;
  retryCount: number;
  totalOutputBytes: number;
  averageLatencyMs: number | null;
  failureRate: number;
  retryRate: number;
  metricCompleteness: string;
};

export type ToolUsageStat = {
  toolName: string;
  toolType: string;
  calls: number;
  failures: number;
  failureRate: number;
  outputBytes: number;
  averageLatencyMs: number | null;
};

export type ToolWarning = {
  severity: string;
  code: string;
  message: string;
  evidence: Record<string, unknown>;
};

export type ToolSessionAnalytics = {
  sessionId: string;
  title: string | null;
  cwd: string | null;
  repositoryId: string;
  model: string;
  createdAtMs: number;
  updatedAtMs: number;
  metrics: ToolMetricSummary;
  dataCompleteness: string;
  tools: ToolUsageStat[];
  warnings: ToolWarning[];
  evidence: Record<string, unknown>;
};

export type ToolAnalyticsSummary = {
  generatedAt: string;
  dataCompleteness: string;
  totalSessions: number;
  totalToolCalls: number;
  failedToolCalls: number;
  retryCount: number;
  totalOutputBytes: number;
  averageLatencyMs: number | null;
  failureRate: number;
  retryRate: number;
  warnings: string[];
  tools: ToolUsageStat[];
  sessions: ToolSessionAnalytics[];
};

export type ToolDailyAnalytics = {
  day: string;
  sessions: number;
  toolCalls: number;
  failedToolCalls: number;
  retryCount: number;
  failureRate: number;
};

export async function getToolAnalyticsSummary(): Promise<ToolAnalyticsSummary> {
  return getJson('/tool-call-analytics/summary', 'Failed to load tool analytics');
}

export async function getToolAnalyticsDaily(): Promise<ToolDailyAnalytics[]> {
  return getJson('/tool-call-analytics/daily', 'Failed to load tool analytics trend');
}
