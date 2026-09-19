import { ApiError } from '@/types/api';

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export async function apiFetch<T>(
  path: string,
  options?: RequestInit,
  timeoutMs = 8000
): Promise<T> {
  const url = `${API_BASE_URL.replace(/\/$/, '')}${path}`;
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(options?.headers || {}),
      },
      signal: controller.signal,
    });

    clearTimeout(id);

    if (!response.ok) {
      const text = await response.text();
      let errorBody: unknown;
      try {
        errorBody = JSON.parse(text);
      } catch {
        errorBody = text;
      }

      const error: ApiError = {
        code: `HTTP_${response.status}`,
        message: `Backend request failed: HTTP ${response.status} ${response.statusText}`,
        status: response.status,
        details: errorBody,
      };
      throw error;
    }

    const data = await response.json();
    return data as T;
  } catch (err: unknown) {
    clearTimeout(id);

    if ((err as { name?: string })?.name === 'AbortError') {
      const timeoutError: ApiError = {
        code: 'TIMEOUT',
        message: `Request to ${path} timed out after ${timeoutMs}ms. Analysis service is currently unresponsive.`,
      };
      throw timeoutError;
    }

    if ((err as ApiError)?.code) {
      throw err;
    }

    const networkError: ApiError = {
      code: 'NETWORK_ERROR',
      message: `Failed to connect to backend at ${API_BASE_URL}. Ensure the Spring Boot service is running.`,
      details: err,
    };
    throw networkError;
  }
}
