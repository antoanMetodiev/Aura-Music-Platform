import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      // TEMPORARY: seeded placeholder artwork for the mock catalog (Home still uses it).
      { protocol: "https", hostname: "picsum.photos", pathname: "/seed/**" },
      // Real catalog artwork served by TIDAL (via catalog-svc — Project-Info.md §34: we never mirror it).
      { protocol: "https", hostname: "resources.tidal.com" },
      // Supabase Storage (avatars, playlist covers) — hostname filled from env at deploy time.
    ],
  },
};

export default withNextIntl(nextConfig);
