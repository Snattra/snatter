# ChannelHeader

The 48px bar over a channel: its type icon, its name in `text-strong`, the topic in `muted` after a hairline, and actions on the right.

**Provide** `name`, `type`, `topic` (optional, and truncated to one line), and `children` for actions: IconButtons, such as the member-list toggle with `pressed`.

- Do keep to one line. The topic truncates, the name never does.
- Don't put more than three actions here.
