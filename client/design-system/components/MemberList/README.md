# MemberList

The member pane: members grouped under uppercase `text-eyebrow` headings ("Online — 3"), each a row with avatar, name and, while they type, compact typing dots.

**Provide** `groups`: `{ title, online, members: [{ id, name, src?, color?, typing? }] }`, sorted by name as the app does. An empty group is skipped. `footer` is pinned under the list, above a 1px `divider`, and stays put while the list scrolls: the InviteButton goes there.

**States** Offline members have a `muted` name, an avatar at `opacity-offline` and a grey dot. A member coming online moves groups with a short fade, and their dot pings twice. Rows take a `panel-raised` fill on hover.

- Do keep offline names `muted`. Don't dim the whole row: at 0.45 the name falls to 3.5:1.
- Don't show a role colour on an offline member.
