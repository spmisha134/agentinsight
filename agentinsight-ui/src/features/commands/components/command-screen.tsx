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
import { latencyLabel, percent, tooltipStyle } from '../../../utils/analytics-format';
import { CommandAnalyticsSummary, CommandDailyAnalytics, CommandSessionAnalytics } from '../api';

type CommandPanel = 'trend' | 'categories' | 'sessions' | 'warnings';

export function CommandScreen({ commandAnalytics, commandDaily }: { commandAnalytics: CommandAnalyticsSummary | null; commandDaily: CommandDailyAnalytics[] }) {
  const [activePanel, setActivePanel] = useState<CommandPanel>('trend');
  if (!commandAnalytics) return <div className="empty">Command analytics are not available.</div>;

  const sessions = commandAnalytics.sessions.slice(0, 30);
  const warnings = sessions.flatMap((session) => session.warnings.map((warning) => ({ ...warning, sessionId: session.sessionId }))).slice(0, 20);

  return (
    <>
      <section className="metrics">
        <Metric title="Commands" value={compact(commandAnalytics.totalCommands)} />
        <Metric title="Failure Rate" value={percent(commandAnalytics.failureRate)} />
        <Metric title="Retries" value={compact(commandAnalytics.retryCount)} />
        <Metric title="Avg Duration" value={latencyLabel(commandAnalytics.averageDurationMs)} />
      </section>

      <section className="context-accordion">
        <div className="context-tabs command-tabs" role="tablist" aria-label="Command analytics sections">
          <ContextTab label="Command Trend" meta={`${commandDaily.length} days`} active={activePanel === 'trend'} onClick={() => setActivePanel('trend')} />
          <ContextTab
            label="Categories"
            meta={`${commandAnalytics.categories.length} groups`}
            active={activePanel === 'categories'}
            onClick={() => setActivePanel('categories')}
          />
          <ContextTab label="Sessions" meta={`${sessions.length} sessions`} active={activePanel === 'sessions'} onClick={() => setActivePanel('sessions')} />
          <ContextTab label="Warnings" meta={`${warnings.length} warnings`} active={activePanel === 'warnings'} onClick={() => setActivePanel('warnings')} />
        </div>
        <div className="context-accordion-body">
          {activePanel === 'trend' && (
            <>
              <div className="panel-meta">
                <span>Generated {new Date(commandAnalytics.generatedAt).toLocaleString()}</span>
                <span>Data {commandAnalytics.dataCompleteness.replace('_', ' ')}</span>
                <span>Duration {commandAnalytics.averageDurationMs === null ? 'not available' : 'exact when lifecycle timestamps exist'}</span>
                {commandAnalytics.warnings.length > 0 && <span>{commandAnalytics.warnings.length} warning types</span>}
              </div>
              {commandDaily.length === 0 ? (
                <div className="empty">No shell command signals are available yet.</div>
              ) : (
                <ChartSurface empty={commandDaily.length === 0} emptyText="No shell command signals are available yet.">
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={commandDaily}>
                      <CartesianGrid vertical={false} stroke="var(--border)" />
                      <XAxis dataKey="day" tickLine={false} axisLine={false} />
                      <YAxis tickLine={false} axisLine={false} />
                      <Tooltip formatter={(v) => compact(Number(v))} contentStyle={tooltipStyle} />
                      <Legend />
                      <Bar dataKey="commands" name="Commands" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="failedCommands" name="Failures" fill="var(--chart-5)" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </ChartSurface>
              )}
            </>
          )}
          {activePanel === 'categories' && (
            commandAnalytics.categories.length === 0 ? (
              <div className="empty">No command categories were detected.</div>
            ) : (
              <div className="table command-category-table">
                <div className="row header"><span>Category</span><span>Commands</span><span>Success</span><span>Failures</span><span>Retries</span><span>Duration</span></div>
                {commandAnalytics.categories.map((category) => (
                  <div className="row" key={category.category}>
                    <span className="title">{category.category}</span>
                    <span>{compact(category.commands)}</span>
                    <span>{compact(category.successes)}</span>
                    <span>{percent(category.failureRate)}</span>
                    <span>{compact(category.retries)}</span>
                    <span>{latencyLabel(category.averageDurationMs)}</span>
                  </div>
                ))}
              </div>
            )
          )}
          {activePanel === 'sessions' && (
            sessions.length === 0 ? (
              <div className="empty">Command analytics will appear after sessions with readable shell tool calls are imported.</div>
            ) : (
              <div className="table command-session-table">
                <div className="row header"><span>Session</span><span>Repo</span><span>Commands</span><span>Failures</span><span>Retries</span><span>Data</span></div>
                {sessions.map((session) => <CommandSessionRow session={session} key={session.sessionId} />)}
              </div>
            )
          )}
          {activePanel === 'warnings' && (
            warnings.length === 0 ? (
              <div className="empty">No risky commands, repeated commands, or parser warnings detected.</div>
            ) : (
              <div className="reference-list">
                {warnings.map((warning, index) => (
                  <div className="reference-item" key={`${warning.sessionId}-${warning.code}-${index}`}>
                    <strong>{warning.code.replace(/_/g, ' ')}</strong>
                    <span>{warning.severity} · session {warning.sessionId} · {commandWarningEvidence(warning.evidence)}</span>
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


function CommandSessionRow({ session }: { session: CommandSessionAnalytics }) {
  return (
    <div className="row">
      <span className="title" title={session.warnings.map((warning) => warning.code).join(', ') || 'No warnings'}>{session.title || session.sessionId}</span>
      <span>{session.repositoryId}</span>
      <span>{compact(session.metrics.totalCommands)}</span>
      <span>{percent(session.metrics.failureRate)}</span>
      <span>{compact(session.metrics.retryCount)}</span>
      <span>{session.dataCompleteness.replace('_', ' ')} · {session.metrics.metricCompleteness}</span>
    </div>
  );
}


function commandWarningEvidence(evidence: Record<string, unknown>) {
  const preview = evidence.commandPreview;
  if (typeof preview === 'string' && preview.length > 0) return preview;
  const count = evidence.repeatCount;
  if (typeof count === 'number') return `${count} repeats`;
  return 'evidence available';
}
