import { createNavigation } from "next-intl/navigation";
import { routing } from "./routing";

/**
 * Locale-aware drop-ins for next/link and next/navigation.
 * ALWAYS import these instead of the Next.js originals inside the app —
 * they prepend the active locale (`/bg/home`) automatically.
 */
export const { Link, redirect, usePathname, useRouter, getPathname } = createNavigation(routing);
