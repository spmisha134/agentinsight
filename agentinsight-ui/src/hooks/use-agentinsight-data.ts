import { useEffect, useState } from 'react';

import {
  CacheDailyAnalytics,
  CacheImpactDaily,
  CacheImpactModel,
  CacheImpactRecommendation,
  CacheImpactRepository,
  CacheImpactSession,
  CacheImpactSummary,
  CachePerformanceSummary,
  getCacheImpactDaily,
  getCacheImpactModels,
  getCacheImpactRecommendations,
  getCacheImpactRepositories,
  getCacheImpactSessions,
  getCacheImpactSummary,
  getCachePerformanceDaily,
  getCachePerformanceSummary,
} from '../features/cache/api';
import {
  CommandAnalyticsSummary,
  CommandDailyAnalytics,
  getCommandAnalyticsDaily,
  getCommandAnalyticsSummary,
} from '../features/commands/api';
import {
  ContextAnalyticsSummary,
  ContextDailyAnalytics,
  getContextAnalyticsDaily,
  getContextAnalyticsSummary,
} from '../features/context/api';
import {
  Dashboard,
  getDashboard,
} from '../features/dashboard/api';
import {
  getLiveUsageSummary,
  LiveUsageSummary,
} from '../features/live/api';
import {
  getUsageOptimizationDaily,
  getUsageOptimizationSummary,
  UsageOptimizationDaily,
  UsageOptimizationSummary,
} from '../features/optimization/api';
import {
  getReplayDaily,
  getReplaySummary,
  ReplayDailyAnalytics,
  ReplaySummary,
} from '../features/replay/api';
import {
  getSessions,
  SessionSummary,
} from '../features/sessions/api';
import {
  getToolAnalyticsDaily,
  getToolAnalyticsSummary,
  ToolAnalyticsSummary,
  ToolDailyAnalytics,
} from '../features/tools/api';

export type AgentInsightData = {
  dashboard: Dashboard | null;
  liveUsage: LiveUsageSummary | null;
  contextAnalytics: ContextAnalyticsSummary | null;
  contextDaily: ContextDailyAnalytics[];
  cachePerformance: CachePerformanceSummary | null;
  cacheDaily: CacheDailyAnalytics[];
  cacheImpact: CacheImpactSummary | null;
  cacheImpactDaily: CacheImpactDaily[];
  cacheImpactRepositories: CacheImpactRepository[];
  cacheImpactModels: CacheImpactModel[];
  cacheImpactSessions: CacheImpactSession[];
  cacheImpactWorstSessions: CacheImpactSession[];
  cacheImpactRecommendations: CacheImpactRecommendation[];
  toolAnalytics: ToolAnalyticsSummary | null;
  toolDaily: ToolDailyAnalytics[];
  commandAnalytics: CommandAnalyticsSummary | null;
  commandDaily: CommandDailyAnalytics[];
  replaySummary: ReplaySummary | null;
  replayDaily: ReplayDailyAnalytics[];
  usageOptimization: UsageOptimizationSummary | null;
  optimizationDaily: UsageOptimizationDaily[];
  sessions: SessionSummary[];
};

export function useAgentInsightData(enabled = true) {
  const [data, setData] = useState<AgentInsightData>({
    dashboard: null,
    liveUsage: null,
    contextAnalytics: null,
    contextDaily: [],
    cachePerformance: null,
    cacheDaily: [],
    cacheImpact: null,
    cacheImpactDaily: [],
    cacheImpactRepositories: [],
    cacheImpactModels: [],
    cacheImpactSessions: [],
    cacheImpactWorstSessions: [],
    cacheImpactRecommendations: [],
    toolAnalytics: null,
    toolDaily: [],
    commandAnalytics: null,
    commandDaily: [],
    replaySummary: null,
    replayDaily: [],
    usageOptimization: null,
    optimizationDaily: [],
    sessions: [],
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);

    Promise.all([
      getDashboard(),
      getSessions(),
      getLiveUsageSummary(),
      getContextAnalyticsSummary(),
      getContextAnalyticsDaily(),
      getCachePerformanceSummary(),
      getCachePerformanceDaily(),
      getCacheImpactSummary(),
      getCacheImpactDaily(),
      getCacheImpactRepositories(),
      getCacheImpactModels(),
      getCacheImpactSessions(),
      getCacheImpactSessions('cacheEfficiency', 'asc'),
      getCacheImpactRecommendations(),
      getToolAnalyticsSummary(),
      getToolAnalyticsDaily(),
      getCommandAnalyticsSummary(),
      getCommandAnalyticsDaily(),
      getReplaySummary(),
      getReplayDaily(),
      getUsageOptimizationSummary(),
      getUsageOptimizationDaily(),
    ])
      .then(([
        dashboard,
        sessions,
        liveUsage,
        contextAnalytics,
        contextDaily,
        cachePerformance,
        cacheDaily,
        cacheImpact,
        cacheImpactDaily,
        cacheImpactRepositories,
        cacheImpactModels,
        cacheImpactSessions,
        cacheImpactWorstSessions,
        cacheImpactRecommendations,
        toolAnalytics,
        toolDaily,
        commandAnalytics,
        commandDaily,
        replaySummary,
        replayDaily,
        usageOptimization,
        optimizationDaily,
      ]) => {
        setData({
          dashboard,
          liveUsage,
          contextAnalytics,
          contextDaily,
          cachePerformance,
          cacheDaily,
          cacheImpact,
          cacheImpactDaily,
          cacheImpactRepositories,
          cacheImpactModels,
          cacheImpactSessions: cacheImpactSessions.items,
          cacheImpactWorstSessions: cacheImpactWorstSessions.items,
          cacheImpactRecommendations,
          toolAnalytics,
          toolDaily,
          commandAnalytics,
          commandDaily,
          replaySummary,
          replayDaily,
          usageOptimization,
          optimizationDaily,
          sessions,
        });
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [enabled]);

  return {
    ...data,
    error,
    loading,
    load,
  };
}
