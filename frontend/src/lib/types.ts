export type Role = 'CUSTOMER' | 'TECHNICIAN';

export interface User {
  id: number;
  email: string;
  role: Role;
  active: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  role: Role;
}

export interface UpdateUserRequest {
  role?: Role;
  active?: boolean;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetResponse {
  token: string;
}

export interface PasswordResetConfirmRequest {
  token: string;
  new_password: string;
}

export type TicketOrigin = 'HUMAN' | 'WEBHOOK';

export type TicketState = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type EventType =
  | 'COMMENT'
  | 'STATE_CHANGE'
  | 'ASSIGNMENT_CHANGE'
  | 'SEVERITY_CHANGE'
  | 'TAG_CHANGE'
  | 'WATCHER_CHANGE'
  | 'REFIRE'
  | 'ATTACHMENT_ADDED';

export interface Tag {
  key: string;
  value: string;
}

export interface Ticket {
  id: number;
  origin: TicketOrigin;
  reporterUserId: number | null;
  assigneeUserId: number | null;
  title: string;
  description: string;
  severity: Severity;
  state: TicketState;
  refireCount: number;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
  tags: Tag[];
}

export interface TicketEvent {
  id: number;
  type: EventType;
  actorUserId: number | null;
  payload: Record<string, unknown>;
  createdAt: string;
}

export interface TicketDetail {
  ticket: Ticket;
  events: TicketEvent[];
  watcherIds: number[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface ListTicketsParams {
  state?: TicketState;
  severity?: Severity;
  assignee?: number;
  tag?: string;
  search?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export interface CreateTicketRequest {
  title: string;
  description: string;
  severity: Severity;
  tags: Tag[];
}
