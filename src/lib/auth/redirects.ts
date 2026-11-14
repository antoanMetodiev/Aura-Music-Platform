/** Only same-site paths — never an absolute URL someone put in a query string. */
export function safePath(value: string | null | undefined, fallback = "/home"): string {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : fallback;
}
