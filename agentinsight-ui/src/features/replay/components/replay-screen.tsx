import { useEffect, useState } from 'react';
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
import { ChartSurface, ContextTab, Metric, Panel, ToggleButton } from '../../../components/ui';
import { compact, money } from '../../../lib/format';
import { tooltipStyle } from '../../../utils/analytics-format';
import { getReplaySession, ReplayDailyAnalytics, ReplaySession, ReplaySummary, ReplayTimelineEvent } from '../api';

export function ReplayScreen({ replaySummary, replayDaily }: { replaySummary: ReplaySummary | null; replayDaily: ReplayDailyAnalytics[] }) {
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [selectedReplay, setSelectedReplay] = useState<ReplaySession | null>(null);
  const [showPrompts, setShowPrompts] = useState(false);
  const [showOutputs, setShowOutputs] = useState(false);
  const [showFilePaths, setShowFilePaths] = useState(false);

  useEffect(() => {
    const sessionId = selectedSessionId ?? replaySummary?.sessions.find((session) => session.totalEvents > 0)?.sessionId ?? null;
    if (!sessionId) {
      setSelectedReplay(null);
      return;
    }
    setSelectedSessionId(sessionId);
    getReplaySession(sessionId, { showPrompts, showOutputs, showFilePaths })
      .then(setSelectedReplay)
      .catch(() => setSelectedReplay(null));
  }, [selectedSessionId, replaySummary, showPrompts, showOutputs, showFilePaths]);

  if (!replaySummary) return <div className="empty">Session replay is not available.</div>;
  const sessions = replaySummary.sessions.slice(0, 20);

  return (
    <>
      <section className="metrics">
        <Metric title="Replay Events" value={compact(replaySummary.totalEvents)} />
        <Metric title="Sessions" value={compact(replaySummary.sessionsWithReplay)} />
        <Metric title="Tokens" value={compact(replaySummary.totalTokens)} />
        <Metric title="Estimated Cost" value={money(replaySummary.estimatedCost)} />
      </section>

      <section className="split replay-layout">
        <Panel title="Replay Index">
          <div className="panel-meta">
            <span>Generated {new Date(replaySummary.generatedAt).toLocaleString()}</span>
            <span>Data {replaySummary.dataCompleteness.replace('_', ' ')}</span>
            <span>{replaySummary.malformedLines} malformed lines</span>
          </div>
          {replayDaily.length > 0 && (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={replayDaily}>
                <CartesianGrid vertical={false} stroke="var(--border)" />
                <XAxis dataKey="day" hide />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip formatter={(v) => compact(Number(v))} contentStyle={tooltipStyle} />
                <Legend />
                <Bar dataKey="events" name="Events" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
                <Bar dataKey="commandEvents" name="Commands" fill="var(--chart-3)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
          {sessions.length === 0 ? (
            <div className="empty">Replay timelines will appear after readable rollout files are imported.</div>
          ) : (
            <div className="replay-session-list">
              {sessions.map((session) => (
                <button
                  className={`replay-session-button ${selectedSessionId === session.sessionId ? 'active' : ''}`}
                  key={session.sessionId}
                  onClick={() => setSelectedSessionId(session.sessionId)}
                >
                  <strong>{session.title || session.sessionId}</strong>
                  <span>{session.repositoryId} · {compact(session.metrics.totalEvents)} events · {session.dataCompleteness.replace('_', ' ')}</span>
                </button>
              ))}
            </div>
          )}
        </Panel>

        <Panel title="Session Timeline">
          <div className="replay-controls">
            <ToggleButton active={showPrompts} label="Prompts" onClick={() => setShowPrompts((value) => !value)} />
            <ToggleButton active={showOutputs} label="Outputs" onClick={() => setShowOutputs((value) => !value)} />
            <ToggleButton active={showFilePaths} label="Paths" onClick={() => setShowFilePaths((value) => !value)} />
          </div>
          {!selectedReplay ? (
            <div className="empty">Select a session with replay events.</div>
          ) : (
            <>
              <div className="panel-meta">
                <span>{compact(selectedReplay.totalEvents)} events</span>
                <span>{compact(selectedReplay.metrics.totalTokens)} tokens</span>
                <span>{money(selectedReplay.metrics.estimatedCost)} estimated</span>
                <span>{selectedReplay.dataCompleteness.replace('_', ' ')} · {selectedReplay.metrics.metricCompleteness}</span>
              </div>
              <div className="timeline">
                {selectedReplay.events.length === 0 ? (
                  <div className="empty">No replay events match the current filters.</div>
                ) : (
                  selectedReplay.events.map((event) => <ReplayEventItem event={event} key={event.id} />)
                )}
              </div>
            </>
          )}
        </Panel>
      </section>
    </>
  );
}


function ReplayEventItem({ event }: { event: ReplayTimelineEvent }) {
  return (
    <article className={`timeline-item ${event.eventType}`}>
      <div className="timeline-marker">{event.sequence}</div>
      <div className="timeline-body">
        <header>
          <strong>{event.title}</strong>
          <span>{event.eventType.replace(/_/g, ' ')} · {event.status}</span>
        </header>
        <p>{event.contentPreview || event.commandPreview || event.filePath || 'No preview available'}</p>
        <div className="timeline-meta">
          <span>line {event.lineNumber}</span>
          <span>{event.actor}</span>
          {event.totalTokens !== null && <span>{compact(event.totalTokens)} tokens</span>}
          {event.estimatedCost !== null && <span>{money(event.estimatedCost)}</span>}
          {event.filePath && <span>{event.filePath}</span>}
        </div>
      </div>
    </article>
  );
}
