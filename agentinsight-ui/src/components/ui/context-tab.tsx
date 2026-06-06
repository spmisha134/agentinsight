type ContextTabProps = {
  label: string;
  meta: string;
  active: boolean;
  onClick: () => void;
};

export function ContextTab({ label, meta, active, onClick }: ContextTabProps) {
  return (
    <button
      type="button"
      className={`context-tab ${active ? 'active' : ''}`}
      role="tab"
      aria-selected={active}
      onClick={onClick}
    >
      <span>{label}</span>
      <small>{meta}</small>
    </button>
  );
}
