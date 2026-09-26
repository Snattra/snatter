# Banner

A full-width strip at the top of the channel pane for a connection-wide state. It slides down from the header's edge when it mounts.

**Provide** `children` (a few words: "Reconnecting…"), `busy` for a state that will resolve on its own, and `tone`: `warning` (the default) for degraded, `danger` for lost, `accent` (the sheen, with dark text) for information.

- Do render it only while the state holds, in the AppShell's `banner` slot.
- Don't stack banners; show the most severe one.
