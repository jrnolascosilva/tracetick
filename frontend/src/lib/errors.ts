import { ApiError } from '@/lib/apiClient';

export function describeApiError(error: unknown, fallback = 'Something went wrong.'): string {
  if (error instanceof ApiError) {
    return error.message || `HTTP ${error.status}`;
  }
  return fallback;
}