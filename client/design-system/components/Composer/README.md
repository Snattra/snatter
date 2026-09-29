# Composer

The message field at the foot of a channel: a `bg` well that grows with its text, an optional attach button, and a send button that springs in once there is something to send.

**Provide** `placeholder` ("Message #general"), `onSend(text)`, and optionally `onAttach` and `footer`, usually a `TypingIndicator` for the channel. It keeps its own text unless you pass `value` and `onChange`.

**Keys** Enter sends, Shift+Enter adds a line, and composing with an IME never sends.

**Motion** Focus draws the sheen edge and `shadow-glow` over `duration-fast`. The send button scales in on `ease-spring` as soon as the text is non-blank, so the composer shows when a message can go.

**Mentions** Typing `@` or `#` at the start of a word opens the suggestion list (`sn-suggestions`) just above the field: a `floating` panel with `shadow-float` and `radius-md`, an eyebrow title ("Members", "Channels"), then up to eight rows. A member row is a `sm` Avatar, the display name and the username in `muted`; a channel row is its type icon and name. The chosen row is `panel-active` with `text-strong`. The arrow keys move the choice, Enter or Tab puts it in the text as `@username` or `#Channel name` with a space after, and Escape closes the list until the next mention. The field keeps focus throughout, pointing at a row chooses it, and the list pops in (`sn-pop-in`, `duration-fast`). Sending turns the names into mention tokens.

- Do send the typing signal from `onChange`, throttled as the server expects.
- Don't disable the composer while a message is sending: show the message as `pending` instead.
