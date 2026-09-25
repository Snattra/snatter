import createClient from "openapi-fetch";
import type { paths } from "./schema";
import type { ApiErrorBody } from "./types";

export type Api = ReturnType<typeof createApi>;

/** A typed client for one server, sending the session token when there is one. */
export function createApi(origin: string, token: () => string | null) {
  const client = createClient<paths>({ baseUrl: origin });
  client.use({
    onRequest({ request }) {
      const current = token();
      if (current !== null) {
        request.headers.set("Authorization", `Bearer ${current}`);
      }
      return request;
    },
  });
  return client;
}

/** A request the server answered with an error, carrying its {@code ApiError} body. */
export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    readonly body: ApiErrorBody,
  ) {
    super(body.message);
  }

  get code(): string {
    return this.body.error;
  }
}

/** The data of a successful response (undefined for 204), or an {@link ApiRequestError}. */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error !== undefined || !result.response.ok) {
    const body = isApiError(result.error)
      ? result.error
      : { error: "unexpected_response", message: `The server answered ${result.response.status}` };
    throw new ApiRequestError(result.response.status, body);
  }
  return result.data as T;
}

function isApiError(value: unknown): value is ApiErrorBody {
  return typeof value === "object" && value !== null && "error" in value && "message" in value;
}
