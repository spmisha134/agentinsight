import { API_BASE } from '../../../lib/api';

export type CostBreakdown = {
  inputCost: number;
  cachedInputCost: number;
  outputCost: number;
  totalCost: number;
  cacheSavings: number;
};

export type SessionSummary = {
  id: string;
  title: string;
  cwd: string;
  repositoryUrl: string | null;
  branch: string | null;
  model: string;
  createdAtMs: number;
  updatedAtMs: number;
  tokensUsed: number;
  cost: CostBreakdown;
};

type SessionPage = {
  items: SessionSummary[];
  page: number;
  size: number;
  total: number;
};

export async function getSessions(): Promise<SessionSummary[]> {
  const response = await fetch(`${API_BASE}/sessions`);

  if (!response.ok) {
    throw new Error('Failed to load sessions');
  }

  const payload = (await response.json()) as SessionSummary[] | SessionPage;

  return Array.isArray(payload) ? payload : payload.items;
}
