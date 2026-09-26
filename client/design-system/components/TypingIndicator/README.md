# TypingIndicator

Three dots that bounce in sequence, each a slice of the sheen from `accent` to `accent-sheen`, and who is typing: "**Wigeon** is typing…", "**Wigeon** and **Teal** are typing…", or, past three people, "Several people are typing…".

**Provide** `names` (display names, most recent first). An empty list still renders the polite live region, so screen readers hear the next change. `compact` gives dots only, for a member row.

**Motion** Each dot rises 3px and brightens once per `duration-loop` (1200ms), 160ms after the one before. With reduced motion the dots only brighten.

- Do place it in the Composer's `footer`, so it never pushes messages around.
- Don't show the reader's own typing.
