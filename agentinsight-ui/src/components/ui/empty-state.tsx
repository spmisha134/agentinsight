import { Search } from 'lucide-react';

type EmptyStateProps = {
  title: string;
  description: string;
};

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <div className="empty-icon">
        <Search size={18} />
      </div>
      <strong>{title}</strong>
      <span>{description}</span>
    </div>
  );
}
