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
