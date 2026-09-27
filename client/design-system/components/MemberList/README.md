# MemberList

The member pane: members grouped under uppercase `text-eyebrow` headings ("Online — 3"), each a row with avatar, name and, while they type, compact typing dots.

**Provide** `groups`: `{ title, online, banned?, members: [{ id, name, src?, color?, typing?, bannedText? }] }`, sorted by name as the app does, and `onOpen`, called with a member's id when their row is clicked. An empty group is skipped. `footer` is pinned under the list, above a 1px `divider`, and stays put while the list scrolls: the InviteButton goes there.

**States** Offline members have a `muted` name, an avatar at `opacity-offline` and a grey dot. A member coming online moves groups with a short fade, and their dot pings twice. Rows take a `panel-raised` fill on hover.

**Opening a profile** Each row is a button that opens the member's Profile.

**Banned members** stay members, in their own group at the end ("Banned — 1"). Their avatar is at `opacity-offline` with no presence dot, their name `muted`, and a `ban` mark in `danger` ends the row. Its Tooltip, on the left, says when ("Banned on 27 September"). Lifting the ban moves them back.

- Do keep offline names `muted`. Don't dim the whole row: at 0.45 the name falls to 3.5:1.
- Don't show a role colour on an offline member.
