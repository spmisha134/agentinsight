import React from 'react';

import { EmptyState } from './empty-state';

type ChartSurfaceProps = {
  children: React.ReactNode;
  empty: boolean;
  emptyText: string;
};

export function ChartSurface({ children, empty, emptyText }: ChartSurfaceProps) {
  if (empty) {
    return (
      <EmptyState
        title="No chart data"
        description={emptyText}
      />
    );
  }

  return (
    <div className="chart-surface">
      {children}
    </div>
  );
}
