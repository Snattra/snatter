# ChannelHeader

The 48px bar over a channel: its type icon, its name in `text-strong`, the topic in `muted` after a hairline, and actions on the right.

**Provide** `name`, `type`, `topic` (optional, and truncated to one line), and `children` for actions: IconButtons, such as `settings` for Channel settings (only for members who may change the channel) and then the member-list toggle with `pressed`. A voice and text channel leads them with a small Button, `headset` and "Join voice", or `call-end` and "Leave voice" while the member is in it; a voice channel has its way in in its body instead.

- Do keep to one line. The topic truncates, the name never does.
- Don't put more than three actions here.
