import { Activity, BarChart3, Database, Gauge } from 'lucide-react';
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
import { ChartSurface, Metric, Panel } from '../../../components/ui';
import { compact, money } from '../../../lib/format';
import { tooltipStyle } from '../../../utils/analytics-format';
import { LiveUsageSummary } from '../../live/api';
import { SessionSummary } from '../../sessions/api';
import { SessionTable } from '../../sessions/components/session-table';
import { Dashboard } from '../api';

export function OverviewScreen({ dashboard, liveUsage, sessions }: { dashboard: Dashboard; liveUsage: LiveUsageSummary | null; sessions: SessionSummary[] }) {
  const trendData = dashboard.models.map((model) => ({
    name: model.model,
    cost: model.cost,
    tokens: model.tokens,
  }));

  return (
    <>
      <section className="metrics">
        <Metric title="Sessions" value={dashboard.totalSessions.toString()} trend="Imported sessions" icon={<Database size={16} />} />
        <Metric title="Tokens" value={compact(dashboard.totalTokens)} trend="Rollout token events" icon={<Gauge size={16} />} />
        <Metric title="Estimated Cost" value={money(dashboard.estimatedCost)} trend="Configurable pricing" icon={<BarChart3 size={16} />} />
        <Metric title="Active Now" value={(liveUsage?.activeSessions ?? 0).toString()} trend={`${liveUsage?.staleSessions ?? 0} stale`} icon={<Activity size={16} />} />
      </section>
      <section className="split hero-grid">
        <Panel title="Model Cost" description="Estimated spend by model with token volume context.">
          <ChartSurface empty={trendData.length === 0} emptyText="No model costs are available yet.">
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={trendData}>
                <defs>
                  <linearGradient id="modelCostFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--chart-1)" stopOpacity={0.45} />
                    <stop offset="95%" stopColor="var(--chart-1)" stopOpacity={0.03} />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} stroke="var(--border)" />
                <XAxis dataKey="name" tickLine={false} axisLine={false} tickMargin={8} />
                <YAxis tickLine={false} axisLine={false} tickFormatter={(v) => money(Number(v))} />
                <Tooltip formatter={(v, key) => key === 'cost' ? money(Number(v)) : compact(Number(v))} contentStyle={tooltipStyle} />
                <Legend />
                <Area dataKey="cost" name="Cost" type="monotone" fill="url(#modelCostFill)" stroke="var(--chart-1)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </ChartSurface>
        </Panel>
        <Panel title="Recent Sessions" description="Latest imported sessions and their cost footprint.">
          <SessionTable sessions={sessions.slice(0, 6)} compactRows />
        </Panel>
      </section>
    </>
  );
}
