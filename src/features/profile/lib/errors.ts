import { useTranslations } from "next-intl";
import type { ActionResult } from "../actions";

/** Translates a failed profile action into the `profile.errors.*` text. */
export function useActionErrorText() {
  const t = useTranslations("profile");
  return (result: Extract<ActionResult, { ok: false }>) => t(`errors.${result.code}`);
}
