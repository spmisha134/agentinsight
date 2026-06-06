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
import { bytesLabel, latencyLabel, percent, tooltipStyle } from '../../../utils/analytics-format';
import { ToolAnalyticsSummary, ToolDailyAnalytics, ToolSessionAnalytics } from '../api';

type ToolPanel = 'trend' | 'tools' | 'sessions' | 'warnings';

export function ToolScreen({ toolAnalytics, toolDaily }: { toolAnalytics: ToolAnalyticsSummary | null; toolDaily: ToolDailyAnalytics[] }) {
  const [activePanel, setActivePanel] = useState<ToolPanel>('trend');
  if (!toolAnalytics) return <div className="empty">Tool analytics are not available.</div>;

  const sessions = toolAnalytics.sessions.slice(0, 30);
  const warnings = sessions.flatMap((session) => session.warnings.map((warning) => ({ ...warning, sessionId: session.sessionId }))).slice(0, 20);

  return (
    <>
      <section className="metrics">
        <Metric title="Tool Calls" value={compact(toolAnalytics.totalToolCalls)} />
        <Metric title="Failure Rate" value={percent(toolAnalytics.failureRate)} />
        <Metric title="Retries" value={compact(toolAnalytics.retryCount)} />
        <Metric title="Latency" value={latencyLabel(toolAnalytics.averageLatencyMs)} />
      </section>

      <section className="context-accordion">
        <div className="context-tabs" role="tablist" aria-label="Tool analytics sections">
          <ContextTab label="Tool Trend" meta={`${toolDaily.length} days`} active={activePanel === 'trend'} onClick={() => setActivePanel('trend')} />
          <ContextTab label="Tool Usage" meta={`${toolAnalytics.tools.length} tools`} active={activePanel === 'tools'} onClick={() => setActivePanel('tools')} />
          <ContextTab label="Sessions" meta={`${sessions.length} sessions`} active={activePanel === 'sessions'} onClick={() => setActivePanel('sessions')} />
          <ContextTab label="Warnings" meta={`${warnings.length} warnings`} active={activePanel === 'warnings'} onClick={() => setActivePanel('warnings')} />
        </div>
        <div className="context-accordion-body">
          {activePanel === 'trend' && (
            <>
              <div className="panel-meta">
                <span>Generated {new Date(toolAnalytics.generatedAt).toLocaleString()}</span>
                <span>Data {toolAnalytics.dataCompleteness.replace('_', ' ')}</span>
                {toolAnalytics.warnings.length > 0 && <span>{toolAnalytics.warnings.length} warning types</span>}
              </div>
              {toolDaily.length === 0 ? (
                <div className="empty">No tool call signals are available yet.</div>
              ) : (
                <ChartSurface empty={toolDaily.length === 0} emptyText="No tool call signals are available yet.">
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={toolDaily}>
                      <CartesianGrid vertical={false} stroke="var(--border)" />
                      <XAxis dataKey="day" tickLine={false} axisLine={false} />
                      <YAxis tickLine={false} axisLine={false} />
                      <Tooltip formatter={(v) => compact(Number(v))} contentStyle={tooltipStyle} />
                      <Legend />
                      <Bar dataKey="toolCalls" name="Calls" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="failedToolCalls" name="Failures" fill="var(--chart-5)" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </ChartSurface>
              )}
            </>
          )}
          {activePanel === 'tools' && (
            toolAnalytics.tools.length === 0 ? (
              <div className="empty">No normalized tool calls were detected.</div>
            ) : (
              <div className="table tool-table">
                <div className="row header"><span>Tool</span><span>Type</span><span>Calls</span><span>Failures</span><span>Output</span><span>Latency</span></div>
                {toolAnalytics.tools.map((tool) => (
                  <div className="row" key={`${tool.toolName}-${tool.toolType}`}>
                    <span className="title">{tool.toolName}</span>
                    <span>{tool.toolType}</span>
                    <span>{compact(tool.calls)}</span>
                    <span>{percent(tool.failureRate)}</span>
                    <span>{bytesLabel(tool.outputBytes)}</span>
                    <span>{latencyLabel(tool.averageLatencyMs)}</span>
                  </div>
                ))}
              </div>
            )
          )}
          {activePanel === 'sessions' && (
            sessions.length === 0 ? (
              <div className="empty">Tool analytics will appear after sessions with readable rollouts are imported.</div>
            ) : (
              <div className="table tool-session-table">
                <div className="row header"><span>Session</span><span>Repo</span><span>Calls</span><span>Failures</span><span>Retries</span><span>Data</span></div>
                {sessions.map((session) => <ToolSessionRow session={session} key={session.sessionId} />)}
              </div>
            )
          )}
          {activePanel === 'warnings' && (
            warnings.length === 0 ? (
              <div className="empty">No tool loops, high failure rates, or parser warnings detected.</div>
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


function ToolSessionRow({ session }: { session: ToolSessionAnalytics }) {
  return (
    <div className="row">
      <span className="title" title={session.warnings.map((warning) => warning.code).join(', ') || 'No warnings'}>{session.sessionId}</span>
      <span>{session.repositoryId}</span>
      <span>{compact(session.metrics.totalToolCalls)}</span>
      <span>{percent(session.metrics.failureRate)}</span>
      <span>{compact(session.metrics.retryCount)}</span>
      <span>{session.dataCompleteness.replace('_', ' ')} · {session.metrics.metricCompleteness}</span>
    </div>
  );
}
