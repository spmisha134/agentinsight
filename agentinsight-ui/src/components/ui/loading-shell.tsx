export function LoadingShell() {
  return (
    <div className="loading-shell">
      <div className="skeleton skeleton-title" />
      <div className="metrics">
        <SkeletonCard />
        <SkeletonCard />
        <SkeletonCard />
        <SkeletonCard />
      </div>
      <div className="split">
        <div className="panel"><div className="skeleton skeleton-panel" /></div>
        <div className="panel"><div className="skeleton skeleton-panel" /></div>
      </div>
    </div>
  );
}

function SkeletonCard() {
  return (
    <div className="metric skeleton-card">
      <div className="skeleton skeleton-line short" />
      <div className="skeleton skeleton-value" />
      <div className="skeleton skeleton-line" />
    </div>
  );
}
