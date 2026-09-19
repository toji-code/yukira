import { apiFetch } from './client';
import { Scheme, SchemeOption } from '@/types/domain';

export async function fetchAllSchemes(): Promise<Scheme[]> {
  return apiFetch<Scheme[]>('/api/v1/schemes');
}

export async function fetchSchemeById(id: number): Promise<Scheme> {
  return apiFetch<Scheme>(`/api/v1/schemes/${id}`);
}

export async function fetchAllSchemeOptions(): Promise<SchemeOption[]> {
  return apiFetch<SchemeOption[]>('/api/v1/schemes/options');
}
