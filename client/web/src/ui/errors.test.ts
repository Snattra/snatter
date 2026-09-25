import { describe, expect, it } from "vitest";
import { ApiRequestError } from "../api/client";
import { describeError } from "./errors";

describe("describeError", () => {
  it("uses the message when the server sends null for the optional parts", () => {
    const error = new ApiRequestError(401, {
      error: "invalid_credentials",
      message: "Unknown username or wrong password",
      fields: null,
      ban: null,
    });
    expect(describeError(error)).toBe("Unknown username or wrong password");
  });

  it("lists per-field messages", () => {
    const error = new ApiRequestError(400, {
      error: "validation_failed",
      message: "Request is invalid",
      fields: { username: "too short", password: "too short" },
    });
    expect(describeError(error)).toBe("username: too short. password: too short");
  });

  it("gives the ban reason", () => {
    const error = new ApiRequestError(403, {
      error: "banned",
      message: "You are banned from this server",
      ban: { reason: "spamming", bannedAt: "2026-01-01T00:00:00Z" },
    });
    expect(describeError(error)).toBe("You are banned from this server: spamming");
  });
});
