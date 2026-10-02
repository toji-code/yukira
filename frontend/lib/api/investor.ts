export interface WatchlistResponse {
  schemeId: number;
  code: string;
}

export interface PortfolioResponse {
  schemeOptionId: number;
  units: number;
}

async function nextFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...(options?.headers || {}),
    },
  });

  if (!response.ok) {
    throw new Error(`Failed request to ${path}: ${response.status} ${response.statusText}`);
  }

  // DELETE might not return JSON
  if (options?.method === 'DELETE') {
    return {} as T;
  }
  
  return response.json();
}

export async function fetchServerWatchlist(): Promise<WatchlistResponse[]> {
  return nextFetch<WatchlistResponse[]>('/api/proxy/investor/watchlist');
}

export async function addServerWatchlist(schemeId: number): Promise<WatchlistResponse> {
  return nextFetch<WatchlistResponse>('/api/proxy/investor/watchlist', {
    method: 'POST',
    body: JSON.stringify({ schemeId }),
  });
}

export async function removeServerWatchlist(schemeId: number): Promise<void> {
  return nextFetch<void>(`/api/proxy/investor/watchlist/${schemeId}`, {
    method: 'DELETE',
  });
}

export async function fetchServerPortfolio(): Promise<PortfolioResponse[]> {
  return nextFetch<PortfolioResponse[]>('/api/proxy/investor/portfolio');
}

export async function addServerPortfolioHolding(schemeOptionId: number, units: number): Promise<PortfolioResponse> {
  return nextFetch<PortfolioResponse>('/api/proxy/investor/portfolio', {
    method: 'POST',
    body: JSON.stringify({ schemeOptionId, units }),
  });
}

export async function removeServerPortfolioHolding(schemeOptionId: number): Promise<void> {
  return nextFetch<void>(`/api/proxy/investor/portfolio/${schemeOptionId}`, {
    method: 'DELETE',
  });
}
