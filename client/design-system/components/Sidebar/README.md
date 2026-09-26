# Sidebar

The channel pane: the community name in a 48px header, the channel list, and the signed-in user at the foot.

**Provide** `name`, `children` (a `ChannelList` of `ChannelItem`s, each with `name`, `type` of `text`, `voice` or `voice_text`, `selected`, `unread`, `onClick`), `footer` (a UserPanel), and `collapsed` with `onToggle` for the chevron.

**States of a channel row** At rest it is `muted`. On hover it takes a `panel-raised` fill and `text` ink. Selected, it is `panel-active` with `text-strong` and an accent icon, echoing the selected server. Unread shows `text-strong` at 600, with a small white pill at the pane's edge. Colour changes run for `duration-instant`.

**Collapsing** The icons sit where they would in the 56px column, so collapsing never moves them. The names fade (`duration-fast`) while the column narrows (`duration-slow`), and each row's title becomes its tooltip.

- Do sort channels by position, as the server does.
- Don't centre the icons with flexbox in the collapsed state: that makes them jump.
