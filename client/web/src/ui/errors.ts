import { ApiRequestError } from "../api/client";

/** A sentence for the user about a failed action. Never throws. */
export function describeError(error: unknown): string {
  if (error instanceof ApiRequestError) {
    const { body } = error;
    if (body.error === "banned") {
      return body.ban?.reason ? `You are banned from this server: ${body.ban.reason}` : "You are banned from this server.";
    }
    const fields = Object.entries(body.fields ?? {});
    if (fields.length > 0) {
      return fields.map(([field, message]) => `${field}: ${message}`).join(". ");
    }
    return body.message;
  }
  return error instanceof Error ? error.message : "Something went wrong.";
}
