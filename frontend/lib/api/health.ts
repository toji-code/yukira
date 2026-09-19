import { apiFetch } from './client';
import { BackendHealthResponse } from '@/types/api';

export async function fetchBackendHealth(): Promise<BackendHealthResponse> {
  return apiFetch<BackendHealthResponse>('/api/v1/health');
}
