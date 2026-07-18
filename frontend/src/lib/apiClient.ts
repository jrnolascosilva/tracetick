import type { CreateUserRequest, LoginRequest, UpdateUserRequest, User } from '@/lib/types';

const API_BASE = '/api/v1';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
    ...init,
  });

  if (response.status === 401) {
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      const here = window.location.pathname + window.location.search;
      const next = encodeURIComponent(here);
      window.location.assign(`/login?next=${next}`);
    }
    throw new ApiError(401, 'Unauthenticated');
  }

  if (!response.ok) {
    const message = await response.text().catch(() => response.statusText);
    throw new ApiError(response.status, message || response.statusText);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  if (response.headers.get('content-length') === '0') {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const apiClient = {
  login(body: LoginRequest): Promise<User> {
    return request<User>('/auth/login', { method: 'POST', body: JSON.stringify(body) });
  },
  logout(): Promise<void> {
    return request<void>('/auth/logout', { method: 'POST' });
  },
  me(): Promise<User> {
    return request<User>('/me');
  },
  listUsers(): Promise<User[]> {
    return request<User[]>('/users');
  },
  createUser(body: CreateUserRequest): Promise<User> {
    return request<User>('/users', { method: 'POST', body: JSON.stringify(body) });
  },
  updateUser(id: number, body: UpdateUserRequest): Promise<User> {
    return request<User>(`/users/${id}`, { method: 'PATCH', body: JSON.stringify(body) });
  },
};
