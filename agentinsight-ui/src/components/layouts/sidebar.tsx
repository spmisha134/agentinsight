import agentInsightMark from '../../assets/agentinsight-mark.svg';
import { NAV_GROUPS, Screen } from '../../config/navigation';
import { NavButton } from '../ui';

type SidebarProps = {
  screen: Screen;
  onSelect: (screen: Screen) => void;
};

export function Sidebar({ screen, onSelect }: SidebarProps) {
  return (
    <>
      <div className="brand">
        <div className="brand-mark">
          <img src={agentInsightMark} alt="" />
        </div>
        <div>
          <strong>AgentInsight</strong>
          <span>Local analytics</span>
        </div>
      </div>

      <nav className="nav-sections">
        {NAV_GROUPS.map((group) => (
          <div className="nav-section" key={group.label}>
            <p>{group.label}</p>
            {group.items.map((item) => (
              <NavButton
                key={item.screen}
                icon={item.icon}
                label={item.label}
                active={screen === item.screen}
                onClick={() => onSelect(item.screen)}
              />
            ))}
          </div>
        ))}
      </nav>
    </>
  );
}
