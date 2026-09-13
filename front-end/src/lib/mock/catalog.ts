/**
 * TEMPORARY mock catalog used until the Music Catalog / Library services exist.
 * Shapes match `@/types/catalog` exactly so swapping to real data is a drop-in.
 * Artwork uses seeded picsum.photos images (Unsplash-licensed) — no copyrighted covers.
 */
import type {
  AlbumSummary,
  ArtistSummary,
  Artwork,
  PlaylistSummary,
  Track,
} from "@/types/catalog";

export const artwork = (seed: string, dominantColor?: string): Artwork => ({
  url: `https://picsum.photos/seed/${seed}/400/400`,
  dominantColor,
});

const artist = (id: string, name: string): ArtistSummary => ({
  id,
  name,
  artwork: artwork(`artist-${id}`),
});

export const artists = {
  billie: artist("billie-eilish", "Billie Eilish"),
  radiohead: artist("radiohead", "Radiohead"),
  weeknd: artist("the-weeknd", "The Weeknd"),
  tame: artist("tame-impala", "Tame Impala"),
  arctic: artist("arctic-monkeys", "Arctic Monkeys"),
  lana: artist("lana-del-rey", "Lana Del Rey"),
  bonobo: artist("bonobo", "Bonobo"),
  frank: artist("frank-ocean", "Frank Ocean"),
  kendrick: artist("kendrick-lamar", "Kendrick Lamar"),
  daft: artist("daft-punk", "Daft Punk"),
  sza: artist("sza", "SZA"),
  khalid: artist("khalid", "Khalid"),
} satisfies Record<string, ArtistSummary>;

const album = (
  id: string,
  title: string,
  artist: ArtistSummary,
  releaseYear: number,
  dominantColor?: string,
): AlbumSummary => ({
  id,
  title,
  artist,
  releaseYear,
  artwork: artwork(`album-${id}`, dominantColor),
});

export const albums = {
  hmhas: album("hit-me-hard-and-soft", "HIT ME HARD AND SOFT", artists.billie, 2024, "#1b3a6b"),
  inRainbows: album("in-rainbows", "In Rainbows", artists.radiohead, 2007, "#6b2d1b"),
  afterHours: album("after-hours", "After Hours", artists.weeknd, 2020, "#7a1f1f"),
  currents: album("currents", "Currents", artists.tame, 2015, "#2a4d6b"),
  am: album("am", "AM", artists.arctic, 2013, "#111111"),
  nfr: album("norman-rockwell", "Norman F***ing Rockwell!", artists.lana, 2019, "#2f5a7a"),
  fragments: album("fragments", "Fragments", artists.bonobo, 2022, "#3b2f6b"),
  blonde: album("blonde", "Blonde", artists.frank, 2016, "#4a6b3b"),
  damn: album("damn", "DAMN.", artists.kendrick, 2017, "#6b1b1b"),
  ram: album("random-access-memories", "Random Access Memories", artists.daft, 2013, "#1b1b1b"),
  sos: album("sos", "SOS", artists.sza, 2022, "#1b4a6b"),
} satisfies Record<string, AlbumSummary>;

const track = (
  id: string,
  title: string,
  album: AlbumSummary,
  durationMs: number,
  opts: { explicit?: boolean; feat?: ArtistSummary[]; playbackAvailable?: boolean } = {},
): Track => ({
  id,
  title,
  album,
  durationMs,
  explicit: opts.explicit ?? false,
  artists: [album.artist, ...(opts.feat ?? [])],
  artwork: album.artwork,
  playbackAvailable: opts.playbackAvailable ?? true,
});

export const tracks: Track[] = [
  track("birds-of-a-feather", "BIRDS OF A FEATHER", albums.hmhas, 210_000),
  track("wildflower", "WILDFLOWER", albums.hmhas, 261_000),
  track("blue", "BLUE", albums.hmhas, 343_000),
  track("lovely", "lovely", albums.hmhas, 200_000, { feat: [artists.khalid] }),
  track("nude", "Nude", albums.inRainbows, 255_000),
  track("weird-fishes", "Weird Fishes / Arpeggi", albums.inRainbows, 318_000),
  track("blinding-lights", "Blinding Lights", albums.afterHours, 200_000),
  track("after-hours", "After Hours", albums.afterHours, 361_000),
  track("the-less-i-know", "The Less I Know The Better", albums.currents, 216_000),
  track("let-it-happen", "Let It Happen", albums.currents, 467_000),
  track("do-i-wanna-know", "Do I Wanna Know?", albums.am, 272_000),
  track("505", "505", albums.am, 253_000),
  track("venice-bitch", "Venice Bitch", albums.nfr, 590_000, { explicit: true }),
  track("rosie", "Rosewood", albums.fragments, 316_000),
  track("pink-white", "Pink + White", albums.blonde, 184_000),
  track("humble", "HUMBLE.", albums.damn, 177_000, { explicit: true }),
  track("instant-crush", "Instant Crush", albums.ram, 337_000),
  track("kill-bill", "Kill Bill", albums.sos, 153_000, { explicit: true }),
  track("nights", "Nights", albums.blonde, 307_000, { playbackAvailable: false }),
];

