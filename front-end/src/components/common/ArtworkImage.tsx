import Image from "next/image";
import { Music2 } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Artwork } from "@/types/catalog";

interface ArtworkImageProps {
  artwork?: Artwork;
  alt: string;
  /** Used to pick a stable fallback gradient when there's no artwork. */
  seed?: string;
  shape?: "square" | "circle";
  /** `sizes` hint for next/image — pass the rendered width, e.g. "176px". */
  sizes?: string;
  priority?: boolean;
  className?: string;
  iconClassName?: string;
}

/**
 * The single place that renders catalog artwork. Handles the "no artwork yet"
 * state with a deterministic navy-ish gradient so lists never show broken images.
 */
export function ArtworkImage({
  artwork,
  alt,
  seed = alt,
  shape = "square",
  sizes = "200px",
  priority,
  className,
  iconClassName,
}: ArtworkImageProps) {
  const rounded = shape === "circle" ? "rounded-full" : "rounded-md";

  if (!artwork) {
    return (
      <div
        aria-label={alt}
        role="img"
        className={cn(
          "relative flex items-center justify-center overflow-hidden",
          rounded,
          className,
        )}
        style={{ backgroundImage: fallbackGradient(seed) }}
      >
        <Music2 className={cn("size-[28%] text-white/60", iconClassName)} strokeWidth={1.5} />
      </div>
    );
  }

  return (
    <div className={cn("relative overflow-hidden bg-elevated", rounded, className)}>
      <Image
        src={artwork.url}
        alt={alt}
        fill
        sizes={sizes}
        priority={priority}
        className="object-cover"
      />
    </div>
  );
}

/** Stable hue pair per seed, biased toward the brand's blue/teal range. */
function fallbackGradient(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  const hue = 190 + (Math.abs(hash) % 70); // 190..260 → teal → blue → indigo
  const hue2 = hue + 25;
  return `linear-gradient(135deg, hsl(${hue} 55% 34%) 0%, hsl(${hue2} 60% 18%) 100%)`;
}
