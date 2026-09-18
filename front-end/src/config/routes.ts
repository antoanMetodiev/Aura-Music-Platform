/**
 * Typed route helpers. Every internal link goes through here so a route change
 * is a one-line edit instead of a repo-wide search.
 */
export const routes = {
  home: "/home",
  search: (query?: string) =>
    query ? `/search/${encodeURIComponent(query)}` : "/search",
  artist: (id: string) => `/artists/${id}`,
  album: (id: string) => `/albums/${id}`,
  track: (id: string) => `/tracks/${id}`,
  playlist: (id: string) => `/playlists/${id}`,
  library: "/library",
  liked: "/liked",
  recentlyPlayed: "/recently-played",
  friends: "/friends",
  friendRequests: "/friends/requests",
  profile: (username: string) => `/profile/${username}`,
  notifications: "/notifications",
  settings: "/settings",
  queue: "/queue",
  login: "/login",
  register: "/register",
  forgotPassword: "/forgot-password",
  resetPassword: "/reset-password",
  privacy: "/privacy",
  terms: "/terms",
} as const;
