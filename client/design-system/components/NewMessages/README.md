# NewMessages

Where reading picks up again. `NewMessagesDivider` is a 1px `accent` line across the message list, ending in "New" in `accent-text`, placed above the first message the member has not read. `UnreadBar` floats over the top of the channel while that divider is scrolled out of view above, on `floating` with `shadow-float`, and takes the member back to it.

**Provide** For the bar, `count` when every unread message is loaded (otherwise it says "New messages"), `since` as the time of the last message read, `onJump`, and optionally `onMarkRead`. The divider takes an optional `label`.

**Behaviour**
- The divider stays where it was while the channel is open, even as the member reads past it, so they can find their place again, and moves only when they leave the channel and come back. With no divider showing, one appears above whatever arrives while they look elsewhere (another window, or scrolled up).
- The member's own messages never sit below the divider.
- A message right under the divider always starts a new group, with its avatar and name.
- The bar shows while the divider is above the viewport and has not been on screen yet; once the member has seen it, the bar has done its job. "Jump to first unread" scrolls the divider into view, loading older messages first if it is not loaded yet. "Mark as read" clears the divider and the bar.

**Motion** The bar drops 4px in and fades over `duration-base` on `ease-out` (`sn-drop-in`). The divider does not animate: it is there when the channel opens, and opening a channel is still.

- Do put the bar inside a positioned wrapper around the scrolling body, so it stays put while messages scroll.
- Don't use the sheen on either: unread is a place in the conversation, not a choice.
