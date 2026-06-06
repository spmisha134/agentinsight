import { LiveSessionState } from '../features/live/api';
import { SessionSummary } from '../features/sessions/api';

export const tooltipStyle = {
  background: 'var(--popover)',
  border: '1px solid var(--border)',
  borderRadius: 8,
  color: 'var(--popover-foreground)',
  boxShadow: 'var(--shadow-lg)',
};

export function percent(value: number) {
  return `${Math.round(value * 100)}%`;
}

export function percentPoint(value: number) {
  return `${Number(value ?? 0).toFixed(1)}%`;
}

export function latencyLabel(value: number | null) {
  if (value === null) {
    return 'n/a';
  }

  if (value < 1000) {
    return `${value}ms`;
  }

  return `${(value / 1000).toFixed(1)}s`;
}

export function bytesLabel(value: number) {
  if (value < 1024) {
    return `${value} B`;
  }

  if (value < 1024 * 1024) {
    return `${Math.round(value / 1024)} KB`;
  }

  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

export function repoLabel(session: SessionSummary) {
  if (session.repositoryUrl) {
    return session.repositoryUrl
      .replace('git@github.com:', 'github.com/')
      .replace('.git', '');
  }

  return session.cwd?.split('/').pop() ?? 'unknown';
}

export function liveRepoLabel(session: LiveSessionState) {
  if (session.repositoryUrl) {
    return session.repositoryUrl
      .replace('git@github.com:', 'github.com/')
      .replace('.git', '');
  }

  return session.cwd?.split('/').pop() ?? 'unknown';
}

export function freshnessLabel(readFreshnessMs: number | null) {
  if (readFreshnessMs === null) {
    return 'unknown';
  }

  const minutes = Math.round(readFreshnessMs / 60000);

  if (minutes < 1) {
    return 'now';
  }

  if (minutes < 60) {
    return `${minutes}m ago`;
  }

  return `${Math.round(minutes / 60)}h ago`;
}
