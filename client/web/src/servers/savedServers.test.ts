import { afterEach, describe, expect, it, vi } from "vitest";
import { platform } from "../platform/platform";
import { withServer } from "../state/serverList";
import { loadSavedServers, useSavedServers } from "./savedServers";

const demo = { origin: "https://demo.snatter.app", name: "Snatter Demo Server" };
const home = { origin: "http://192.168.1.10:8080", name: "Pondside" };

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useSavedServers", () => {
  it("keeps the lists in the order they changed, though storage answers late", async () => {
    const saved: string[] = [];
    const answers: (() => void)[] = [];
    vi.spyOn(platform.settings, "set").mockImplementation(async (_key, value) => {
      await new Promise<void>((resolve) => answers.push(resolve));
      saved.push(value);
    });
    const { change } = useSavedServers.getState();

    change((list) => withServer(list, demo));
    change((list) => withServer(list, home));
    // The second save waits for the first, so it cannot land before it.
    await vi.waitFor(() => expect(answers).toHaveLength(1));
    answers[0]?.();
    await vi.waitFor(() => expect(answers).toHaveLength(2));
    answers[1]?.();
    await vi.waitFor(() => expect(saved).toHaveLength(2));

    expect(JSON.parse(saved[1] ?? "{}").selected).toBe(home.origin);
    expect(JSON.parse(saved[1] ?? "{}").servers).toHaveLength(2);
  });
});

describe("loadSavedServers", () => {
  it("reads what was kept once, however often it is asked", async () => {
    const read = vi
      .spyOn(platform.settings, "get")
      .mockResolvedValue(JSON.stringify({ servers: [demo], selected: null }));

    await Promise.all([loadSavedServers(), loadSavedServers()]);

    expect(read).toHaveBeenCalledTimes(1);
    expect(useSavedServers.getState().loaded).toBe(true);
  });
});
