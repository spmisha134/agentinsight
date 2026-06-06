import React from 'react';
import {
  Activity,
  BarChart3,
  BrainCircuit,
  Command,
  Database,
  Eye,
  Gauge,
  LayoutDashboard,
  Lightbulb,
  Settings,
  Terminal,
  Wrench,
  Zap,
} from 'lucide-react';

export type Screen =
  | 'overview'
  | 'live'
  | 'context'
  | 'cache'
  | 'tools'
  | 'commands'
  | 'replay'
  | 'optimization'
  | 'analytics'
  | 'sessions'
  | 'settings';

export type NavigationGroup = {
  label: string;
  items: Array<{
    screen: Screen;
    label: string;
    icon: React.ReactNode;
  }>;
};

export const NAV_GROUPS: NavigationGroup[] = [
  {
    label: 'Dashboard',
    items: [
      {
        screen: 'overview',
        label: 'Dashboard',
        icon: <LayoutDashboard size={18} />,
      },
    ],
  },
  {
    label: 'Observability',
    items: [
      {
        screen: 'live',
        label: 'Live Usage',
        icon: <Activity size={18} />,
      },
      {
        screen: 'context',
        label: 'Context Memory',
        icon: <BrainCircuit size={18} />,
      },
      {
        screen: 'cache',
        label: 'Cache Performance',
        icon: <Zap size={18} />,
      },
      {
        screen: 'tools',
        label: 'Tool Analytics',
        icon: <Wrench size={18} />,
      },
      {
        screen: 'commands',
        label: 'Command Analytics',
        icon: <Terminal size={18} />,
      },
    ],
  },
  {
    label: 'Insights',
    items: [
      {
        screen: 'replay',
        label: 'Agent Replay',
        icon: <Eye size={18} />,
      },
      {
        screen: 'optimization',
        label: 'Usage Optimization',
        icon: <Lightbulb size={18} />,
      },
      {
        screen: 'analytics',
        label: 'Repository Analytics',
        icon: <BarChart3 size={18} />,
      },
    ],
  },
  {
    label: 'Data',
    items: [
      {
        screen: 'sessions',
        label: 'Sessions',
        icon: <Database size={18} />,
      },
    ],
  },
  {
    label: 'Settings',
    items: [
      {
        screen: 'settings',
        label: 'Settings',
        icon: <Settings size={18} />,
      },
    ],
  },
];

export function screenTitle(screen: Screen) {
  return {
    overview: 'Overview',
    live: 'Live Usage',
    context: 'Context Memory',
    cache: 'Cache Performance',
    tools: 'Tool Calls',
    commands: 'Command Analytics',
    replay: 'Agent Replay',
    optimization: 'Usage Optimization',
    analytics: 'Analytics',
    sessions: 'Sessions',
    settings: 'Settings',
  }[screen];
}

export function activeNavigationGroup(screen: Screen) {
  return NAV_GROUPS.find((group) =>
    group.items.some((item) => item.screen === screen),
  )?.label ?? 'Dashboard';
}
