# Message

One message in a channel. It is a *head* (avatar, author, time) when it starts a group from one author, and a follow-up (`head={false}`) otherwise.

**Provide** `author`, `children` (the content), `time` ("Today at 18:31") and `shortTime` ("18:31") for follow-ups; `seed` (the account id) or `src` for the avatar; `color` for a role colour. Wrap messages in a `MessageList` (`role="log"`).

**States**
- `isNew` rises in 4px over `duration-base` on `ease-out`. Set it only for messages that arrive while the channel is open, never for history, so loading a channel stays still.
- `state="pending"` dims the content to `muted` until the server confirms; the change back to `text` fades in, confirming delivery quietly.
- `state="failed"` tints the row `danger-soft` and adds `error` (default "Not sent. Try again.") in `danger-text`.
- `mentioned` lays `gradient-mention` across the row: warm at the gutter, fading along the line.
- Hover shows a `panel-hover` fill, the follow-up's time in the gutter, and the `actions` toolbar, which rises into place on `floating` with `shadow-float`.

`SystemMessage` renders server notices (someone joined, a channel or the server renamed, the topic changed) as one `muted` line behind an accent arrow. Names in it go in `<strong>`.

- Do start a new head when the author changes or after a long pause.
- Don't use a role colour that falls under 4.5:1 on `panel-raised`; fall back to `text-strong`.
