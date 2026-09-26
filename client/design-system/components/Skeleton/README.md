# Skeleton

A placeholder in the shape of content that is still loading, with `gradient-shimmer` sweeping across it.

**Provide** `variant`: `line` (with a `width`), `circle` (an avatar) or `message` (avatar and three lines). Mark the region `aria-busy`. With reduced motion the sweep stops.

- Do use it for message history and the member list on first load, in place of "Loading…".
- Don't keep a skeleton for more than a moment. After a few seconds, say what is happening.
