/**
 * Endpoints for the third-party map services the UI talks to directly.
 *
 * The defaults are free, community-run demo instances:
 *   - router.project-osrm.org   routing/ETA
 *   - photon.komoot.io          place autocomplete
 *   - tile.openstreetmap.org    map tiles
 *
 * All three are rate limited and their usage policies do not permit production traffic — the
 * tile server in particular blocks apps that send real volume. They are fine for development,
 * but a deployment must point these at self-hosted or commercial endpoints. Every value is
 * overridable at build time so that does not require code changes.
 */
const trimTrailingSlash = (value) => (value || '').replace(/\/+$/, '')

export const OSRM_BASE_URL = trimTrailingSlash(
  import.meta.env.VITE_OSRM_BASE_URL || 'https://router.project-osrm.org'
)

export const PHOTON_URL = `${trimTrailingSlash(
  import.meta.env.VITE_PHOTON_BASE_URL || 'https://photon.komoot.io'
)}/api/`

export const TILE_URL =
  import.meta.env.VITE_MAP_TILE_URL || 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'

export const TILE_ATTRIBUTION =
  import.meta.env.VITE_MAP_TILE_ATTRIBUTION || '&copy; OpenStreetMap contributors'
