# Popover

A small task beside the control that opened it: a title in `text-heading`, an optional `muted` line saying what it is for, and the task itself. It sits on `floating` with `shadow-float` and `radius-md`, 16px in, at most 340px wide, and lives in the top layer, so no pane clips it.

**Provide** `title`, optionally `description`, `anchorRef` (the control it opens from), `side` (`top`, the default, or `bottom`), and `onToggle` to hear it open and close. The control opens it with `popovertarget` set to the popover's `id` and shows `aria-expanded` while it is open.

**Placement** Its right edge lines up with the control's, 8px above or below it. Where the window is too narrow it narrows, keeping 8px from the edge. Resizing the window closes it.

**Behaviour** Escape or a click outside closes it, and focus goes back to the control. Tab moves from the control into the popover. Only one is open at a time.

**Motion** It rises 4px out of its control and scales from 96% (`sn-pop-in`, `duration-fast`, `ease-out`), from the control's corner. It leaves at once.

- Do keep it to one task that takes a click or two, such as copying an invite link.
- Don't use it for anything that needs a form to fill in, or that must not be lost on a stray click.
- Don't put a Tooltip on the control while it has a visible label.
