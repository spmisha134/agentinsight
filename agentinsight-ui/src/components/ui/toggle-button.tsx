import { Eye, EyeOff } from 'lucide-react';

type ToggleButtonProps = {
  active: boolean;
  label: string;
  onClick: () => void;
};

export function ToggleButton({ active, label, onClick }: ToggleButtonProps) {
  return (
    <button
      type="button"
      className={`toggle-button ${active ? 'active' : ''}`}
      onClick={onClick}
    >
      {active ? <Eye size={16} /> : <EyeOff size={16} />}
      <span>{label}</span>
    </button>
  );
}
