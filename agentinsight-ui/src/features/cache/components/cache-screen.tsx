import { useState } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ChartSurface, ContextTab, Metric } from '../../../components/ui';
import { compact, money } from '../../../lib/format';
import { percent, percentPoint, tooltipStyle } from '../../../utils/analytics-format';
import {
  CacheDailyAnalytics,
  CacheImpactDaily,
  CacheImpactModel,
  CacheImpactRecommendation,
  CacheImpactRepository,
  CacheImpactSession,
  CacheImpactSummary,
  CachePerformanceSummary,
  CacheSessionAnalytics,
} from '../api';

type CachePanel = 'impact' | 'trend' | 'repositories' | 'models' | 'sessions' | 'recommendations' | 'warnings';

export function CacheScreen({
  cachePerformance,
  cacheDaily,
  cacheImpact,
  cacheImpactDaily,
  cacheImpactRepositories,
  cacheImpactModels,
  cacheImpactSessions,
  cacheImpactWorstSessions,
  cacheImpactRecommendations,
}: {
  cachePerformance: CachePerformanceSummary | null;
  cacheDaily: CacheDailyAnalytics[];
  cacheImpact: CacheImpactSummary | null;
  cacheImpactDaily: CacheImpactDaily[];
  cacheImpactRepositories: CacheImpactRepository[];
  cacheImpactModels: CacheImpactModel[];
  cacheImpactSessions: CacheImpactSession[];
  cacheImpactWorstSessions: CacheImpactSession[];
  cacheImpactRecommendations: CacheImpactRecommendation[];
}) {
  const [activePanel, setActivePanel] = useState<CachePanel>('impact');
  if (!cachePerformance) return <div className="empty">Cache performance is not available.</div>;

  const warnings = cachePerformance.sessions
    .flatMap((session) => session.warnings.map((warning) => ({ ...warning, sessionId: session.sessionId })))
    .slice(0, 20);

  return (
    <>
      <section className="metrics">
        <Metric
          title="Actual Cost"
          value={money(cacheImpact?.actualCost ?? 0)}
          trend={cacheImpact ? `Data ${cacheImpact.dataCompleteness.toLowerCase()}` : 'Impact unavailable'}
        />
        <Metric
          title="Without Cache"
          value={money(cacheImpact?.withoutCacheCost ?? 0)}
          trend={cacheImpact ? `Pricing ${cacheImpact.pricingProfileId}` : 'Pricing unavailable'}
        />
        <Metric
          title="Savings"
          value={money(cacheImpact?.savings ?? cachePerformance.estimatedCacheSavings)}
          trend={cacheImpact ? percentPoint(cacheImpact.savingsPercent) : 'Estimated from cache hits'}
        />
        <Metric
          title="Cache Efficiency"
          value={cacheImpact ? percentPoint(cacheImpact.cacheEfficiency) : percent(cachePerformance.cacheHitRatio)}
          trend={`${compact(cachePerformance.cachedInputTokens)} cached input`}
        />
      </section>

      <section className="context-accordion">
        <div className="context-tabs cache-tabs" role="tablist" aria-label="Cache performance sections">
          <ContextTab
            label="Impact"
            meta={cacheImpact ? money(cacheImpact.savings) : 'Unavailable'}
            active={activePanel === 'impact'}
            onClick={() => setActivePanel('impact')}
          />
          <ContextTab
            label="Cache Trend"
            meta={`${cacheDaily.length} days`}
            active={activePanel === 'trend'}
            onClick={() => setActivePanel('trend')}
          />
          <ContextTab
            label="Repositories"
            meta={`${cacheImpactRepositories.length} repos`}
            active={activePanel === 'repositories'}
            onClick={() => setActivePanel('repositories')}
          />
          <ContextTab
            label="Models"
            meta={`${cacheImpactModels.length} models`}
            active={activePanel === 'models'}
            onClick={() => setActivePanel('models')}
          />
          <ContextTab
            label="Sessions"
            meta={`${cacheImpactSessions.length} sessions`}
            active={activePanel === 'sessions'}
            onClick={() => setActivePanel('sessions')}
          />
          <ContextTab
            label="Recommendations"
            meta={`${cacheImpactRecommendations.length} items`}
            active={activePanel === 'recommendations'}
            onClick={() => setActivePanel('recommendations')}
          />
          <ContextTab
            label="Warnings"
            meta={`${warnings.length} warnings`}
            active={activePanel === 'warnings'}
            onClick={() => setActivePanel('warnings')}
          />
        </div>
        <div className="context-accordion-body">
          {activePanel === 'impact' && (
            <>
              <div className="panel-meta">
                <span>Generated {cacheImpact ? new Date(cacheImpact.generatedAt).toLocaleString() : new Date(cachePerformance.generatedAt).toLocaleString()}</span>
                <span>Data {(cacheImpact?.dataCompleteness ?? cachePerformance.dataCompleteness).replace('_', ' ').toLowerCase()}</span>
                <span>{compact(cacheImpact?.uncachedInputTokens ?? cachePerformance.uncachedInputTokens)} uncached input</span>
                {cacheImpact?.estimatedTimeSavedMs === null && <span>Time saved unavailable</span>}
              </div>
              {cacheImpactDaily.length === 0 ? (
                <div className="empty">No cache impact data is available yet.</div>
              ) : (
                <ChartSurface empty={cacheImpactDaily.length === 0} emptyText="No cache impact data is available yet.">
                  <ResponsiveContainer width="100%" height={300}>
                    <AreaChart data={cacheImpactDaily}>
                      <defs>
                        <linearGradient id="cacheImpactSavingsFill" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="var(--chart-2)" stopOpacity={0.45} />
                          <stop offset="95%" stopColor="var(--chart-2)" stopOpacity={0.03} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid vertical={false} stroke="var(--border)" />
                      <XAxis dataKey="day" tickLine={false} axisLine={false} />
                      <YAxis tickLine={false} axisLine={false} tickFormatter={(v) => money(Number(v))} />
                      <Tooltip formatter={(v, key) => key === 'cacheEfficiency' ? percentPoint(Number(v)) : money(Number(v))} contentStyle={tooltipStyle} />
                      <Legend />
                      <Area dataKey="withoutCacheCost" name="Without cache" type="monotone" fill="transparent" stroke="var(--chart-4)" strokeWidth={2} />
                      <Area dataKey="actualCost" name="Actual cost" type="monotone" fill="transparent" stroke="var(--chart-1)" strokeWidth={2} />
                      <Area dataKey="savings" name="Savings" type="monotone" fill="url(#cacheImpactSavingsFill)" stroke="var(--chart-2)" strokeWidth={2} />
                    </AreaChart>
                  </ResponsiveContainer>
                </ChartSurface>
              )}
            </>
          )}
          {activePanel === 'trend' && (
            <>
              <div className="panel-meta">
                <span>Generated {new Date(cachePerformance.generatedAt).toLocaleString()}</span>
                <span>Data {cachePerformance.dataCompleteness.replace('_', ' ')}</span>
                <span>Pricing {cachePricingStatus(cachePerformance)}</span>
                {cachePerformance.warnings.length > 0 && <span>{cachePerformance.warnings.length} warning types</span>}
              </div>
              {cacheDaily.length === 0 ? (
                <div className="empty">No cache token events are available yet.</div>
              ) : (
                <ChartSurface empty={cacheDaily.length === 0} emptyText="No cache token events are available yet.">
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={cacheDaily}>
                      <CartesianGrid vertical={false} stroke="var(--border)" />
                      <XAxis dataKey="day" tickLine={false} axisLine={false} />
                      <YAxis tickLine={false} axisLine={false} />
                      <Tooltip formatter={(v) => compact(Number(v))} contentStyle={tooltipStyle} />
                      <Legend />
                      <Bar dataKey="cachedInputTokens" name="Cached input" stackId="cache" fill="var(--chart-2)" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="uncachedInputTokens" name="Uncached input" stackId="cache" fill="var(--chart-4)" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </ChartSurface>
              )}
            </>
          )}
          {activePanel === 'repositories' && (
            cacheImpactRepositories.length === 0 ? (
              <div className="empty">Repository cache impact will appear after sessions with token usage and pricing are available.</div>
            ) : (
              <div className="table cache-impact-table">
                <div className="row header"><span>Repository</span><span>Sessions</span><span>Actual</span><span>No Cache</span><span>Savings</span><span>Efficiency</span></div>
                {cacheImpactRepositories.map((repository) => (
                  <div className="row" key={repository.repositoryKey}>
                    <span className="title" title={repository.repositoryKey}>{repository.repositoryLabel}</span>
                    <span>{compact(repository.sessions)}</span>
                    <span>{money(repository.actualCost)}</span>
                    <span>{money(repository.withoutCacheCost)}</span>
                    <span>{money(repository.savings)} · {percentPoint(repository.savingsPercent)}</span>
                    <span>{percentPoint(repository.cacheEfficiency)}</span>
                  </div>
                ))}
              </div>
            )
          )}
          {activePanel === 'models' && (
            cacheImpactModels.length === 0 ? (
              <div className="empty">Model cache impact will appear after priced token usage events are imported.</div>
            ) : (
              <div className="table cache-impact-table">
                <div className="row header"><span>Model</span><span>Sessions</span><span>Actual</span><span>No Cache</span><span>Savings</span><span>Efficiency</span></div>
                {cacheImpactModels.map((model) => (
                  <div className="row" key={model.model}>
                    <span className="title">{model.model}</span>
                    <span>{compact(model.sessions)}</span>
                    <span>{money(model.actualCost)}</span>
                    <span>{money(model.withoutCacheCost)}</span>
                    <span>{money(model.savings)} · {percentPoint(model.savingsPercent)}</span>
                    <span>{percentPoint(model.cacheEfficiency)}</span>
                  </div>
                ))}
              </div>
            )
          )}
          {activePanel === 'sessions' && (
            cacheImpactSessions.length === 0 && cacheImpactWorstSessions.length === 0 ? (
              <div className="empty">Session cache impact will appear after sessions with token usage events are imported.</div>
            ) : (
              <>
                <div className="panel-meta"><span>Top savings</span><span>Lowest cache efficiency</span></div>
                <div className="table cache-impact-table">
                  <div className="row header"><span>Session</span><span>Repo</span><span>Actual</span><span>No Cache</span><span>Savings</span><span>Efficiency</span></div>
                  {cacheImpactSessions.map((session) => <CacheImpactSessionRow session={session} key={`top-${session.sessionId}`} />)}
                </div>
                <div className="table cache-impact-table cache-impact-secondary">
                  <div className="row header">
                    <span>Low Efficiency</span>
                    <span>Repo</span>
                    <span>Actual</span>
                    <span>No Cache</span>
                    <span>Uncached</span>
                    <span>Efficiency</span>
                  </div>
                  {cacheImpactWorstSessions.map((session) => (
                    <div className="row" key={`worst-${session.sessionId}`}>
                      <span className="title" title={session.sessionId}>{session.title || session.sessionId}</span>
                      <span>{session.repositoryLabel}</span>
                      <span>{money(session.actualCost)}</span>
                      <span>{money(session.withoutCacheCost)}</span>
                      <span>{compact(session.uncachedInputTokens)}</span>
                      <span>{percentPoint(session.cacheEfficiency)}</span>
                    </div>
                  ))}
                </div>
              </>
            )
          )}
          {activePanel === 'recommendations' && (
            cacheImpactRecommendations.length === 0 ? (
              <div className="empty">No measurable cache impact recommendations are available.</div>
            ) : (
              <div className="reference-list">
                {cacheImpactRecommendations.map((recommendation) => (
                  <div className="reference-item" key={recommendation.id}>
                    <strong>{recommendation.title}</strong>
                    <span>{recommendation.severity.toLowerCase()} · {money(recommendation.impactCost)} · {compact(recommendation.impactTokens)} tokens</span>
                    <p>{recommendation.recommendation}</p>
                  </div>
                ))}
              </div>
            )
          )}
          {activePanel === 'warnings' && (
            warnings.length === 0 ? (
              <div className="empty">No low cache ratio or parser warnings detected.</div>
            ) : (
              <div className="reference-list">
                {warnings.map((warning, index) => (
                  <div className="reference-item" key={`${warning.sessionId}-${warning.code}-${index}`}>
                    <strong>{warning.code.replace(/_/g, ' ')}</strong>
                    <span>{warning.severity} · session {warning.sessionId}</span>
                  </div>
                ))}
              </div>
            )
          )}
        </div>
      </section>
    </>
  );
}


