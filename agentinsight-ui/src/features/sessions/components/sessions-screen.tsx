import { Panel } from '../../../components/ui';
import { SessionSummary } from '../api';
import { SessionTable } from './session-table';

export function SessionsScreen({ sessions }: { sessions: SessionSummary[] }) {
  return (
    <Panel title="Sessions" description="Filter and page through imported provider sessions.">
      <SessionTable sessions={sessions} />
    </Panel>
  );
}
