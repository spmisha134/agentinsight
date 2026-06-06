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
import { compact } from '../../../lib/format';
import { percent, tooltipStyle } from '../../../utils/analytics-format';
import { ContextAnalyticsSummary, ContextDailyAnalytics, ContextSessionAnalytics } from '../api';

type ContextAccordion = 'trend' | 'references' | 'sessions';

export function ContextScreen({ contextAnalytics, contextDaily }: { contextAnalytics: ContextAnalyticsSummary | null; contextDaily: ContextDailyAnalytics[] }) {
  const [openSection, setOpenSection] = useState<ContextAccordion>('trend');
  if (!contextAnalytics) return <div className="empty">Context analytics are not available.</div>;

  const sessions = contextAnalytics.sessions.slice(0, 30);
  const topReferences = sessions
    .flatMap((session) => session.memoryReferences.map((ref) => ({ ...ref, sessionTitle: session.title || session.sessionId })))
    .slice(0, 12);

  return (
    <>
      <section className="metrics">
        <Metric title="Context Tokens" value={compact(contextAnalytics.totalContextTokens)} />
        <Metric title="Repeated" value={percent(contextAnalytics.repeatedContextRatio)} />
        <Metric title="Cacheable" value={compact(contextAnalytics.cacheableContextTokens)} />
        <Metric title="Data" value={contextAnalytics.dataCompleteness.replace('_', ' ')} />
      </section>

      <section className="context-accordion">
        <div className="context-tabs" role="tablist" aria-label="Context analytics sections">
          <ContextTab label="Context Trend" meta={`${contextDaily.length} days`} active={openSection === 'trend'} onClick={() => setOpenSection('trend')} />
          <ContextTab label="Memory References" meta={`${topReferences.length} references`} active={openSection === 'references'} onClick={() => setOpenSection('references')} />
          <ContextTab label="Session Context" meta={`${sessions.length} sessions`} active={openSection === 'sessions'} onClick={() => setOpenSection('sessions')} />
        </div>
        <div className="context-accordion-body">
          {openSection === 'trend' && (
            <>
              <div className="panel-meta">
                <span>Generated {new Date(contextAnalytics.generatedAt).toLocaleString()}</span>
                <span>Metrics {metricCompleteness(contextAnalytics)}</span>
                {contextAnalytics.warnings.length > 0 && <span>{contextAnalytics.warnings.length} warning types</span>}
              </div>
              {contextDaily.length === 0 ? (
                <div className="empty">No imported context signals are available yet.</div>
              ) : (
                <ChartSurface empty={contextDaily.length === 0} emptyText="No imported context signals are available yet.">
                  <ResponsiveContainer width="100%" height={280}>
                    <AreaChart data={contextDaily}>
                      <defs>
                        <linearGradient id="contextFill" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="var(--chart-1)" stopOpacity={0.45} />
                          <stop offset="95%" stopColor="var(--chart-1)" stopOpacity={0.03} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid vertical={false} stroke="var(--border)" />
                      <XAxis dataKey="day" tickLine={false} axisLine={false} />
                      <YAxis tickLine={false} axisLine={false} />
                      <Tooltip formatter={(v) => compact(Number(v))} contentStyle={tooltipStyle} />
                      <Legend />
                      <Area dataKey="totalContextTokens" name="Context tokens" type="monotone" fill="url(#contextFill)" stroke="var(--chart-1)" strokeWidth={2} />
                      <Line dataKey="repeatedContextTokens" name="Repeated tokens" stroke="var(--chart-5)" strokeWidth={2} dot={false} />
                    </AreaChart>
                  </ResponsiveContainer>
                </ChartSurface>
              )}
            </>
          )}
          {openSection === 'references' && (
            topReferences.length === 0 ? (
              <div className="empty">No AGENTS.md, skill, docs, or spec references were detected.</div>
            ) : (
              <div className="reference-list">
                {topReferences.map((ref, index) => (
                  <div className="reference-item" key={`${ref.label}-${ref.lineNumber}-${index}`}>
                    <strong>{ref.label}</strong>
                    <span>{ref.type.replace('_', ' ')} · line {ref.lineNumber} · {ref.confidence}</span>
                  </div>
                ))}
              </div>
            )
          )}
          {openSection === 'sessions' && (
            sessions.length === 0 ? (
              <div className="empty">Context analytics will appear after sessions with readable rollouts are imported.</div>
            ) : (
              <div className="table context-table">
                <div className="row header"><span>Session</span><span>Repo</span><span>Model</span><span>Context</span><span>Repeated</span><span>Data</span></div>
                {sessions.map((session) => <ContextSessionRow session={session} key={session.sessionId} />)}
              </div>
            )
          )}
        </div>
      </section>
    </>
  );
}


function ContextSessionRow({ session }: { session: ContextSessionAnalytics }) {
  const repeatedWarning = session.warnings.find((warning) => warning.code === 'repeated_context');
  return (
    <div className="row">
      <span className="title" title={warningTitle(session)}>{session.title || session.sessionId}</span>
      <span>{session.repositoryId}</span>
      <span>{session.model}</span>
      <span>{compact(session.metrics.totalContextTokens)}</span>
      <span title={repeatedWarning ? evidenceSummary(repeatedWarning.evidence) : 'No repeated context warning'}>{percent(session.metrics.repeatedContextRatio)}</span>
      <span>{session.dataCompleteness.replace('_', ' ')} · {session.metrics.metricCompleteness}</span>
    </div>
  );
}


function warningTitle(session: ContextSessionAnalytics) {
  if (session.warnings.length === 0) return 'No warnings';
  return session.warnings.map((warning) => warning.code).join(', ');
}

function evidenceSummary(evidence: Record<string, unknown>) {
  const lines = evidence.lineNumbers;
  return Array.isArray(lines) ? `Evidence lines ${lines.join(', ')}` : 'Evidence available';
}


function metricCompleteness(summary: ContextAnalyticsSummary) {
  const first = summary.sessions.find((session) => session.metrics.metricCompleteness);
  return first?.metrics.metricCompleteness ?? 'not available';
}
