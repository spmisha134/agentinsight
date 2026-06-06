import { getJson } from '../../../lib/api';

export type CommandMetricSummary = {
  totalCommands: number;
  successfulCommands: number;
  failedCommands: number;
  unknownCommands: number;
  retryCount: number;
  totalDurationMs: number;
  averageDurationMs: number | null;
  totalStdoutBytes: number;
  totalStderrBytes: number;
  successRate: number;
  failureRate: number;
  retryRate: number;
  metricCompleteness: string;
};

export type CommandCategoryStat = {
  category: string;
  commands: number;
  successes: number;
  failures: number;
  retries: number;
  failureRate: number;
  averageDurationMs: number | null;
};

export type CommandEvent = {
  sessionId: string;
  lineNumber: number;
  toolCallId: string | null;
  commandPreview: string;
  commandHash: string;
  commandExecutable: string;
  cwd: string | null;
  category: string;
  status: string;
  exitCode: number | null;
  startedAtMs: number | null;
  completedAtMs: number | null;
  durationMs: number | null;
  stdoutSizeBytes: number;
  stderrSizeBytes: number;
  riskLevel: string;
  riskReason: string;
  evidence: Record<string, unknown>;
};

export type CommandWarning = {
  severity: string;
  code: string;
  message: string;
  evidence: Record<string, unknown>;
};

export type CommandSessionAnalytics = {
  sessionId: string;
  title: string | null;
  cwd: string | null;
  repositoryId: string;
  model: string;
  createdAtMs: number;
  updatedAtMs: number;
  metrics: CommandMetricSummary;
  dataCompleteness: string;
  categories: CommandCategoryStat[];
  commands: CommandEvent[];
  warnings: CommandWarning[];
  evidence: Record<string, unknown>;
};

export type CommandAnalyticsSummary = {
  generatedAt: string;
  dataCompleteness: string;
  totalSessions: number;
  totalCommands: number;
  failedCommands: number;
  retryCount: number;
  totalDurationMs: number;
  averageDurationMs: number | null;
  successRate: number;
  failureRate: number;
  retryRate: number;
  warnings: string[];
  categories: CommandCategoryStat[];
  sessions: CommandSessionAnalytics[];
};

export type CommandDailyAnalytics = {
  day: string;
  sessions: number;
  commands: number;
  failedCommands: number;
  retryCount: number;
  totalDurationMs: number;
  failureRate: number;
};

export async function getCommandAnalyticsSummary(): Promise<CommandAnalyticsSummary> {
  return getJson('/command-analytics/summary', 'Failed to load command analytics');
}

export async function getCommandAnalyticsDaily(): Promise<CommandDailyAnalytics[]> {
  return getJson('/command-analytics/daily', 'Failed to load command analytics trend');
}
