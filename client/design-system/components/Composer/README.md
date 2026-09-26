# Composer

The message field at the foot of a channel: a `bg` well that grows with its text, an optional attach button, and a send button that springs in once there is something to send.

**Provide** `placeholder` ("Message #general"), `onSend(text)`, and optionally `onAttach` and `footer`, usually a `TypingIndicator` for the channel. It keeps its own text unless you pass `value` and `onChange`.

**Keys** Enter sends, Shift+Enter adds a line, and composing with an IME never sends.

**Motion** Focus draws the sheen edge and `shadow-glow` over `duration-fast`. The send button scales in on `ease-spring` as soon as the text is non-blank, so the composer shows when a message can go.

- Do send the typing signal from `onChange`, throttled as the server expects.
- Don't disable the composer while a message is sending: show the message as `pending` instead.
