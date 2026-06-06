import { getJson } from '../../../lib/api';

export type RepositoryStat = {
  repository: string;
  sessions: number;
  tokens: number;
  cost: number;
};

export type ModelStat = {
  model: string;
  sessions: number;
  tokens: number;
  cost: number;
};

export type Dashboard = {
  totalSessions: number;
  totalTokens: number;
  estimatedCost: number;
  cacheSavings: number;
  topRepositories: RepositoryStat[];
  models: ModelStat[];
};

export async function getDashboard(): Promise<Dashboard> {
  return getJson('/dashboard', 'Failed to load dashboard');
}
