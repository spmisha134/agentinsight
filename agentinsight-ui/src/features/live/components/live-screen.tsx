import { Metric, Panel } from '../../../components/ui';
import { freshnessLabel, liveRepoLabel } from '../../../utils/analytics-format';
import { LiveSessionState, LiveUsageSummary } from '../api';

export function LiveScreen({ liveUsage }: { liveUsage: LiveUsageSummary | null }) {
  if (!liveUsage) return <div className="empty">Live data is not available.</div>;

  return (
    <>
      <section className="metrics">
        <Metric title="Active" value={liveUsage.activeSessions.toString()} />
        <Metric title="Stale" value={liveUsage.staleSessions.toString()} />
        <Metric title="Inactive" value={liveUsage.inactiveSessions.toString()} />
        <Metric title="Unknown" value={liveUsage.unknownSessions.toString()} />
      </section>

      <Panel title="Live Session State">
        <div className="panel-meta">
          <span>Generated {new Date(liveUsage.generatedAt).toLocaleString()}</span>
          <span>Data {liveUsage.dataCompleteness.replace('_', ' ')}</span>
          {liveUsage.warnings.length > 0 && <span>{liveUsage.warnings.length} warning types</span>}
        </div>
        {liveUsage.sessions.length === 0 ? (
          <div className="empty">No local provider sessions are visible yet.</div>
        ) : (
          <div className="table live-table">
            <div className="row header"><span>Status</span><span>Title</span><span>Repo</span><span>Model</span><span>Freshness</span><span>Data</span></div>
            {liveUsage.sessions.slice(0, 40).map((s) => (
              <LiveSessionRow session={s} key={s.sessionId} />
            ))}
          </div>
        )}
      </Panel>
    </>
  );
}


function LiveSessionRow({ session }: { session: LiveSessionState }) {
  return (
    <div className="row">
      <span><span className={`status ${session.status.toLowerCase()}`}>{session.status.toLowerCase()}</span></span>
      <span className="title">{session.title || session.sessionId}</span>
      <span>{liveRepoLabel(session)}</span>
      <span>{session.model}</span>
      <span>{freshnessLabel(session.readFreshnessMs)}</span>
      <span title={session.warnings.join(', ') || session.statusReason}>{session.dataCompleteness.replace('_', ' ')}</span>
    </div>
  );
}
