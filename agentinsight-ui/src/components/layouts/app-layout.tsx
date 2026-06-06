import React from 'react';
import {
  Menu,
  RefreshCw,
  X,
} from 'lucide-react';

import {
  activeNavigationGroup,
  Screen,
  screenTitle,
} from '../../config/navigation';
import { Sidebar } from './sidebar';

type AppLayoutProps = {
  children: React.ReactNode;
  error: string | null;
  loading: boolean;
  screen: Screen;
  mobileNavOpen: boolean;
  onRefresh: () => void;
  onSelectScreen: (screen: Screen) => void;
  onToggleMobileNav: () => void;
  onCloseMobileNav: () => void;
};

export function AppLayout({
  children,
  error,
  loading,
  screen,
  mobileNavOpen,
  onRefresh,
  onSelectScreen,
  onToggleMobileNav,
  onCloseMobileNav,
}: AppLayoutProps) {
  return (
    <div className={`app-shell ${mobileNavOpen ? 'nav-open' : ''}`}>
      <aside className="sidebar">
        <Sidebar
          screen={screen}
          onSelect={onSelectScreen}
        />
      </aside>
      <div
        className="mobile-backdrop"
        onClick={onCloseMobileNav}
      />

      <main className="content">
        <header className="topbar">
          <button
            className="icon-button mobile-menu-button"
            type="button"
            onClick={onToggleMobileNav}
            aria-label="Open navigation"
          >
            {mobileNavOpen ? <X size={18} /> : <Menu size={18} />}
          </button>
          <div>
            <p className="eyebrow">{activeNavigationGroup(screen)}</p>
            <h1>{screenTitle(screen)}</h1>
          </div>
          <div className="topbar-actions">
            {error && <span className="inline-alert">{error}</span>}
            <button
              className="refresh-button"
              onClick={onRefresh}
              aria-label="Refresh data"
              disabled={loading}
            >
              <RefreshCw
                size={18}
                className={loading ? 'spinning' : ''}
              />
              Refresh
            </button>
          </div>
        </header>

        {children}
      </main>
    </div>
  );
}
