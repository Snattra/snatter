# Modal

A task over the app that needs a form, or that must not be lost on a stray click: creating a channel, changing settings, confirming a deletion. A `panel` card with the `gradient-edge` sheen along its top and `shadow-lift`, 480px wide (560px `wide`, for settings with Tabs), over the `scrim`, which dims the app toward `floating`. It is a native modal `<dialog>`, so the top layer, the focus trap and Escape come from the browser.

**Provide** `title` (sentence case, in `text-title`), optionally a one-line `description` (such as the channel it is about, after its icon), `onClose`, `children` for the body, and `footer` with Cancel and the one primary action. Give `onSubmit` to make the body and footer one form: Enter in a field sends it, and the primary button is `type="submit"`. `footerStart` holds a destructive action at the footer's other end, `tabs` holds Tabs under the title, `leading` holds something beside the title (a member's Avatar in a Profile), and `error` says what went wrong, in `danger-text` above the footer.

**Behaviour** It is open while it is mounted: render it to open it, stop rendering it to close it. Focus goes to the control marked `data-autofocus`, or else the first one, and back to what opened it on closing. Escape, the close button and Cancel close it; a click on the scrim does not, so a half-filled form is never lost to a stray click. When it is taller than the window only the body scrolls, and the header and footer stay put. A modal whose content changes, such as the question before a deletion, gives each content a `step`. When the step changes, focus moves to the new step's `data-autofocus` control, since the one that had focus is gone.

**Forms** The browser's own validation is off. Check the fields yourself on submit and hand each problem to its field as `error`, written as what to do; a problem on another tab switches to that tab. While the action runs, the primary button is `busy` with a present participle ("Creating…"). In a settings form, the primary button stays disabled until something has changed.

**Asking first** Before anything that cannot be undone, the same modal turns into the question: the title names the action ("Delete channel"), the body says what it means in a sentence or two, and the footer has Cancel and a `danger` button with the same verb. Cancel takes focus, unless the question has something to fill in, such as the reason for a ban.

**Motion** It rises in (`sn-rise-in`, `duration-slow`, `ease-out`) as a card enters, while the scrim fades in (`duration-fast`). It leaves at once.

- Do keep to one task per modal, titled with its verb and noun: "Create channel", "Server settings".
- Don't open a modal from a modal; change its content instead.
- Don't use it for a small task beside its control, such as copying an invite link: that is a Popover.