export const trackById = (id: string): Track | undefined =>
  tracks.find((t) => t.id === id);

const owner = { id: "me", displayName: "Antoan" };
const aura = { id: "aura", displayName: "Aura" };

export const playlists: PlaylistSummary[] = [
  {
    id: "liked",
    title: "Liked Songs",
    trackCount: 214,
    owner,
    kind: "user",
  },
  {
    id: "coding-sessions",
    title: "Coding Sessions",
    description: "Deep focus. No lyrics. Mostly.",
    artwork: artwork("pl-coding", "#1b3a6b"),
    trackCount: 86,
    owner,
    kind: "user",
  },
  {
    id: "late-night-drive",
    title: "Late Night Drive",
    description: "Synths, neon and empty highways.",
    artwork: artwork("pl-drive", "#2a1b4d"),
    trackCount: 42,
    owner,
    kind: "user",
  },
  {
    id: "gym",
    title: "Gym",
    artwork: artwork("pl-gym", "#4d1b1b"),
    trackCount: 58,
    owner,
    kind: "user",
  },
  {
    id: "sunday-morning",
    title: "Sunday Morning",
    artwork: artwork("pl-sunday", "#4d3b1b"),
    trackCount: 31,
    owner,
    kind: "user",
  },
  {
    id: "daily-mix-1",
    title: "Daily Mix 1",
    description: "Billie Eilish, Lana Del Rey, Frank Ocean and more",
    artwork: artwork("mix-1"),
    trackCount: 50,
    owner: aura,
    kind: "mix",
  },
  {
    id: "daily-mix-2",
    title: "Daily Mix 2",
    description: "Radiohead, Tame Impala, Arctic Monkeys and more",
    artwork: artwork("mix-2"),
    trackCount: 50,
    owner: aura,
    kind: "mix",
  },
  {
    id: "daily-mix-3",
    title: "Daily Mix 3",
    description: "Kendrick Lamar, SZA, The Weeknd and more",
    artwork: artwork("mix-3"),
    trackCount: 50,
    owner: aura,
    kind: "mix",
  },
  {
    id: "discover-weekly",
    title: "Discover Weekly",
    description: "Your weekly mixtape of fresh music. Updates every Monday.",
    artwork: artwork("discover"),
    trackCount: 30,
    owner: aura,
    kind: "editorial",
  },
  {
    id: "release-radar",
    title: "Release Radar",
    description: "New releases from artists you follow. Updates every Friday.",
    artwork: artwork("radar"),
    trackCount: 30,
    owner: aura,
    kind: "editorial",
  },
  {
    id: "deep-focus",
    title: "Deep Focus",
    description: "Keep calm and focus with ambient and post-rock.",
    artwork: artwork("deep-focus"),
    trackCount: 120,
    owner: aura,
    kind: "editorial",
  },
  {
    id: "night-rider",
    title: "Night Rider",
    description: "Dark synthwave for the 3AM city.",
    artwork: artwork("night-rider"),
    trackCount: 74,
    owner: aura,
    kind: "editorial",
  },
];

export const playlistById = (id: string): PlaylistSummary | undefined =>
  playlists.find((p) => p.id === id);

/** Deterministic pseudo-tracklist for any playlist/album id (rotates the pool). */
export const tracksFor = (contextId: string, count = 8): Track[] => {
  let hash = 0;
  for (let i = 0; i < contextId.length; i++) hash = (hash * 31 + contextId.charCodeAt(i)) | 0;
  const start = Math.abs(hash) % tracks.length;
  return Array.from({ length: Math.min(count, tracks.length) }, (_, i) => tracks[(start + i) % tracks.length]!);
};

export const albumTracks = (albumId: string): Track[] => {
  const own = tracks.filter((t) => t.album.id === albumId);
  return own.length > 0 ? own : tracksFor(albumId, 6);
};

/** Own playlists for the sidebar — Liked Songs has its own nav entry. */
export const userPlaylists = playlists.filter((p) => p.kind === "user" && p.id !== "liked");
export const madeForYou = playlists.filter((p) => p.kind === "mix" || p.kind === "editorial");
export const recommendedPlaylists = playlists.filter((p) => p.kind === "editorial");

export const newReleases: AlbumSummary[] = [
  albums.hmhas,
  albums.sos,
  albums.fragments,
  albums.damn,
  albums.afterHours,
  albums.nfr,
  albums.blonde,
];

export const recentlyPlayed: (Track | AlbumSummary | PlaylistSummary)[] = [
  tracks[0],
  playlistById("coding-sessions")!,
  albums.inRainbows,
  tracks[8],
  playlistById("late-night-drive")!,
  albums.currents,
  tracks[16],
  albums.blonde,
];

export const similarArtists: ArtistSummary[] = [
  artists.tame,
  artists.arctic,
  artists.bonobo,
  artists.frank,
  artists.lana,
  artists.daft,
  artists.weeknd,
];
