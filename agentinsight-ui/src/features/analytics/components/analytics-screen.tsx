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
import { ChartSurface, Panel } from '../../../components/ui';
import { compact, money } from '../../../lib/format';
import { tooltipStyle } from '../../../utils/analytics-format';
import { Dashboard } from '../../dashboard/api';

export function AnalyticsScreen({ dashboard }: { dashboard: Dashboard }) {
  return (
    <section className="split">
      <Panel title="Repository Cost" description="Top repositories by estimated cost.">
        <ChartSurface empty={dashboard.topRepositories.length === 0} emptyText="No repository analytics are available yet.">
          <ResponsiveContainer width="100%" height={320}>
            <BarChart data={dashboard.topRepositories}>
              <CartesianGrid vertical={false} stroke="var(--border)" />
              <XAxis dataKey="repository" hide />
              <YAxis tickLine={false} axisLine={false} tickFormatter={(v) => money(Number(v))} />
              <Tooltip formatter={(v) => money(Number(v))} contentStyle={tooltipStyle} />
              <Legend />
              <Bar dataKey="cost" name="Cost" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartSurface>
      </Panel>
      <Panel title="Model Tokens" description="Token volume by model for drill-down comparison.">
        <ChartSurface empty={dashboard.models.length === 0} emptyText="No model analytics are available yet.">
          <ResponsiveContainer width="100%" height={320}>
            <BarChart data={dashboard.models}>
              <CartesianGrid vertical={false} stroke="var(--border)" />
              <XAxis dataKey="model" tickLine={false} axisLine={false} />
              <YAxis tickLine={false} axisLine={false} tickFormatter={(v) => compact(Number(v))} />
              <Tooltip formatter={(v, key) => key === 'cost' ? money(Number(v)) : compact(Number(v))} contentStyle={tooltipStyle} />
              <Legend />
              <Bar dataKey="tokens" name="Tokens" fill="var(--chart-2)" radius={[4, 4, 0, 0]} />
              <Bar dataKey="sessions" name="Sessions" fill="var(--chart-3)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartSurface>
      </Panel>
    </section>
  );
}
