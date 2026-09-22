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

export async function fetchSchemeOptionsBySchemeId(schemeId: number): Promise<SchemeOption[]> {
  try {
    return await apiFetch<SchemeOption[]>(`/api/v1/schemes/${schemeId}/options`);
  } catch {
    // Fallback to filtering all options if specific endpoint is unreachable
    const allOptions = await fetchAllSchemeOptions();
    return allOptions.filter((opt) => opt.plan?.scheme?.id === schemeId);
  }
}
