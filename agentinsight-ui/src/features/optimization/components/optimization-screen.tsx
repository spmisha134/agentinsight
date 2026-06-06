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
import { ChartSurface, Metric, Panel } from '../../../components/ui';
import { compact, money } from '../../../lib/format';
import { tooltipStyle } from '../../../utils/analytics-format';
import { OptimizationSignal, UsageOptimizationDaily, UsageOptimizationSummary } from '../api';

export function OptimizationScreen({ optimization, optimizationDaily }: { optimization: UsageOptimizationSummary | null; optimizationDaily: UsageOptimizationDaily[] }) {
  const [severity, setSeverity] = useState('all');
  const [category, setCategory] = useState('all');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  if (!optimization) return <div className="empty">Usage optimization is not available.</div>;

  const categories = Array.from(new Set(optimization.signals.map((signal) => signal.category))).sort();
  const filtered = optimization.signals.filter((signal) => (severity === 'all' || signal.severity === severity) && (category === 'all' || signal.category === category));
  const selected = filtered.find((signal) => signal.id === selectedId) ?? filtered[0] ?? null;

  return (
    <>
      <section className="metrics">
        <Metric title="Signals" value={compact(optimization.totalSignals)} />
        <Metric title="High Severity" value={compact(optimization.highSeveritySignals)} />
        <Metric title="Impact Tokens" value={compact(optimization.totalImpactTokens)} />
        <Metric title="Impact Cost" value={money(optimization.totalImpactCost)} />
      </section>

      <section className="split optimization-layout">
        <Panel title="Optimization Inbox">
          <div className="panel-meta">
            <span>Generated {new Date(optimization.generatedAt).toLocaleString()}</span>
            <span>Data {optimization.dataCompleteness.replace('_', ' ')}</span>
            {optimization.warnings.length > 0 && <span>{optimization.warnings.length} warning types</span>}
          </div>
          <div className="optimization-filters">
            <select value={severity} onChange={(event) => setSeverity(event.target.value)} aria-label="Severity filter">
              <option value="all">All severities</option>
              <option value="high">High</option>
              <option value="medium">Medium</option>
              <option value="low">Low</option>
            </select>
            <select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Category filter">
              <option value="all">All categories</option>
              {categories.map((item) => <option value={item} key={item}>{item.replace(/_/g, ' ')}</option>)}
            </select>
          </div>
          {optimizationDaily.length > 0 && (
            <ResponsiveContainer width="100%" height={170}>
              <LineChart data={optimizationDaily}>
                <CartesianGrid vertical={false} stroke="var(--border)" />
                <XAxis dataKey="day" hide />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip formatter={(v) => compact(Number(v))} contentStyle={tooltipStyle} />
                <Legend />
                <Line dataKey="signals" name="Signals" stroke="var(--chart-1)" strokeWidth={2} dot={false} />
                <Line dataKey="highSeveritySignals" name="High severity" stroke="var(--chart-5)" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
          {filtered.length === 0 ? (
            <div className="empty">No recommendations match the selected filters.</div>
          ) : (
            <div className="optimization-list">
              {filtered.map((signal) => (
                <button className={`optimization-item ${selected?.id === signal.id ? 'active' : ''}`} key={signal.id} onClick={() => setSelectedId(signal.id)}>
                  <span className={`severity ${signal.severity}`}>{signal.severity}</span>
                  <strong>{signal.title}</strong>
                  <small>{signal.category.replace(/_/g, ' ')} · {compact(signal.impactTokens)} tokens · {money(signal.impactCost)}</small>
                </button>
              ))}
            </div>
          )}
        </Panel>

        <Panel title="Recommendation Detail">
          {!selected ? (
            <div className="empty">Select a recommendation.</div>
          ) : (
            <div className="optimization-detail">
              <div className="panel-meta">
                <span>{selected.severity}</span>
                <span>{selected.category.replace(/_/g, ' ')}</span>
                <span>{selected.status}</span>
              </div>
              <h3>{selected.title}</h3>
              <p>{selected.recommendation}</p>
              <div className="detail-grid">
                <Metric title="Affected Sessions" value={compact(selected.affectedSessions.length)} />
                <Metric title="Impact Tokens" value={compact(selected.impactTokens)} />
                <Metric title="Impact Cost" value={money(selected.impactCost)} />
              </div>
              <div className="evidence-box">
                <strong>Evidence</strong>
                <span>{optimizationEvidence(selected)}</span>
              </div>
            </div>
          )}
        </Panel>
      </section>
    </>
  );
}


function optimizationEvidence(signal: OptimizationSignal) {
  const source = signal.evidence.metricSource;
  const completeness = signal.evidence.dataCompleteness;
  const parts = [
    typeof source === 'string' ? `source ${source.replace(/_/g, ' ')}` : null,
    typeof completeness === 'string' ? `data ${completeness.replace(/_/g, ' ')}` : null,
    signal.affectedSessions.length > 0 ? `sessions ${signal.affectedSessions.slice(0, 3).join(', ')}` : null,
  ].filter(Boolean);
  return parts.join(' · ') || 'Evidence references are available without raw prompts or outputs.';
}
