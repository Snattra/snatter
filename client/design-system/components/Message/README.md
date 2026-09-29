# Message

One message in a channel. It is a *head* (avatar, author, time) when it starts a group from one author, and a follow-up (`head={false}`) otherwise.

**Provide** `author`, `children` (the content), `time` ("Today at 18:31") and `shortTime` ("18:31") for follow-ups; `seed` (the account id) or `src` for the avatar; `color` for a role colour; and `onAuthor` to open the author's Profile. Wrap messages in a `MessageList` (`role="log"`).

**The author** With `onAuthor`, the name is a button (`sn-name-button`) that underlines on hover, and the avatar opens the profile too. The avatar is for the mouse only: keyboards reach the profile through the name, so each message adds one stop, not two.

**States**
- `isNew` rises in 4px over `duration-base` on `ease-out`. Set it only for messages that arrive while the channel is open, never for history, so loading a channel stays still.
- `state="pending"` dims the content to `muted` until the server confirms; the change back to `text` fades in, confirming delivery quietly.
- `state="failed"` tints the row `danger-soft` and adds `error` (default "Not sent. Try again.") in `danger-text`.
- `mentioned` lays `gradient-mention` across the row: warm at the gutter, fading along the line.
- Hover shows a `panel-hover` fill, the follow-up's time in the gutter, and the `actions` toolbar, which rises into place on `floating` with `shadow-float`.

**Formatting** The content is the author's Markdown, rendered as elements, never as HTML. Their line breaks and blank lines stay (`pre-wrap`), and blocks sit `space-4` apart.
- **Bold** is weight 700 in the same ink, *italic* is `em`, underline and ~~strikethrough~~ are text decorations.
- Inline `code` is `text-code` on a `bg` fill with `radius-sm`. A code block (`sn-code-block`) is the same well padded `space-8` `space-12`; long lines scroll inside it instead of wrapping.
- A quote (`sn-quote`) has a 4px `panel-active` bar and `space-12` of indent. Lists indent `space-24` with `muted` markers.
- A spoiler (`sn-spoiler`) is a `bg` fill hiding its text, `floating` on hover. Clicking it or pressing Enter shows it: it fades in on `duration-fast` and keeps a `panel-active` fill, so it still reads as a spoiler. While hidden, its content is `inert`, so a link inside can't be clicked or reached unseen.
- There are no headings, images or tables, and no links with their own text.

**Mentions** A member mention is a pill (`sn-mention`): `@` and their display name as it is now, in `accent-text` at 500 on `accent-soft`, turning to an `accent` fill with `on-accent` ink on hover; it opens their Profile. A channel mention is the same pill with the channel's type icon and name, and switches to that channel. Someone no longer a member ("@Unknown member") and a channel the reader can't see ("Unknown channel", with `hash`) are `muted` on `panel-active` and do nothing, so a private channel's name never shows outside it. A message that mentions you is `mentioned`.

**Actions** The `actions` toolbar holds what the reader may do to the message: `edit` on their own, `delete` on their own or, with `MANAGE_MESSAGES`, on anyone's, notices included. The delete icon turns `danger` on hover. The toolbar shows on hover and on focus, so a keyboard reaches it too.

**Editing** Edit puts the Composer, in its `edit` mode, in place of the text: the same well, starting with the text and focus, with "Escape to cancel, Enter to save" under it. An edited message ends with "(edited)" in `muted` `text-caption`, its time in the tooltip.

**Deleting** Delete asks first in a Modal that shows the message on `panel-raised` (`sn-message-preview`) and says what it will read; Shift-click skips the question. A deleted member's message keeps its place, author and time, and reads "This message was deleted." or, when someone else deleted it, "Removed by a moderator.", in `muted` italics after the `delete` icon (`sn-message-deleted`). Deleted messages that follow each other, from one author and deleted the same way, share one line however far apart they were sent: "12 messages removed by a moderator.", "3 messages deleted.", at the time of the first. A deleted notice disappears.

**Links** A link always shows its whole address, in `accent-text`, underlined on hover. Opening one never happens straight away: a Modal, "Open this link?", names the host it really goes to and shows the whole address in a `bg` well (`sn-link-address`, mono). Cancel has focus, so opening is always its own choice.

`SystemMessage` renders server notices (someone joined, a channel or the server renamed, the topic changed) as one `muted` line behind an accent arrow. Names in it go in `<strong>`, or in an `sn-name-button` when they open a profile.

- Do start a new head when the author changes, after a long pause, and under the NewMessagesDivider.
- Don't use a role colour that falls under 4.5:1 on `panel-raised`; fall back to `text-strong`.
