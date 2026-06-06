import { API_BASE, getJson } from '../../../lib/api';

export type ReplayTimelineEvent = {
  id: string;
  sessionId: string;
  sequence: number;
  lineNumber: number;
  eventType: string;
  sourceType: string;
  actor: string;
  title: string;
  contentPreview: string;
  contentHash: string | null;
  redacted: boolean;
  eventTimeMs: number | null;
  status: string;
  toolName: string | null;
  commandPreview: string | null;
  repositoryId: string;
  branch: string;
  filePath: string | null;
  model: string;
  inputTokens: number | null;
  cachedInputTokens: number | null;
  outputTokens: number | null;
  totalTokens: number | null;
  estimatedCost: number | null;
  evidence: Record<string, unknown>;
};

export type ReplayWarning = {
  severity: string;
  code: string;
  message: string;
  evidence: Record<string, unknown>;
};

export type ReplayMetricSummary = {
  totalEvents: number;
  messageEvents: number;
  toolEvents: number;
  commandEvents: number;
  fileEvents: number;
  tokenEvents: number;
  totalTokens: number;
  cachedInputTokens: number;
  estimatedCost: number;
  metricCompleteness: string;
};

export type ReplaySession = {
  sessionId: string;
  title: string | null;
  cwd: string | null;
  repositoryId: string;
  branch: string;
  model: string;
  createdAtMs: number;
  updatedAtMs: number;
  metrics: ReplayMetricSummary;
  dataCompleteness: string;
  page: number;
  size: number;
  totalEvents: number;
  events: ReplayTimelineEvent[];
  warnings: ReplayWarning[];
  evidence: Record<string, unknown>;
};

export type ReplaySummary = {
  generatedAt: string;
  dataCompleteness: string;
  totalSessions: number;
  totalEvents: number;
  sessionsWithReplay: number;
  malformedLines: number;
  totalTokens: number;
  estimatedCost: number;
  warnings: string[];
  sessions: ReplaySession[];
};

export type ReplayDailyAnalytics = {
  day: string;
  sessions: number;
  events: number;
  messageEvents: number;
  toolEvents: number;
  commandEvents: number;
  totalTokens: number;
  estimatedCost: number;
};

export async function getReplaySummary(): Promise<ReplaySummary> {
  return getJson('/agent-session-replay/summary', 'Failed to load session replay summary');
}

export async function getReplayDaily(): Promise<ReplayDailyAnalytics[]> {
  return getJson('/agent-session-replay/daily', 'Failed to load session replay trend');
}

export async function getReplaySession(
  sessionId: string,
  options: {
    showPrompts?: boolean;
    showOutputs?: boolean;
    showFilePaths?: boolean;
  } = {},
): Promise<ReplaySession> {
  const params = new URLSearchParams({
    page: '0',
    size: '80',
    showPrompts: String(options.showPrompts ?? false),
    showOutputs: String(options.showOutputs ?? false),
    showFilePaths: String(options.showFilePaths ?? false),
  });

  const response = await fetch(
    `${API_BASE}/agent-session-replay/sessions/${encodeURIComponent(sessionId)}?${params}`,
  );

  if (!response.ok) {
    throw new Error('Failed to load session replay');
  }

  return response.json();
}
