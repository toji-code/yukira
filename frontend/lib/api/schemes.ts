import { apiFetch } from './client';
import { Scheme, SchemeOption, Holding } from '@/types/domain';

export async function fetchAllSchemes(): Promise<Scheme[]> {
  return apiFetch<Scheme[]>('/api/v1/schemes');
}

export async function fetchSchemeById(id: number): Promise<Scheme> {
  return apiFetch<Scheme>(`/api/v1/schemes/${id}`);
}

export async function fetchAllSchemeOptions(): Promise<SchemeOption[]> {
  // The full expanded-universe share-class payload is ~9.5 MB across 14k+ options, which
  // legitimately exceeds the shared 8s client default. Give this call an explicit budget
  // rather than raising the global default for every other endpoint.
  return apiFetch<SchemeOption[]>('/api/v1/schemes/options', undefined, 45000);
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

export async function fetchSchemeHoldings(schemeOptionId: number): Promise<Holding[]> {
  return apiFetch<Holding[]>(`/api/v1/schemes/options/${schemeOptionId}/holdings`);
}

export async function searchSchemeOptions(query: string): Promise<SchemeOption[]> {
  if (!query || query.trim().length === 0) return [];
  return apiFetch<SchemeOption[]>(`/api/v1/schemes/options?search=${encodeURIComponent(query.trim())}`);
}
