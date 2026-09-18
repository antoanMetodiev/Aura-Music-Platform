import "server-only";

/**
 * Transactional auth mail (verification, password reset).
 *
 * Resend (Project-Info.md §31) is the intended provider; until `RESEND_API_KEY` is set the links
 * are printed to the server console so the flows can be exercised locally. Nothing here ever runs
 * in the browser.
 */
const FROM = process.env.AUTH_EMAIL_FROM ?? "Aura <onboarding@resend.dev>";

export async function sendVerificationEmail(to: string, url: string): Promise<void> {
  await send({
    to,
    subject: "Verify your Aura email",
    text: `Welcome to Aura. Confirm your email address by opening this link:\n\n${url}\n\nIf you didn't create an account, ignore this message.`,
  });
}

export async function sendPasswordResetEmail(to: string, url: string): Promise<void> {
  await send({
    to,
    subject: "Reset your Aura password",
    text: `Someone asked to reset the password for this Aura account. Open the link to choose a new one:\n\n${url}\n\nThe link expires in one hour. If it wasn't you, nothing changes.`,
  });
}

async function send(mail: { to: string; subject: string; text: string }): Promise<void> {
  const apiKey = process.env.RESEND_API_KEY;
  if (!apiKey) {
    console.info(`[auth-mail] (no RESEND_API_KEY — not sent)\n  to: ${mail.to}\n  subject: ${mail.subject}\n  ${mail.text.replace(/\n/g, "\n  ")}`);
    return;
  }

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: FROM, to: mail.to, subject: mail.subject, text: mail.text }),
  });
  if (!response.ok) {
    throw new Error(`Resend refused the message (${response.status}): ${await response.text()}`);
  }
}
