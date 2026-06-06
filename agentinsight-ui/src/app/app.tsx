import { lazy, Suspense, useEffect, useState } from 'react';

import { AppLayout } from '../components/layouts/app-layout';
import { LoadingShell } from '../components/ui';
import { Screen } from '../config/navigation';
import { getActiveProvider, getProviders, ProviderDescriptor, ProviderInstance } from '../features/providers/api';
import { useAgentInsightData } from '../hooks/use-agentinsight-data';

const AnalyticsScreen = lazy(() => import('../features/analytics/components/analytics-screen').then((module) => ({ default: module.AnalyticsScreen })));
const CacheScreen = lazy(() => import('../features/cache/components/cache-screen').then((module) => ({ default: module.CacheScreen })));
const CommandScreen = lazy(() => import('../features/commands/components/command-screen').then((module) => ({ default: module.CommandScreen })));
const ContextScreen = lazy(() => import('../features/context/components/context-screen').then((module) => ({ default: module.ContextScreen })));
const OverviewScreen = lazy(() => import('../features/dashboard/components/overview-screen').then((module) => ({ default: module.OverviewScreen })));
const LiveScreen = lazy(() => import('../features/live/components/live-screen').then((module) => ({ default: module.LiveScreen })));
const OptimizationScreen = lazy(() => import('../features/optimization/components/optimization-screen').then((module) => ({ default: module.OptimizationScreen })));
const ProviderSetupScreen = lazy(() => import('../features/providers/components/provider-setup-screen').then((module) => ({ default: module.ProviderSetupScreen })));
const ReplayScreen = lazy(() => import('../features/replay/components/replay-screen').then((module) => ({ default: module.ReplayScreen })));
const SessionsScreen = lazy(() => import('../features/sessions/components/sessions-screen').then((module) => ({ default: module.SessionsScreen })));
const SettingsScreen = lazy(() => import('../features/settings/components/settings-screen').then((module) => ({ default: module.SettingsScreen })));
const ToolScreen = lazy(() => import('../features/tools/components/tool-screen').then((module) => ({ default: module.ToolScreen })));

export function App() {
  const [activeProvider, setActiveProvider] = useState<ProviderInstance | null | undefined>(undefined);
  const [providers, setProviders] = useState<ProviderDescriptor[]>([]);
  const [providerError, setProviderError] = useState<string | null>(null);
  const [providerVersion, setProviderVersion] = useState(0);
  const {
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
    sessions,
    error,
    loading,
    load,
  } = useAgentInsightData(activeProvider !== undefined && activeProvider !== null);
  const [screen, setScreen] = useState<Screen>('overview');
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  useEffect(() => {
    Promise.all([getProviders(), getActiveProvider()])
      .then(([registry, active]) => {
        setProviders(registry);
        setActiveProvider(active);
      })
      .catch((err) => {
        setProviderError(err.message);
        setActiveProvider(null);
      });
  }, [providerVersion]);

  if (activeProvider === undefined) {
    return (
      <main className="standalone-state">
        <LoadingShell />
      </main>
    );
  }

  if (providerError) {
    return (
      <main className="standalone-state">
        <div className="error">{providerError}</div>
      </main>
    );
  }

  if (!activeProvider) {
    return (
      <Suspense fallback={<main className="standalone-state"><LoadingShell /></main>}>
        <ProviderSetupScreen
          providers={providers}
          onActivated={() => setProviderVersion((version) => version + 1)}
        />
      </Suspense>
    );
  }

  if (error && !dashboard) {
    return (
      <main className="standalone-state">
        <div className="error">{error}</div>
      </main>
    );
  }

  if (!dashboard) {
    return (
      <main className="standalone-state">
        <LoadingShell />
      </main>
    );
  }

  const selectScreen = (nextScreen: Screen) => {
    setScreen(nextScreen);
    setMobileNavOpen(false);
  };

  return (
    <AppLayout
      error={error}
      loading={loading}
      screen={screen}
      mobileNavOpen={mobileNavOpen}
      onRefresh={load}
      onSelectScreen={selectScreen}
      onToggleMobileNav={() => setMobileNavOpen((value) => !value)}
      onCloseMobileNav={() => setMobileNavOpen(false)}
    >
      <Suspense fallback={<LoadingShell />}>
        {screen === 'overview' && (
          <OverviewScreen
            dashboard={dashboard}
            liveUsage={liveUsage}
            sessions={sessions}
          />
        )}
        {screen === 'live' && (
          <LiveScreen liveUsage={liveUsage} />
        )}
        {screen === 'context' && (
          <ContextScreen
            contextAnalytics={contextAnalytics}
            contextDaily={contextDaily}
          />
        )}
        {screen === 'cache' && (
          <CacheScreen
            cachePerformance={cachePerformance}
            cacheDaily={cacheDaily}
            cacheImpact={cacheImpact}
            cacheImpactDaily={cacheImpactDaily}
            cacheImpactRepositories={cacheImpactRepositories}
            cacheImpactModels={cacheImpactModels}
            cacheImpactSessions={cacheImpactSessions}
            cacheImpactWorstSessions={cacheImpactWorstSessions}
            cacheImpactRecommendations={cacheImpactRecommendations}
          />
        )}
        {screen === 'tools' && (
          <ToolScreen
            toolAnalytics={toolAnalytics}
            toolDaily={toolDaily}
          />
        )}
        {screen === 'commands' && (
          <CommandScreen
            commandAnalytics={commandAnalytics}
            commandDaily={commandDaily}
          />
        )}
        {screen === 'replay' && (
          <ReplayScreen
            replaySummary={replaySummary}
            replayDaily={replayDaily}
          />
        )}
        {screen === 'optimization' && (
          <OptimizationScreen
            optimization={usageOptimization}
            optimizationDaily={optimizationDaily}
          />
        )}
        {screen === 'analytics' && (
          <AnalyticsScreen dashboard={dashboard} />
        )}
        {screen === 'sessions' && (
          <SessionsScreen sessions={sessions} />
        )}
        {screen === 'settings' && <SettingsScreen activeProvider={activeProvider} providers={providers} onProviderChanged={() => setProviderVersion((version) => version + 1)} />}
      </Suspense>
    </AppLayout>
  );
}
