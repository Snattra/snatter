# ServerRail

The 72px column of servers at the far left, on `bg`.

**Provide** one `RailServer` per server (`name`, and `src` for its icon, else initials), a `RailDivider`, then actions such as `<RailServer variant="action" icon="plus" name="Add a server" />`.

**Motion, and what it means**
- A server is a circle (`radius-round`) at rest. Hovered or selected, it settles into a rounded square (`radius-lg`) while the sheen fades in behind dark initials (`duration-base`, `ease-standard`). The selected one also casts the `shadow-accent` glow.
- The sheen pill at the rail's edge shows the server's state: 8px unread, 20px on hover, 40px selected. It grows on `ease-spring`.
- An action such as Add a server shows an accent icon and fills with the sheen on hover.
- The server name shows in a tooltip to the right.

- Do keep selection and unread state on the server itself; the pill reflects it.
- Don't add counts or colours to the pill. It only changes height.
