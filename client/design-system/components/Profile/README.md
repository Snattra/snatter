# Profile

A member's profile, in a Modal. It opens from their row in the member list and from their name or avatar in a channel. Their Avatar (`lg`, with presence) leads the header, their display name is the title, and the description is their `@username`, followed by an `accent` Tag, "Owner", for the server owner.

**For everyone** A warning Callout when they are banned ("Banned": since when, and that they can't sign in) or timed out ("Timed out": until when, and what that means). Then, under `text-label` headings, when they joined ("Member since") and their roles as Tags, highest first, or "No roles".

**For moderators** Each action shows only to members who may take it, by the same rules the server applies, so none of them is refused:
- **Edit roles** (`MANAGE_ROLES`) opens a step with every role as a checkbox Choice. A role that grants permissions the moderator lacks is disabled and says so. The owner can change any role.
- **Time out** (`TIMEOUT_MEMBERS`) opens a step with a Select of lengths from 1 minute to 28 days. A timed-out member offers **End timeout** instead, which acts at once.
- **Ban** (`BAN_MEMBERS`) sits at the footer's start, as the destructive action, and opens a step that asks first, with an optional reason. A banned member offers **Lift ban** instead, which acts at once. Moderators also see the reason in the Callout.
- Timing out and banning are only offered for a member whose roles grant nothing the moderator lacks, and never for the owner or oneself.

**Steps** The actions are steps of the same Modal (`step`), titled with what they do ("Ban Robin", "Time out Robin", "Edit roles"). Cancel goes back to the profile. A step finishes once the change shows in the member list, and then returns to the profile, which shows it too.

- Do show everyone's roles, timeouts and bans to every member, and keep the ban reason to moderators.
- Don't offer an action the server would refuse.
