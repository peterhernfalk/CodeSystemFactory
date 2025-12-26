// API configuration
// In development, Vite proxy handles /api requests
// In production, use VITE_API_URL environment variable or default to relative path
const getApiBaseUrl = (): string => {
  // In development, use relative path (Vite proxy handles it)
  if (import.meta.env.DEV) {
    return '/api'
  }
  
  // In production, use environment variable or default to relative path
  const apiUrl = import.meta.env.VITE_API_URL
  if (apiUrl) {
    // Remove trailing slash if present, and ensure it doesn't end with /api
    let url = apiUrl.replace(/\/$/, '')
    // If the URL already includes /api, use it as-is, otherwise append /api
    if (!url.endsWith('/api')) {
      url = url + '/api'
    }
    return url
  }
  
  // Fallback to relative path (for same-origin deployments)
  return '/api'
}

export const API_BASE_URL = getApiBaseUrl()

