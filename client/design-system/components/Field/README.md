# Field

A labelled text input on a `bg` well, with optional hint and error text.

**Provide** `label`, the input's own props (`name`, `type`, `autoComplete`, `required`, `pattern`…), `optional` to append "(optional)", `hint` for helper copy, and `error` once a check fails.

**Motion** Focus draws a sheen edge (`gradient-accent` showing through a transparent border) and the accent `shadow-glow`. An error switches to the `danger` border and `shadow-glow-danger`, shakes the field once (`duration-slow`), and rises the message in, in `danger-text`. Each new error shakes again, so a second failed attempt is felt. With reduced motion there is no shake.

- Do write errors as what to do: "Usernames use letters, digits, _ and .".
- Don't show an error before the first submit.
