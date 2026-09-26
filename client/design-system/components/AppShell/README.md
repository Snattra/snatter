# AppShell

The four-column frame of a signed-in server: rail, channel sidebar, channel, member list.

**Provide** `rail` (a ServerRail), `sidebar` (a Sidebar), `header` (a ChannelHeader), `children` (a MessageList, which scrolls), `footer` (a Composer), `members` (a MemberList) and, while the gateway is down, `banner` (a Banner). Give it the viewport's height (`100vh` in the app).

**State** `collapsed` narrows the sidebar from `sidebar-width` to `sidebar-collapsed`; pass the same value to the Sidebar. `membersOpen={false}` slides the member list shut. Both animate the grid columns over `duration-slow` on `ease-standard`. The member list stays mounted and is `inert` while shut, so it keeps its scroll position.

- Do keep the column order and widths; they are the `size` tokens.
- Don't unmount the member list to hide it: the column then snaps instead of sliding.
