Snatter is a self-hosted voice and text server for gamers and hobby communities. Its interface is a harbour at night: four deep navy panes (servers, channels, the channel, the people in it), an azure sheen on whatever you've chosen, and motion that only happens when something changes.

## Principles

- **The conversation is the brightest thing.** Surfaces step from dark to less dark toward the content: `bg`, then `panel`, then `panel-raised`. Chrome stays `muted` until you reach for it.
- **The sheen means "this one".** The accent turning into `accent-sheen` (`gradient-accent`), like harbour lights on water, marks the current server, the chosen tab, the primary action and the field you're typing in, and nothing else. Status colours are marks, not decoration.
- **Motion explains a change.** Something arrives, moves to where you chose, or confirms that it worked. Nothing loops except to say someone is typing or something is loading.
- **Calm by default.** Loading history, opening a channel and first renders are still. Only live events animate.

## Content

- **Voice.** Plain, short and friendly, in the second person: "Set up your server", "The account you create now becomes its owner, with full control over the server and its settings." The app says what is happening and what to do next.
- **Casing.** Sentence case everywhere, buttons included: "Sign in", "Create account", "Collapse channels". Only member-list headings are uppercase, through `text-eyebrow` ("ONLINE — 3"), never typed in capitals.
- **Progress.** Present participle and a real ellipsis (…), not three dots: "Signing in…", "Creating your account…", "Checking you are not a bot…", "Reconnecting…".
- **Counts.** Use an em dash with spaces: "Online — 3".
- **Errors.** Say what to do: "Usernames use letters, digits and _.". Don't write "Invalid input".
- **No emoji, no exclamation marks** in interface copy. People bring their own to their messages.
- **Channel names** are shown as typed, lowercase by convention ("general", "patch-notes"), after their type icon, never with a typed `#`.

## Colour

- **The pane ladder.** Every neutral is the same deep navy, only lighter or darker, so the app reads as one body of water at night rather than grey chrome. The server rail, the user panel and text-field wells are `bg`. The channel sidebar, the member list and cards are `panel`. The channel itself is `panel-raised`. Seams between panes are 1px of `divider`.
- **Rows.** A row on `panel` hovers to `panel-raised` and is selected at `panel-active`. A message on `panel-raised` hovers to `panel-hover`, one step darker, so it reads as picked out rather than chosen.
- **Ink.** `text` for content, `text-strong` for names, headings and anything selected, `muted` for everything secondary. `muted` reads on every surface and on the accent tint (4.8:1 or more).
- **Accent.** `accent` is Snatter azure, the brand colour. As a fill it carries dark `on-accent` labels, never white. It also reads as ink on every surface (6.4:1 or more), so `accent-text` (links, mentions) and `focus` are aliases of it. The current channel's icon takes the accent, echoing the selected server. `accent-sheen` is the cyan the azure turns into, and appears only inside `gradient-accent`. `accent-soft` tints a callout or a highlighted row.
- **Status.** `online` is only ever the presence dot, a green that never appears anywhere else, so presence never reads as a choice. `warning`, a lamp yellow, is for notices, the reconnecting banner (with `on-warning` text on it) and the mention highlight. `danger` marks an invalid field and destructive icons. Error copy is `danger-text`, and destructive buttons are `danger-strong` with `on-danger` labels. The `-soft` tints sit behind rows and fields in that state.
- **Role colours.** People's names may take their role's colour when it holds 4.5:1 on `panel-raised`. Otherwise use `text-strong`.
- **Focus.** Every focusable control shows a 2px solid `focus` outline in the accent, 2px out, 6.4:1 or more on every surface. Text fields and the composer show focus with a sheen edge (`gradient-accent` through the border) and `shadow-glow` instead.

## Gradients

The sheen is the signature, and it only ever covers what is chosen or primary. Every other gradient is a glow or a fade. None of them are decoration.
- `gradient-accent`, the sheen, runs from `accent` to `accent-sheen` at 135°. It fills primary buttons, the selected tab, a hovered or selected server and the rail pill. It edges a focused field and the composer, and each typing dot shows its own slice of it. Paint it at 150% width: on hover it slides toward `accent-sheen`, which is the button answering you.
- `gradient-edge` is a 1px sheen along the top of a card on the ground, such as the sign-in card. It marks the one surface a screen is about.
- `gradient-backdrop` and `gradient-backdrop-far`, layered over `bg`, cast an `accent` glow from the top left and an `accent-sheen` one from the bottom right behind the sign-in card. They are never used inside the app.
- `gradient-mention` warms a message that mentions you: strongest at the gutter where the eye scans, fading across the line.
- `gradient-shimmer` is the highlight that sweeps across a skeleton.
- Don't put the sheen on large surfaces, text or icons at rest, and don't add hues to it.

