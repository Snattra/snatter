# Tooltip

A short label on `floating` that names an icon-only control, or says what a mark means. It appears after a 120ms rest on hover or focus, sliding 4px in from the anchor, and leaves faster than it came.

**Provide** `label`, `side` (`top`, `right`, `bottom`, or `left` for a mark at the end of a row), and one child, which the tooltip describes. Usually that is a focusable control. A mark inside a control, such as the `ban` mark on a member's row, works too: give it `role="img"` and an `aria-label`, because hover is the only way the tooltip shows. The tooltip's text is kept out of the names of the controls around it.

- Do use it on RailServers, IconButtons and status marks.
- Don't put interactive content or more than one line in it.
