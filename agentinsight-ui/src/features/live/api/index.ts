import { getJson } from '../../../lib/api';

export type LiveSessionStatus = 'ACTIVE' | 'STALE' | 'INACTIVE' | 'UNKNOWN';

export type LiveSessionState = {
  sessionId: string;
  title: string | null;
  cwd: string | null;
  repositoryUrl: string | null;
  branch: string | null;
  model: string;
  status: LiveSessionStatus;
  statusReason: string;
  confidence: string;
  latestActivityMs: number;
  threadUpdatedAtMs: number;
  rolloutModifiedAtMs: number | null;
  rolloutSizeBytes: number | null;
  readFreshnessMs: number | null;
  dataCompleteness: string;
  warnings: string[];
  evidence: Record<string, unknown>;
};

export type LiveUsageSummary = {
  generatedAt: string;
  dataCompleteness: string;
  totalSessions: number;
  activeSessions: number;
  staleSessions: number;
  inactiveSessions: number;
  unknownSessions: number;
  warnings: string[];
  sessions: LiveSessionState[];
};

export async function getLiveUsageSummary(): Promise<LiveUsageSummary> {
  return getJson('/live-usage-monitoring/summary', 'Failed to load live usage summary');
}
