import type {
  CreateUserRequest,
  ListTicketsParams,
  LoginRequest,
  Page,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  PasswordResetResponse,
  Ticket,
  TicketDetail,
  UpdateUserRequest,
  User,
} from '@/lib/types';

const API_BASE = '/api/v1';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export interface RequestOptions {
  redirectOnUnauthorized?: boolean;
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {},
): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
    ...init,
  });

  if (response.status === 401) {
    const allowRedirect = options.redirectOnUnauthorized !== false;
    const onResetPath = path.startsWith('/auth/password-reset');
    const onLoginPath =
      typeof window !== 'undefined' && window.location.pathname.startsWith('/login');
    if (
      typeof window !== 'undefined' &&
      allowRedirect &&
      !onResetPath &&
      !onLoginPath
    ) {
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
  requestPasswordReset(body: PasswordResetRequest): Promise<PasswordResetResponse> {
    return request<PasswordResetResponse>(
      '/auth/password-reset',
      { method: 'POST', body: JSON.stringify(body) },
      { redirectOnUnauthorized: false },
    );
  },
  confirmPasswordReset(body: PasswordResetConfirmRequest): Promise<void> {
    return request<void>(
      '/auth/password-reset/confirm',
      { method: 'POST', body: JSON.stringify(body) },
      { redirectOnUnauthorized: false },
    );
  },
  listTickets(params: ListTicketsParams = {}): Promise<Page<Ticket>> {
    const search = new URLSearchParams();
    if (params.state) search.set('state', params.state);
    if (params.severity) search.set('severity', params.severity);
    if (params.assignee !== undefined) search.set('assignee', String(params.assignee));
    if (params.tag && params.tag.trim()) search.set('tag', params.tag);
    if (params.search && params.search.trim()) search.set('search', params.search.trim());
    if (params.sort && params.sort.trim()) search.set('sort', params.sort.trim());
    if (params.page !== undefined) search.set('page', String(params.page));
    if (params.size !== undefined) search.set('size', String(params.size));
    const query = search.toString();
    const path = query.length > 0 ? `/tickets?${query}` : '/tickets';
    return request<Page<Ticket>>(path);
  },
  getTicket(id: number): Promise<TicketDetail> {
    return request<TicketDetail>(`/tickets/${id}`);
  },
};
