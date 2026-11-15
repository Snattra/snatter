import { describe, expect, it } from "vitest";
import type { Invite } from "../api/types";
import { hostedInviteIn, inviteCodeIn, reusableInvite } from "./invites";

const now = Date.parse("2026-09-27T12:00:00Z");
const hour = 60 * 60 * 1000;

function invite(code: string, changes: Partial<Invite> = {}): Invite {
  return {
    code,
    url: `https://chat.example.com/invite/${code}`,
    createdBy: "me",
    createdAt: "2026-09-27T00:00:00Z",
    expiresAt: new Date(now + 7 * 24 * hour).toISOString(),
    maxUses: null,
    uses: 0,
    revoked: false,
    ...changes,
  };
}

describe("inviteCodeIn", () => {
  it("reads the code from an invite link's path", () => {
    expect(inviteCodeIn("/invite/k3Jd8QzP")).toBe("k3Jd8QzP");
    expect(inviteCodeIn("/invite/k3Jd8QzP/")).toBe("k3Jd8QzP");
  });

  it("ignores other paths and malformed codes", () => {
    expect(inviteCodeIn("/")).toBeNull();
    expect(inviteCodeIn("/invite/")).toBeNull();
    expect(inviteCodeIn("/invite/short")).toBeNull();
    expect(inviteCodeIn("/invite/k3Jd8QzP/more")).toBeNull();
    expect(inviteCodeIn("/invite/k3Jd-QzP")).toBeNull();
  });
});

describe("reusableInvite", () => {
  it("picks the newest of the member's own invites", () => {
    const invites = [invite("others00", { createdBy: "bob" }), invite("newest00"), invite("older000")];
    expect(reusableInvite(invites, "me", now)?.code).toBe("newest00");
  });

  it("reuses one that never expires", () => {
    expect(reusableInvite([invite("forever0", { expiresAt: null })], "me", now)?.code).toBe("forever0");
  });

  it("skips invites that are revoked, limited or about to expire", () => {
    const invites = [
      invite("revoked0", { revoked: true }),
      invite("limited0", { maxUses: 5 }),
      invite("expiring", { expiresAt: new Date(now + 23 * hour).toISOString() }),
      invite("deleted0", { createdBy: null }),
    ];
    expect(reusableInvite(invites, "me", now)).toBeNull();
  });
});

describe("hostedInviteIn", () => {
  it("reads the server and the code after the #", () => {
    expect(hostedInviteIn("/invite", "#demo.snatter.app/k3Jd8QzP")).toEqual({
      origin: "https://demo.snatter.app",
      code: "k3Jd8QzP",
    });
    expect(hostedInviteIn("/invite/", "#localhost:8080/k3Jd8QzP/")).toEqual({
      origin: "http://localhost:8080",
      code: "k3Jd8QzP",
    });
    expect(hostedInviteIn("/invite", "#chat.example.com%3A8443/k3Jd8QzP")?.origin).toBe(
      "https://chat.example.com:8443",
    );
  });

  it("is no invite for any other address", () => {
    for (const [path, hash] of [
      ["/", "#demo.snatter.app/k3Jd8QzP"],
      ["/invite/k3Jd8QzP", ""],
      ["/invite", ""],
      ["/invite", "#demo.snatter.app"],
      ["/invite", "#demo.snatter.app/short"],
      ["/invite", "#/k3Jd8QzP"],
      ["/invite", "#%E0%A4%A/k3Jd8QzP"],
      ["/invite", "#user@demo.snatter.app/k3Jd8QzP"],
    ]) {
      expect(hostedInviteIn(path ?? "", hash ?? ""), `${path}${hash}`).toBeNull();
    }
  });
});
