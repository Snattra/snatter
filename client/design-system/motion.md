# Motion

Every animation in Snatter answers one question: *what just changed?* A thing arrives, moves to where you chose, or confirms that it worked. Nothing animates to look lively.

## Timing

- `duration-instant` (100ms): hover fills and colour, press feedback. Hover must feel immediate.
- `duration-fast` (150ms): focus glows, tooltips, popovers, fades.
- `duration-base` (200ms): selection moving (the tab fill, the rail pill, a server icon's shape), the sheen sliding on a hovered button, and arrivals (a message, a banner).
- `duration-slow` (300ms): layout (the sidebar collapsing, the member list sliding shut) and a card or modal entering.
- `duration-loop` (1200ms): one cycle of an ambient loop: the typing dots, and the skeleton sweep at the same pace.

Curves:
- `ease-standard` for anything moving between two states. It is the default.
- `ease-out` for arrivals: fast start, soft landing.
- `ease-in` for departures, which are always quicker than arrivals.
- `ease-spring` overshoots slightly, for small marks that confirm a choice: the rail pill, the tab fill, the send button appearing. Never use it on panes or text.

## The catalogue

These live in `bundle.css` as keyframes and transitions. Each one has one meaning.

| Motion | Where | What it says |
|---|---|---|
| Circle to rounded square, the sheen fading in | Server icon on hover or select | "You're pointing at this server" / "you're here" |
| Sheen slides toward `accent-sheen`, the accent glow rises | Primary button on hover | "This is the action": the light catches it as you reach |
| Pill grows 8, 20 or 40px (`ease-spring`) | Rail edge | Unread, hover, selected: one mark, three heights |
| Fill slides between tabs (`ease-spring`) | Tabs | The choice moved |
| `sn-rise-in`: 4px up and fade (`ease-out`) | New messages, cards, field errors | Something new arrived here |
| Content fades from `muted` to `text` | A pending message confirming | It was delivered |
| `sn-slide-down` from the header edge | Banner | The connection changed |
| `sn-drop-in`: 4px down and fade (`ease-out`) | Unread bar | There is more to read above |
| `sn-pop-in`: 4px out of its control, scale 96% to 100% (`duration-fast`, `ease-out`) | Popover | It opened from what you clicked |
| `sn-rise-in` (`duration-slow`) while the scrim fades in (`duration-fast`); leaves at once | Modal | A task is in front of you now |
| Mark fills with the sheen, its dot or tick springs in (`ease-spring`) | Choice | That one is chosen |
| `sn-shake`: 4px each way, once | Invalid field on submit | That didn't work; look here |
| `sn-typing`: three sheen dots, 160ms apart | Typing indicator | Someone is writing |
| `sn-ping`: two rings from the dot | Presence turning online | They just arrived |
| `sn-shimmer`: light sweeping across | Skeleton | Still loading |
| Toolbar rises 4px, scale 96% to 100% | Message hover | You can act on this |
| Send button springs in | Composer, once text isn't blank | Ready to send |
| Grid columns resize, labels fade first | Sidebar collapse, member list toggle | The layout is changing; nothing else moves |

## Rules

- Only live events animate. History, a channel's first render, a list's first load: none of these animate. `Message` rises in only with `isNew`, and `Avatar` pings only on a change.
- Animate `transform`, `opacity` and colours. The grid columns of the shell are the only layout that moves, and they move on `duration-slow`.
- Departures run on `ease-in` at `duration-fast` or less. Nothing lingers on its way out.
- Keep icons and avatars in place while their pane resizes. Labels fade, positions don't jump.
- **Reduced motion** (`prefers-reduced-motion: reduce`): transitions become instant, arrivals only fade, and the shake, the ping and the shimmer stop. The typing dots only brighten. Information never depends on motion: the typing line still names who is typing, and a field's error is still written out.
