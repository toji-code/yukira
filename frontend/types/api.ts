export interface ApiError {
  code: string;
  message: string;
  status?: number;
  details?: unknown;
}

export type ApiState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'empty'; message?: string }
  | { status: 'error'; error: ApiError }
  | { status: 'unavailable'; message: string };

export interface BackendHealthResponse {
  status: string;
  service: string;
  version: string;
  methodology_status: string;
  empirical_findings: string;
}
