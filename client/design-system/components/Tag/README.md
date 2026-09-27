# Tag

A small pill naming something about a person: one of their roles, or "Owner". It has a `panel-active` fill, `text-small` at 500, and `radius-pill`. A role's Tag leads with the role's colour as an 8px dot. That is a mark rather than ink, so any colour reads; a role without a colour gets a `muted` dot. An `accent` Tag has `accent-text` on `accent-soft`, for what should stand out without being chosen.

**Provide** `children` (the name), and `color` for a role (null when it has none) or `accent`. Gather several in a `ul.sn-tags`, which wraps them 6px apart.

- Don't make Tags clickable, and don't put the sheen on them.
