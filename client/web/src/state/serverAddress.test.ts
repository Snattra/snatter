import { describe, expect, it } from "vitest";
import { blockedAsInsecure, serverOrigin } from "./serverAddress";

describe("serverOrigin", () => {
  it("takes a bare host as HTTPS", () => {
    expect(serverOrigin("demo.snatter.app")).toBe("https://demo.snatter.app");
    expect(serverOrigin("  Chat.Example.com:8443 ")).toBe("https://chat.example.com:8443");
  });

  it("takes a host on this machine as plain HTTP", () => {
    expect(serverOrigin("localhost:8080")).toBe("http://localhost:8080");
    expect(serverOrigin("127.0.0.1:8080")).toBe("http://127.0.0.1:8080");
    expect(serverOrigin("chat.localhost:8080")).toBe("http://chat.localhost:8080");
    // Only those the hosted app's policy lets it reach.
    expect(serverOrigin("[::1]:8080")).toBe("https://[::1]:8080");
    expect(serverOrigin("127.0.0.2:8080")).toBe("https://127.0.0.2:8080");
  });

  it("keeps the scheme it is given, and leaves out the path and the scheme's own port", () => {
    expect(serverOrigin("http://192.168.1.10:8080/")).toBe("http://192.168.1.10:8080");
    expect(serverOrigin("https://demo.snatter.app/invite/k3Jd8QzP")).toBe("https://demo.snatter.app");
    expect(serverOrigin("https://demo.snatter.app:443")).toBe("https://demo.snatter.app");
  });

  it("names no server for anything else", () => {
    for (const typed of [
      "",
      "  ",
      "demo snatter app",
      "ftp://files.example.com",
      "mailto:someone@example.com",
      "https://",
    ]) {
      expect(serverOrigin(typed), typed).toBeNull();
    }
  });
});

describe("blockedAsInsecure", () => {
  it("blocks plain HTTP from an HTTPS page, unless it is on this machine", () => {
    expect(blockedAsInsecure("http://192.168.1.10:8080", "https:")).toBe(true);
    expect(blockedAsInsecure("http://localhost:8080", "https:")).toBe(false);
    expect(blockedAsInsecure("http://chat.localhost:8080", "https:")).toBe(false);
    expect(blockedAsInsecure("http://[::1]:8080", "https:")).toBe(true);
    expect(blockedAsInsecure("https://demo.snatter.app", "https:")).toBe(false);
    expect(blockedAsInsecure("http://192.168.1.10:8080", "http:")).toBe(false);
  });
});
