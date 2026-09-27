# Choice

A radio button or checkbox as a row: a mark, an optional icon, a label, and a line of description. The native input stays, out of sight but focusable, so keyboards and screen readers treat it as the real thing: the arrow keys move between the radios of a set, and Space ticks a checkbox.

**Provide** `type` (`radio` for one of a set, which share a `name`; `checkbox` for a setting that is on or off), `label`, optionally `description` and `icon`, and the input's own props (`checked`, `onChange`, `name`, `value`, `disabled`). Gather related Choices in a `ChoiceGroup` under a `legend`; it takes a `hint` or an `error` like a Field.

**States** At rest the mark is a 2px `muted` ring, a circle for a radio and a `radius-sm` square for a checkbox. On hover the row fills `panel-raised` and the ring turns `text`. Chosen, the mark fills with the sheen and its `on-accent` dot or `check` springs in (`ease-spring`, `duration-base`), and the label turns `text-strong`. A chosen radio's row is `panel-active`, as a selected row is, and its icon takes the accent. Focus rings the whole row. In a group with an error, the empty marks are edged in `danger`. A disabled row is at `opacity-disabled`.

- Do use radios for two to four options worth comparing, each with a description: a channel's type, who can create an account.
- Do use a checkbox for one setting, and let its description say what turning it on does.
- Don't use Choices to switch between views; that is Tabs.
