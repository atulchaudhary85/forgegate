const API = import.meta.env.VITE_API_URL || '/api';
let token = sessionStorage.getItem('forgegate_token') || '';
let onUnauthorized = () => {};
export function setToken(value) { token = value || ''; if (token) sessionStorage.setItem('forgegate_token', token); else sessionStorage.removeItem('forgegate_token'); }
export function getToken() { return token; }
export function setUnauthorizedHandler(fn) { onUnauthorized = fn; }
export async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(options.headers || {}) };
  const response = await fetch(`${API}${path}`, { ...options, headers });
  if (response.status === 401) { setToken(''); onUnauthorized(); }
  const contentType = response.headers.get('content-type') || '';
  const body = response.status === 204 ? null : contentType.includes('application/json') ? await response.json() : await response.text();
  if (!response.ok) throw new Error(body?.message || body?.error || `Request failed (${response.status})`);
  return body;
}
export async function login(email, password) { const result = await api('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }); setToken(result.token); return result.user; }
