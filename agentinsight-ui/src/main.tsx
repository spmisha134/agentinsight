import React from 'react';
import { createRoot } from 'react-dom/client';

import { AppRouter } from './app/router';
import { AppProvider } from './app/provider';
import './styles.css';

const root = document.getElementById('root');
if (!root) {
  throw new Error('No root element found');
}

createRoot(root).render(
  <React.StrictMode>
    <AppProvider>
      <AppRouter />
    </AppProvider>
  </React.StrictMode>,
);