function CacheSessionRow({ session }: { session: CacheSessionAnalytics }) {
  return (
    <div className="row">
      <span className="title" title={session.warnings.map((warning) => warning.code).join(', ') || 'No warnings'}>{session.sessionId}</span>
      <span>{session.repositoryId}</span>
      <span>{session.model}</span>
      <span>{percent(session.metrics.cacheHitRatio)}</span>
      <span>{compact(session.metrics.uncachedInputTokens)}</span>
      <span>{session.dataCompleteness.replace('_', ' ')} · {session.metrics.metricCompleteness}</span>
    </div>
  );
}

function CacheImpactSessionRow({ session }: { session: CacheImpactSession }) {
  return (
    <div className="row">
      <span className="title" title={session.sessionId}>{session.title || session.sessionId}</span>
      <span>{session.repositoryLabel}</span>
      <span>{money(session.actualCost)}</span>
      <span>{money(session.withoutCacheCost)}</span>
      <span>{money(session.savings)} · {percentPoint(session.savingsPercent)}</span>
      <span>{percentPoint(session.cacheEfficiency)}</span>
    </div>
  );
}


function cachePricingStatus(summary: CachePerformanceSummary) {
  const first = summary.sessions.find((session) => session.metrics.pricingStatus);
  return first?.metrics.pricingStatus ?? 'not available';
}
