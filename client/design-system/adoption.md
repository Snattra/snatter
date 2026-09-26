# In the web client

The web client (`client/web` in the Snatter repository) is built on this system. Its pieces map onto this one as follows.

| Here | In `client/web/src` |
|---|---|
| `tokens.json` | `tokens.css`: every token as a custom property on `:root`. The gradients are built there from the colour tokens with `color-mix()`, so they follow the colours. |
| `components/bundle.css` | `styles.css`: the same `sn-` classes, for the components the app uses so far. |
| Button, IconButton, Tabs, Field | `ui/controls.tsx`, with `Spinner` |
| Card, Callout, Banner, Tooltip, Popover, Skeleton | `ui/surfaces.tsx`, with `Backdrop` |
| Avatar, MemberList, UserPanel, TypingIndicator | `ui/people.tsx`; the member list's typing mark is `TypingDots` |
| AppShell, ServerRail, Sidebar, ChannelHeader | `ui/layout.tsx`, with `RailServer`, `ChannelList` and `ChannelItem` |
| Message, SystemMessage, Composer, NewMessages | `ui/messages.tsx`: `Message` with `UserMessageRow` and `PendingRow`, `SystemMessageRow`, `NewMessagesDivider`, `UnreadBar`; `ui/ChannelView.tsx` puts them together |
| Invite | `ui/invites.tsx`: `InviteButton` holds the Popover and `InviteLink` |
| Icon | `ui/icons.tsx`, with `ChannelIcon` |

The app's components take the app's own data (an `Account`, a `Channel`, the connection's origin) instead of the display props in `components/index.d.ts`. They render the same markup and classes, so the app and these previews look and move the same.

Not in the app yet, because the features behind them are not built: mentions and the mention highlight, the message actions toolbar, role colours on names, the Composer's attach button, the add-server action and the server rail's unread pill, Field hints and errors, and the warning Callout and the danger and accent Banners. Their styles are in `components/bundle.css`. Bring each block into `styles.css` together with the component that uses it.

Rules for keeping the two in step:
- A token changes in `tokens.json` and `client/web/src/tokens.css` in the same commit.
- A new component is designed here first, with a README and a preview, and then built in `client/web/src/ui` on the same classes.
- The app's Content-Security-Policy allows no inline styles or scripts from markup. Components set only style properties through React (the avatar fill, the tab position), which the policy allows.
