import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";
import { initOpenNextCloudflareForDev } from "@opennextjs/cloudflare";

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

// Makes the Workers bindings (IMAGES, HYPERDRIVE…) available to `next dev` too, so code that
// reads getCloudflareContext() behaves the same locally as deployed.
initOpenNextCloudflareForDev();
