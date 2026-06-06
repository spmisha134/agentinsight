import { getJson } from '../../../lib/api';

export type OptimizationSignal = {
  id: string;
  category: string;
  severity: string;
  title: string;
  recommendation: string;
  status: string;
  impactTokens: number;
  impactCost: number;
  affectedSessions: string[];
  repositoryId: string;
  model: string;
  generatedAt: string;
  evidence: Record<string, unknown>;
};

export type OptimizationSession = {
  sessionId: string;
  title: string | null;
  cwd: string | null;
  repositoryId: string;
  model: string;
  createdAtMs: number;
  updatedAtMs: number;
  dataCompleteness: string;
  signalCount: number;
  impactTokens: number;
  impactCost: number;
  signals: OptimizationSignal[];
  evidence: Record<string, unknown>;
};

export type UsageOptimizationSummary = {
  generatedAt: string;
  dataCompleteness: string;
  totalSessions: number;
  totalSignals: number;
  highSeveritySignals: number;
  mediumSeveritySignals: number;
  lowSeveritySignals: number;
  totalImpactTokens: number;
  totalImpactCost: number;
  warnings: string[];
  signals: OptimizationSignal[];
  sessions: OptimizationSession[];
};

export type UsageOptimizationDaily = {
  day: string;
  sessions: number;
  signals: number;
  highSeveritySignals: number;
  impactTokens: number;
  impactCost: number;
};

export async function getUsageOptimizationSummary(): Promise<UsageOptimizationSummary> {
  return getJson('/usage-optimization/summary', 'Failed to load usage optimization');
}

export async function getUsageOptimizationDaily(): Promise<UsageOptimizationDaily[]> {
  return getJson('/usage-optimization/daily', 'Failed to load usage optimization trend');
}
