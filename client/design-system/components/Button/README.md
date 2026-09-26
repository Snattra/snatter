# Button

A labelled action. `primary` is the one thing a screen is for; `secondary` is the default; `danger` is for moderation and deletion; `link` is for quiet text actions such as Sign out.

**Provide** `children` (a short verb phrase: "Sign in", "Create account", "Ban member"), `onClick` or `type="submit"`, and `busy` while the action runs. Busy disables the button and shows a spinner; keep the label or swap it for a present participle ("Signing in…").

**Look and motion** `primary` is the sheen with a dark `on-accent` label. On hover the sheen slides toward `accent-sheen` and the `shadow-accent` glow rises beneath it (`duration-base`). `secondary` lightens a step, and `danger` (on `danger-strong`, with an `on-danger` label) darkens. Pressing scales any of them to 98%. Focus shows the solid `focus` ring.

- Do use one primary per view.
- Don't use `danger` for Cancel or Sign out.
