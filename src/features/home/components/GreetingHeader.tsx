"use client";

import { useSyncExternalStore } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { getGreetingKey } from "@/lib/utils/format";

interface GreetingHeaderProps {
  /** `null` for a guest — greeting only. */
  name: string | null;
  liveFriends: number;
}

const subscribe = () => () => {};
/** Minute-bucketed clock: stable across renders, hydration-safe (null on the server). */
const getMinute = () => Math.floor(Date.now() / 60_000);
const getServerMinute = () => null;

/** Time-of-day greeting using the viewer's clock, with a neutral first paint. */
export function GreetingHeader({ name, liveFriends }: GreetingHeaderProps) {
  const t = useTranslations("home");
  const format = useFormatter();
  const minute = useSyncExternalStore(subscribe, getMinute, getServerMinute);
  const now = minute === null ? null : new Date(minute * 60_000);

  const greeting = now ? t(`greeting.${getGreetingKey(now)}`) : t("greeting.fallback");
  const date = now ? format.dateTime(now, { weekday: "long", day: "numeric", month: "long" }) : "";

  return (
    <div className="flex flex-col gap-1">
      <p className="text-xs font-medium tracking-[0.18em] text-primary-hover uppercase">{date || " "}</p>
      <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">
        {name ? `${greeting}, ${name}` : greeting}
      </h1>
      <p className="text-sm text-muted-foreground">
        {liveFriends > 0 ? (
          <>
            <span className="mr-1.5 inline-block size-1.5 animate-pulse rounded-full bg-success align-middle" />
            {t("friendsListeningCount", { count: liveFriends })}
          </>
        ) : (
          t("friendsQuiet")
        )}
      </p>
    </div>
  );
}
