# Field

A labelled control on a `bg` well, with optional hint and error text. `Field` holds a text input, `TextArea` a textarea for a description or a topic (three lines tall, and the member can drag it taller), and `Select` a native select for one of a list, its arrow drawn as `chevron-down`.

**Provide** `label`, the control's own props (`name`, `type`, `autoComplete`, `required`, `maxLength`, `value`…; a Select's options as `children`), `optional` to append "(optional)", `hint` for helper copy, and `error` once a check fails.

**Motion** Focus draws a sheen edge (`gradient-accent` showing through a transparent border) and the accent `shadow-glow`. An error switches to the `danger` border and `shadow-glow-danger`, shakes the field once (`duration-slow`), and rises the message in, in `danger-text`. Each new error shakes again, so a second failed attempt is felt. With reduced motion there is no shake.

- Do write errors as what to do: "Usernames use letters, digits and _.".
- Do use a Select for a list the member picks from, such as channels or roles, and Choices when there are two to four options worth describing.
- Don't show an error before the first submit.
