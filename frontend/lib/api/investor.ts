import { apiFetch } from './client';

export interface WatchlistResponse {
  id: number;
  schemeId: number;
  schemeCode: string;
  schemeName: string;
  amcName?: string | null;
  schemeOptionId?: number | null;
  optionType?: string | null;
  planType?: string | null;
  amfiCode?: string | null;
  isin?: string | null;
  latestNav?: number | null;
  latestNavDate?: string | null;
  yukiraScore?: number | null;
  yukiraScoreStatus?: string | null;
  reasonSaved?: string | null;
  investigationQuestion?: string | null;
  nextStep?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface WatchlistRequest {
  schemeId: number;
  schemeOptionId?: number;
  reasonSaved?: string;
  investigationQuestion?: string;
  nextStep?: string;
}

export interface WatchlistNoteRequest {
  reasonSaved?: string;
  investigationQuestion?: string;
  nextStep?: string;
}

export interface PortfolioResponse {
  schemeOptionId: number;
  units: number;
}

export async function fetchServerWatchlist(): Promise<WatchlistResponse[]> {
  return apiFetch<WatchlistResponse[]>('/api/investor/watchlist');
}

export async function addServerWatchlist(req: WatchlistRequest): Promise<WatchlistResponse> {
  return apiFetch<WatchlistResponse>('/api/investor/watchlist', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function updateServerWatchlistNote(
  schemeId: number,
  note: WatchlistNoteRequest
): Promise<WatchlistResponse> {
  return apiFetch<WatchlistResponse>(`/api/investor/watchlist/${schemeId}`, {
    method: 'PUT',
    body: JSON.stringify(note),
  });
}

export async function updateServerWatchlistNoteByOption(
  schemeOptionId: number,
  note: WatchlistNoteRequest
): Promise<WatchlistResponse> {
  return apiFetch<WatchlistResponse>(`/api/investor/watchlist/option/${schemeOptionId}`, {
    method: 'PUT',
    body: JSON.stringify(note),
  });
}

export async function removeServerWatchlist(schemeId: number): Promise<void> {
  return apiFetch<void>(`/api/investor/watchlist/${schemeId}`, {
    method: 'DELETE',
  });
}

export async function removeServerWatchlistByOption(schemeOptionId: number): Promise<void> {
  return apiFetch<void>(`/api/investor/watchlist/option/${schemeOptionId}`, {
    method: 'DELETE',
  });
}

export async function fetchServerPortfolio(): Promise<PortfolioResponse[]> {
  return apiFetch<PortfolioResponse[]>('/api/investor/portfolio');
}

export async function addServerPortfolioHolding(schemeOptionId: number, units: number): Promise<PortfolioResponse> {
  return apiFetch<PortfolioResponse>('/api/investor/portfolio', {
    method: 'POST',
    body: JSON.stringify({ schemeOptionId, units }),
  });
}

export async function removeServerPortfolioHolding(schemeOptionId: number): Promise<void> {
  return apiFetch<void>(`/api/investor/portfolio/${schemeOptionId}`, {
    method: 'DELETE',
  });
}