## Type

The platform's own UI face (`sans`: system-ui) at a 15px base, with `mono` for code. Hierarchy comes from weight and ink, not size. Most of the app is 15px.
- `text-title` (21/28, 700): one per screen, the community name on the sign-in card.
- `text-heading` (15/20, 600): pane headings, channel and community names, message authors.
- `text-body` (15/22): messages and inputs. Buttons and tabs use it at 500.
- `text-label` (13/16, 500): form labels, in `muted`.
- `text-small` (13/18): hints, errors, topics, callout bodies, tooltips.
- `text-eyebrow` (12/16, 700, uppercase): member-list group headings.
- `text-caption` (12/16): timestamps, the typing line, presence.
- `text-code` (13/18, mono): code in messages, on `bg`.

## Space, size and shape

- **The frame.** A `rail-width` (72px) server rail, a `sidebar-width` (240px) channel sidebar that collapses to `sidebar-collapsed` (56px), the channel, then a `members-width` (240px) member list. Every pane header is `header-height` (48px).
- **Spacing** tokens are named by their pixel value. Rows and controls use 4, 6, 8, 10, 12 and 16. Cards pad 24, and the sign-in screen keeps 32 of margin.
- **Radii.** `radius-sm` (4px) for controls and rows, `radius-md` (8px) for cards, callouts, the composer and anything floating, `radius-round` for people. A server icon is `radius-round` at rest and `radius-lg` (16px) when you reach for it.
- **Elevation** comes from the surface ladder first. Shadows are cast in deep navy, not pure black. `shadow-lift` is for a card on the ground or a modal over the `scrim`, which dims the app toward `floating`, and `shadow-float` for tooltips, popovers and toolbars. `shadow-accent` is the accent glow under a hovered primary button and the selected server: the sheen's light spilling onto the ground.

## States

| | rest | hover | selected / pressed | disabled |
|---|---|---|---|---|
| Channel row | `muted` | `panel-raised`, `text` | `panel-active`, `text-strong`, accent icon | — |
| Server icon | circle, `panel` | rounded square, sheen fill, pill 20px | rounded square, sheen fill + `shadow-accent`, pill 40px | — |
| Primary button | sheen, `on-accent` label | sheen slides toward `accent-sheen` + `shadow-accent` | scale 98% | `opacity-disabled` |
| Icon button | `muted` | `text` | `text-strong` (`aria-pressed`) | `opacity-disabled` |
| Text field | `bg` well | — | sheen edge + `shadow-glow` | `opacity-disabled` |
| Choice | `muted` ring | `panel-raised` row, `text` ring | sheen mark, `text-strong` label; a radio's row `panel-active` | `opacity-disabled` |
| Member | name `text` | `panel-raised` | — | offline: name `muted`, avatar `opacity-offline` |

## Iconography

Filled glyphs on a 24px grid, drawn inline at `icon-size` (18px) as SVG with `fill="currentColor"`, so they follow the ink of their control. Use `hash` for text channels, `speaker` for voice and voice-and-text channels, `members` for the member-list toggle, `person-add` for inviting people, `settings` for server and channel settings, and the chevrons to collapse and expand the sidebar. `plus`, `close`, `send`, `arrow-right`, `arrow-up`, `chevron-down` (a Select), `check` (a ticked Choice), `ban` (a banned member), `edit` (editing a message) and `delete` (deleting one, and what is left where one was deleted) complete the set, with the voice controls: `mic` and `mic-off` (muting, and a muted member, in `danger` when a moderator muted them), `headset` and `headset-off` (joining voice and deafening, and a deafened member), and `call-end` (leaving voice). There is no icon font and no emoji in the chrome. An icon is always decorative: label the button that holds it.

## Brand mark

Snatter has no logo yet. Set the name in plain `text-title` type. A server without a picture shows its initials, as people do.
