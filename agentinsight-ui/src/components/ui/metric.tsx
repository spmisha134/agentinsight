import React from 'react';

type MetricProps = {
  title: string;
  value: string;
  trend?: string;
  icon?: React.ReactNode;
};

export function Metric({ title, value, trend, icon }: MetricProps) {
  return (
    <div className="metric">
      <div className="metric-header">
        <p>{title}</p>
        {icon && (
          <span className="metric-icon">
            {icon}
          </span>
        )}
      </div>
      <strong>{value}</strong>
      {trend && (
        <span className="metric-trend">
          {trend}
        </span>
      )}
    </div>
  );
}
