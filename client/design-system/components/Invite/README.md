# Invite

How a member shares an invite link. `InviteButton` is a row pinned at the foot of the member list, under a 1px `divider`: the `person-add` icon, centred where the members' avatars sit, and "Invite people". It opens a Popover above it holding `InviteLink`: the link in a `text-code` well on `bg`, when it expires, and a primary Copy button.

**Provide** For the button, `popoverId`, `buttonRef` for the Popover's anchor, and `expanded` while it is open. For the link, `url` once there is one, `expiry` ("Expires in 7 days."), or `error` with `onRetry`.

**When it shows** Only while the server's registration is invite only and the member may create invites. On an open server anyone can join without one, so the row is not there.

**States of the button** At rest it is `muted`, like an offline name. On hover it takes a `panel-raised` fill and `text` ink, and while its popover is open it is `panel-active` with `text-strong`, as a selected row.

**States of the link**
- Creating: a spinner and "Creating a link…" in `muted`.
- Ready: the whole link in the well, wrapping rather than cut off, since the code is at the end. One click selects all of it. Copy turns to "Copied" for two seconds and becomes a secondary button, and screen readers hear "Link copied".
- Copying blocked by the browser: the link is selected instead, and the expiry line says so.
- Failed: what went wrong in `danger-text`, with "Try again".

- Do say when the link stops working; "This link never expires." when it doesn't.
- Don't put the invite on a channel: an invite is to the server, so it lives with the people in it.
