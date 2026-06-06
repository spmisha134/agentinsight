import React from 'react';

type PanelProps = {
  title: string;
  description?: string;
  children: React.ReactNode;
};

export function Panel({ title, description, children }: PanelProps) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
      </div>
      {children}
    </section>
  );
}
