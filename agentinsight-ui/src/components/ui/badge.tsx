import React from 'react';

type BadgeProps = {
  children: React.ReactNode;
  tone?: 'default' | 'success' | 'warning' | 'danger';
};

export function Badge({ children, tone = 'default' }: BadgeProps) {
  return (
    <span className={`badge ${tone}`}>
      {children}
    </span>
  );
}
