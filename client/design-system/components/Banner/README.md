# Banner

A full-width strip at the top of the channel pane for a state that holds wherever you go: the connection ("Reconnecting…"), or your own account ("You're timed out until today at 18:30. You can read, but not write."). It slides down from the header's edge when it mounts.

**Provide** `children` (a sentence or two at most), `busy` for a state that will resolve on its own, and `tone`: `warning` (the default) for degraded, `danger` for lost, `accent` (the sheen, with dark text) for information. Reconnecting and a timeout are both `warning`.

- Do render it only while the state holds, in the AppShell's `banner` slot. A timeout's banner goes when the timeout does.
- Don't stack banners; show the most severe one. The connection comes first: while it is down, nothing else on screen is current.
