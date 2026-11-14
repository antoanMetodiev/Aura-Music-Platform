"use client";

import { useEffect, useId, useRef, useState } from "react";

interface ExpandableTextProps {
  text: string;
  /** Lines shown while collapsed. */
  lines?: number;
  showMoreLabel: string;
  showLessLabel: string;
  className?: string;
}

/**
 * Long prose (an artist biography) clamped to a few lines with a "show more" toggle. The toggle
 * only appears when the text actually overflows the clamp, so short bios render plainly.
 */
export function ExpandableText({ text, lines = 6, showMoreLabel, showLessLabel, className }: ExpandableTextProps) {
  const id = useId();
  const ref = useRef<HTMLDivElement>(null);
  const [expanded, setExpanded] = useState(false);
  const [overflows, setOverflows] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => setOverflows(el.scrollHeight > el.clientHeight + 1);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [text, expanded]);

  return (
    <div className={className}>
      <div
        ref={ref}
        id={id}
        className="whitespace-pre-line text-[15px] leading-relaxed text-foreground/85"
        // Inline rather than a Tailwind class: the clamp count is a prop, not a design token.
        style={expanded ? undefined : { display: "-webkit-box", WebkitBoxOrient: "vertical", WebkitLineClamp: lines, overflow: "hidden" }}
      >
        {text}
      </div>
      {(overflows || expanded) && (
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={id}
          onClick={() => setExpanded((v) => !v)}
          className="mt-2 text-sm font-semibold text-foreground hover:underline"
        >
          {expanded ? showLessLabel : showMoreLabel}
        </button>
      )}
    </div>
  );
}
