# Avatar

A person, as a round picture or initials on a steady fill, optionally with a presence dot.

**Provide** `name`; `src` for a picture; `seed` (the account id) so the fill stays the same across renames; `status` for the dot; `size` of `sm` (24px), `md` (32px, the default) or `lg` (40px, for messages).

**The fill** It uses the app's hash of the seed as a hue, at one perceived lightness: `oklch(50% 0.06 hue)`, soft enough to sit quietly on the navy panes. Every hue keeps `text-strong` initials at 5:1 or more. `colourFor(seed)` returns it.

**The dot** It is 12px with a 3px ring cut in the surface's colour. `ring` names that surface (`panel` by default; the UserPanel uses `bg`). Going online, the dot sends out two soft rings. It never does this on first render, so loading a list stays still.

- Do give pictures `alt=""`; the name beside the avatar carries the meaning.
