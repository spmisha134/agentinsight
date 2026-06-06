import { useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';
import { Badge, EmptyState } from '../../../components/ui';
import { compact, dateTime, money } from '../../../lib/format';
import { repoLabel } from '../../../utils/analytics-format';
import { SessionSummary } from '../api';

export function SessionTable({ sessions, compactRows = false }: { sessions: SessionSummary[]; compactRows?: boolean }) {
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<'updated' | 'tokens' | 'cost'>('updated');
  const [page, setPage] = useState(0);
  const pageSize = compactRows ? 6 : 12;
  const filteredSessions = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return sessions
      .filter((session) => {
        if (!normalizedQuery) return true;
        return [session.title, session.id, session.model, repoLabel(session), session.branch ?? '']
          .some((value) => value.toLowerCase().includes(normalizedQuery));
      })
      .sort((a, b) => {
        if (sort === 'tokens') return b.tokensUsed - a.tokensUsed;
        if (sort === 'cost') return b.cost.totalCost - a.cost.totalCost;
        return b.updatedAtMs - a.updatedAtMs;
      });
  }, [sessions, query, sort]);
  const pageCount = Math.max(1, Math.ceil(filteredSessions.length / pageSize));
  const safePage = Math.min(page, pageCount - 1);
  const visibleSessions = filteredSessions.slice(safePage * pageSize, safePage * pageSize + pageSize);

  useEffect(() => {
    setPage(0);
  }, [query, sort, sessions.length]);

  if (sessions.length === 0) {
    return (
      <EmptyState
        title="No sessions"
        description="Imported sessions will appear here after discovery completes."
      />
    );
  }

  return (
    <>
      {!compactRows && (
        <div className="table-toolbar">
          <label className="search-field">
            <Search size={16} />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Filter sessions, repos, models"
            />
          </label>
          <select
            className="select-control"
            value={sort}
            onChange={(event) => setSort(event.target.value as 'updated' | 'tokens' | 'cost')}
            aria-label="Sort sessions"
          >
            <option value="updated">Updated</option>
            <option value="tokens">Tokens</option>
            <option value="cost">Cost</option>
          </select>
        </div>
      )}
      {visibleSessions.length === 0 ? (
        <EmptyState
          title="No matching sessions"
          description="Adjust the filter to broaden the session list."
        />
      ) : (
        <div className={`table ${compactRows ? 'compact-table' : ''}`}>
          <div className="row header">
            <span>Title</span>
            <span>Repo</span>
            <span>Model</span>
            <span>Tokens</span>
            <span>Cost</span>
            <span>Updated</span>
          </div>
          {visibleSessions.map((s) => (
            <div className="row" key={s.id}>
              <span className="title">{s.title || s.id}</span>
              <span>{repoLabel(s)}</span>
              <span><Badge>{s.model}</Badge></span>
              <span>{compact(s.tokensUsed)}</span>
              <span>{money(s.cost.totalCost)}</span>
              <span>{dateTime(s.updatedAtMs)}</span>
            </div>
          ))}
        </div>
      )}
      {!compactRows && (
        <div className="pagination">
          <span>{filteredSessions.length} sessions</span>
          <div>
            <button
              className="icon-button"
              type="button"
              disabled={safePage === 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
              aria-label="Previous page"
            >
              <ChevronLeft size={16} />
            </button>
            <span>Page {safePage + 1} of {pageCount}</span>
            <button
              className="icon-button"
              type="button"
              disabled={safePage >= pageCount - 1}
              onClick={() => setPage((value) => Math.min(pageCount - 1, value + 1))}
              aria-label="Next page"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}
    </>
  );
}
