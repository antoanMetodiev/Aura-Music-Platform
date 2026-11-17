"use client";

import { useEffect, useState } from "react";
import { Loader2, Search } from "lucide-react";
import { useTranslations } from "next-intl";
import { getClientAccessToken } from "@/lib/api/token";
import { Input } from "@/components/ui/input";
import { searchPeople, type PersonResult } from "../api/socialApi";
import { FriendButton } from "./FriendButton";
import { PersonRow } from "./PersonRow";

const DEBOUNCE_MS = 250;

/** Find people by username or name and add them right from the result row. */
export function PeopleSearch() {
  const t = useTranslations("social");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PersonResult[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  const trimmed = query.trim();

  useEffect(() => {
    if (!trimmed) return;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setLoading(true);
      setFailed(false);
      try {
        setResults(await searchPeople(await getClientAccessToken(), trimmed, controller.signal));
      } catch {
        if (!controller.signal.aborted) {
          setResults(null);
          setFailed(true);
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [trimmed]);

  return (
    <section className="flex flex-col gap-3">
      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t("searchPlaceholder")}
          aria-label={t("searchPlaceholder")}
          className="h-11 rounded-full pl-9"
          maxLength={50}
        />
        {loading && trimmed ? (
          <Loader2 className="absolute top-1/2 right-3 size-4 -translate-y-1/2 animate-spin text-muted-foreground" />
        ) : null}
      </div>

      {!trimmed ? null : failed ? (
        <p className="px-2 text-sm text-muted-foreground">{t("unavailable")}</p>
      ) : results && results.length === 0 && !loading ? (
        <p className="px-2 text-sm text-muted-foreground">{t("noPeopleFound", { query: trimmed })}</p>
      ) : results ? (
        <ul className="flex flex-col">
          {results.map((person) => (
            <PersonRow
              key={person.user.id}
              user={person.user}
              actions={
                <FriendButton
                  // Remount when a fresh search reports a different state for the same person.
                  key={person.relationship}
                  userId={person.user.id}
                  displayName={person.user.displayName}
                  initial={person.relationship}
                  compact
                />
              }
            />
          ))}
        </ul>
      ) : null}
    </section>
  );
}
