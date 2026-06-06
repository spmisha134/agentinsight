export const API_BASE = import.meta.env.VITE_API_BASE ?? '/api';

export async function getJson<T>(path: string, errorMessage: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`);

  if (!response.ok) {
    throw new Error(errorMessage);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : null) as T;
}

export async function postJson<T>(path: string, body: unknown, errorMessage: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(errorMessage);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : null) as T;
}
