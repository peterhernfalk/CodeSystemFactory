import { API_BASE_URL } from './api'

// Version information
// This is read from package.json at build time via Vite
export const FRONTEND_VERSION = import.meta.env.VITE_APP_VERSION || '0.1.0';

// Fetch backend version from API
export async function getBackendVersion(): Promise<string | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/version`);
    if (response.ok) {
      const data = await response.json();
      return data.version || null;
    }
  } catch (error) {
    console.warn('Failed to fetch backend version:', error);
  }
  return null;
}

