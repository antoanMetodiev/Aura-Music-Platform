import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import type { UserSummary } from "@/types/social";

interface UserAvatarProps {
  user: Pick<UserSummary, "displayName" | "avatarUrl" | "username">;
  size?: "sm" | "default" | "lg";
  className?: string;
}

export function UserAvatar({ user, size = "default", className }: UserAvatarProps) {
  return (
    <Avatar size={size} className={cn("after:border-white/10", className)}>
      {/* no-referrer: Google profile photos (lh3.googleusercontent.com) refuse requests that carry
          a third-party Referer, so the image would silently fall back to initials. */}
      {user.avatarUrl ? <AvatarImage src={user.avatarUrl} alt={user.displayName} referrerPolicy="no-referrer" /> : null}
      <AvatarFallback
        className="font-medium text-white/90"
        style={{ backgroundImage: avatarGradient(user.username) }}
      >
        {initials(user.displayName)}
      </AvatarFallback>
    </Avatar>
  );
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return (parts[0]?.[0] ?? "" ) + (parts[1]?.[0] ?? "");
}

function avatarGradient(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  const hue = Math.abs(hash) % 360;
  return `linear-gradient(135deg, hsl(${hue} 45% 42%) 0%, hsl(${(hue + 40) % 360} 50% 26%) 100%)`;
}
