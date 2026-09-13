import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      // TEMPORARY: seeded placeholder artwork for the mock catalog.
      { protocol: "https", hostname: "picsum.photos", pathname: "/seed/**" },
      // Supabase Storage (avatars, playlist covers) — hostname filled from env at deploy time.
    ],
  },
};

export default withNextIntl(nextConfig);
