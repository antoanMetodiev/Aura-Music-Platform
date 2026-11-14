import { defineCloudflareConfig } from "@opennextjs/cloudflare";

/**
 * How OpenNext maps Next.js onto Workers primitives.
 *
 * No incremental cache override on purpose: every page here is either static (rendered at build
 * time, served from the assets binding) or dynamic per request — catalog data comes from the API
 * gateway through TanStack Query, and anything behind a session cannot be shared anyway. There is
 * no ISR to persist, so an R2/KV cache would add a binding and a bill for nothing.
 *
 * If ISR or `use cache` arrives later, add an R2 bucket and:
 *   import r2IncrementalCache from "@opennextjs/cloudflare/overrides/incremental-cache/r2-incremental-cache";
 *   export default defineCloudflareConfig({ incrementalCache: r2IncrementalCache });
 */
export default defineCloudflareConfig();
