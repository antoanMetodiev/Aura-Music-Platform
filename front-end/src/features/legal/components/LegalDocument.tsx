import { AuraLogo } from "@/components/layout/AuraLogo";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";

/** One document = title, "last updated", intro, sections. Lists render as bullets. */
export interface LegalSection {
  heading: string;
  /** Paragraphs; a string[] item renders as a bullet list. */
  body: (string | string[])[];
}

export interface LegalContent {
  title: string;
  updated: string;
  intro: string[];
  sections: LegalSection[];
  /** Label + href pairs for the footer nav (the other legal page, sign-in). */
  footer: { privacy: string; terms: string; signIn: string };
}

export function LegalDocument({ content }: { content: LegalContent }) {
  return (
    <article className="mx-auto w-full max-w-2xl px-5 py-12 sm:py-16">
      <AuraLogo className="mb-10" />
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{content.title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{content.updated}</p>

      <div className="mt-8 flex flex-col gap-4 text-[15px] leading-7 text-foreground/90">
        {content.intro.map((p, i) => (
          <p key={i}>{p}</p>
        ))}
      </div>

      {content.sections.map((section, i) => (
        <section key={i} className="mt-10">
          <h2 className="text-xl font-semibold tracking-tight">
            {i + 1}. {section.heading}
          </h2>
          <div className="mt-3 flex flex-col gap-3 text-[15px] leading-7 text-foreground/90">
            {section.body.map((item, j) =>
              Array.isArray(item) ? (
                <ul key={j} className="list-disc space-y-1.5 pl-6 marker:text-muted-foreground">
                  {item.map((li, k) => (
                    <li key={k}>{li}</li>
                  ))}
                </ul>
              ) : (
                <p key={j}>{item}</p>
              ),
            )}
          </div>
        </section>
      ))}

      <nav className="mt-14 flex flex-wrap gap-x-6 gap-y-2 border-t border-border pt-6 text-sm text-muted-foreground">
        <Link href={routes.privacy} className="hover:text-foreground">
          {content.footer.privacy}
        </Link>
        <Link href={routes.terms} className="hover:text-foreground">
          {content.footer.terms}
        </Link>
        <Link href={routes.login} className="hover:text-foreground">
          {content.footer.signIn}
        </Link>
      </nav>
    </article>
  );
}
