import { type ReactNode, useEffect, useState } from "react";
import type { Account, Ban } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import { toggledRole } from "../state/channelForm";
import {
  PURGE_LENGTHS,
  TIMEOUT_LENGTHS,
  canAssign,
  isBanned,
  isMuted,
  isOwner,
  isTimedOut,
  moderationOf,
  roleChanges,
} from "../state/moderation";
import { type ServerView, can, sortedRoles } from "../state/serverView";
import { Button, Choice, ChoiceGroup, Field, Select } from "./controls";
import { describeError } from "./errors";
import { useNow } from "./hooks";
import { Avatar } from "./people";
import { Callout, Modal, Tag } from "./surfaces";
import { aheadTime, dateText, dayText } from "./time";

type Step = "profile" | "roles" | "timeout" | "ban" | "messages";

/** An hour, the timeout offered first. */
const DEFAULT_TIMEOUT_SECONDS = 60 * 60;

interface ProfileProps {
  connection: ServerConnection;
  view: ServerView;
  /** The member as the view has them now, so the profile follows every change. */
  member: Account;
  onClose: () => void;
}

/**
 * A member's profile: who they are, their roles, and whether they are timed
 * out or banned, for everyone. Moderators also get the actions they may take,
 * each a step of the same modal that finishes once the change shows.
 */
export function ProfileModal({ connection, view, member, onClose }: ProfileProps) {
  const [step, setStep] = useState<Step>("profile");
  // The step the profile was left for, so coming back focuses the button that led there.
  const [from, setFrom] = useState<Step | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [chosen, setChosen] = useState<string[]>([]);
  const [seconds, setSeconds] = useState(DEFAULT_TIMEOUT_SECONDS);
  const [reason, setReason] = useState("");
  const [purgeSeconds, setPurgeSeconds] = useState(PURGE_LENGTHS[0]?.seconds ?? 3600);
  // What the last purge did, said on the profile it returns to.
  const [purged, setPurged] = useState<number | null>(null);
  // Ticks while there is a timeout, so its end shows without reopening.
  const now = useNow(member.timedOutUntil != null ? 1_000 : null);
  const allowed = moderationOf(view, member, now);
  const banned = isBanned(member);
  const ban = useBan(connection, member, can(view, "BAN_MEMBERS"));
  const name = member.displayName;

  function go(next: Step) {
    setFailure(null);
    if (next !== "profile") {
      setPurged(null);
    }
    if (next === "profile") {
      setFrom(step);
    } else if (next === "roles") {
      setChosen([...member.roleIds].sort());
    } else if (next === "ban") {
      setReason("");
    }
    setStep(next);
  }

  /** Runs a moderation action with its button busy, then shows the profile, which now shows the change. */
  async function act(label: string, action: () => Promise<void>) {
    setBusy(label);
    setFailure(null);
    try {
      await action();
      if (step !== "profile") {
        go("profile");
      }
    } catch (e) {
      setFailure(describeError(e));
    } finally {
      setBusy(null);
    }
  }

  const cancel = <Button onClick={() => go("profile")}>Cancel</Button>;
  let content: { title: string; body: ReactNode; footer: ReactNode; footerStart?: ReactNode; onSubmit?: () => void };
  switch (step) {
    case "profile":
      content = {
        title: name,
        body: (
          <>
            {purged !== null && (
              <p className="sn-modal-text" role="status">
                {purged === 0
                  ? "They had no messages to delete from then."
                  : `Deleted ${purged} ${purged === 1 ? "message" : "messages"}. ${purged === 1 ? "It reads" : "They read"} “Removed by a moderator.”`}
              </p>
            )}
            <Details view={view} member={member} ban={ban} now={now} />
          </>
        ),
        footerStart: allowed.ban && (
          <Button variant="danger" data-autofocus={from === "ban" || undefined} onClick={() => go("ban")}>
            Ban
          </Button>
        ),
        footer: (
          <>
            {allowed.editRoles && (
              <Button data-autofocus={from === "roles" || undefined} onClick={() => go("roles")}>
                Edit roles
              </Button>
            )}
            {allowed.deleteMessages && (
              <Button data-autofocus={from === "messages" || undefined} onClick={() => go("messages")}>
                Delete messages
              </Button>
            )}
            {allowed.timeOut && (
              <Button data-autofocus={from === "timeout" || undefined} onClick={() => go("timeout")}>
                Time out
              </Button>
            )}
            {allowed.mute && (
              <Button busy={busy === "mute"} onClick={() => void act("mute", () => connection.mute(member.id))}>
                {busy === "mute" ? "Muting…" : "Mute"}
              </Button>
            )}
            {allowed.unmute && (
              <Button busy={busy === "unmute"} onClick={() => void act("unmute", () => connection.unmute(member.id))}>
                {busy === "unmute" ? "Unmuting…" : "Unmute"}
              </Button>
            )}
            {allowed.endTimeout && (
              <Button
                busy={busy === "endTimeout"}
                onClick={() => void act("endTimeout", () => connection.endTimeout(member.id))}
              >
                {busy === "endTimeout" ? "Ending timeout…" : "End timeout"}
              </Button>
            )}
            {allowed.liftBan && (
              <Button
                busy={busy === "liftBan"}
                onClick={() => void act("liftBan", () => connection.liftBan(member.id))}
              >
                {busy === "liftBan" ? "Lifting ban…" : "Lift ban"}
              </Button>
            )}
            {/* Where focus goes back to when the button that led away is gone, as after banning. */}
            <Button data-autofocus={from !== null || undefined} onClick={onClose}>
              Close
            </Button>
          </>
        ),
      };
      break;
    case "roles": {
      const { add, remove } = roleChanges(member, chosen);
      const roles = sortedRoles(view);
      const first = roles.find((role) => canAssign(view, role));
      content = {
        title: "Edit roles",
        onSubmit: () => void act("roles", () => connection.changeRoles(member.id, add, remove)),
        body: (
          <ChoiceGroup legend="Roles">
            {roles.map((role) => (
              <Choice
                key={role.id}
                type="checkbox"
                label={role.name}
                description={canAssign(view, role) ? undefined : "It grants permissions you don't have."}
                disabled={!canAssign(view, role)}
                checked={chosen.includes(role.id)}
                data-autofocus={role === first || undefined}
                onChange={() => setChosen(toggledRole(chosen, role.id))}
              />
            ))}
          </ChoiceGroup>
        ),
        footer: (
          <>
            {cancel}
            <Button
              variant="primary"
              type="submit"
              busy={busy === "roles"}
              disabled={add.length === 0 && remove.length === 0}
            >
              {busy === "roles" ? "Saving…" : "Save changes"}
            </Button>
          </>
        ),
      };
      break;
    }
    case "timeout":
      content = {
        title: `Time out ${name}`,
        onSubmit: () => void act("timeout", () => connection.timeOut(member.id, seconds)),
        body: (
          <>
            <p className="sn-modal-text">
              They keep their roles and can read, but can't write, invite or do anything else until it ends.
            </p>
            <Select
              label="How long"
              data-autofocus
              value={String(seconds)}
              onChange={(event) => setSeconds(Number(event.target.value))}
            >
              {TIMEOUT_LENGTHS.map((length) => (
                <option key={length.seconds} value={length.seconds}>
                  {length.label}
                </option>
              ))}
            </Select>
          </>
        ),
        footer: (
          <>
            {cancel}
            <Button variant="danger" type="submit" busy={busy === "timeout"}>
              {busy === "timeout" ? "Timing out…" : "Time out"}
            </Button>
          </>
        ),
      };
      break;
    case "messages":
      content = {
        title: `Delete messages from ${name}`,
        onSubmit: () =>
          void act("messages", async () => {
            setPurged(await connection.purge(member.id, new Date(Date.now() - purgeSeconds * 1000)));
          }),
        body: (
          <>
            <p className="sn-modal-text">
              Everything they sent in that time, in every channel you can see, will read “Removed by a moderator.”
              What it said is gone for good.
            </p>
            <Select
              label="From"
              data-autofocus
              value={String(purgeSeconds)}
              onChange={(event) => setPurgeSeconds(Number(event.target.value))}
            >
              {PURGE_LENGTHS.map((length) => (
                <option key={length.seconds} value={length.seconds}>
                  {length.label}
                </option>
              ))}
            </Select>
          </>
        ),
        footer: (
          <>
            {cancel}
            <Button variant="danger" type="submit" busy={busy === "messages"}>
              {busy === "messages" ? "Deleting…" : "Delete messages"}
            </Button>
          </>
        ),
      };
      break;
    case "ban":
      content = {
        title: `Ban ${name}`,
        onSubmit: () => void act("ban", () => connection.ban(member.id, reason)),
        body: (
          <>
            <p className="sn-modal-text">
              They are signed out everywhere and can't sign in again until the ban is lifted. Their messages stay.
            </p>
            <Field
              label="Reason"
              optional
              data-autofocus
              maxLength={512}
              autoComplete="off"
              hint="Shown to them when they try to sign in, and to members who can ban."
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </>
        ),
        footer: (
          <>
            {cancel}
            <Button variant="danger" type="submit" busy={busy === "ban"}>
              {busy === "ban" ? "Banning…" : "Ban"}
            </Button>
          </>
        ),
      };
      break;
  }

  return (
    <Modal
      title={content.title}
      description={
        <>
          @{member.username}
          {isOwner(view, member) && <Tag accent>Owner</Tag>}
        </>
      }
      leading={
        <Avatar
          origin={connection.origin}
          account={member}
          online={banned ? undefined : view.online[member.id] === true}
          size="lg"
        />
      }
      step={step}
      onClose={onClose}
      onSubmit={content.onSubmit}
      error={failure}
      footer={content.footer}
      footerStart={content.footerStart}
    >
      {content.body}
    </Modal>
  );
}

/** What everyone sees: a ban, timeout or mute, when they joined, and their roles. */
function Details({ view, member, ban, now }: { view: ServerView; member: Account; ban: Ban | null; now: number }) {
  const roles = sortedRoles(view).filter((role) => member.roleIds.includes(role.id));
  const today = new Date(now);
  const bannedBy = ban?.bannedBy == null ? undefined : view.members[ban.bannedBy];
  return (
    <>
      {member.bannedAt != null && (
        <Callout tone="warning" title="Banned">
          Since {dayText(new Date(member.bannedAt), today)}
          {bannedBy && `, by ${bannedBy.displayName}`}. They can't sign in until the ban is lifted.
          {ban?.reason && ` Reason: ${ban.reason}`}
        </Callout>
      )}
      {member.timedOutUntil != null && isTimedOut(member, now) && (
        <Callout tone="warning" title="Timed out">
          Until {aheadTime(new Date(member.timedOutUntil), today)}. They can read, but not write or do anything else.
        </Callout>
      )}
      {member.mutedAt != null && isMuted(member) && (
        <Callout tone="warning" title="Muted">
          Since {dayText(new Date(member.mutedAt), today)}. They can listen in voice and write, but not speak until
          they're unmuted.
        </Callout>
      )}
      <dl className="sn-profile-facts">
        <div>
          <dt>Member since</dt>
          <dd>{dateText(new Date(member.createdAt))}</dd>
        </div>
        <div>
          <dt>Roles</dt>
          <dd>
            {roles.length === 0 ? (
              <span className="sn-profile-none">No roles</span>
            ) : (
              <ul className="sn-tags">
                {roles.map((role) => (
                  <li key={role.id}>
                    <Tag color={role.color ?? null}>{role.name}</Tag>
                  </li>
                ))}
              </ul>
            )}
          </dd>
        </div>
      </dl>
    </>
  );
}

/**
 * The member's ban with its reason and who made it, which only members
 * holding `BAN_MEMBERS` may read; null otherwise, and while it loads.
 */
function useBan(connection: ServerConnection, member: Account, allowed: boolean): Ban | null {
  const [ban, setBan] = useState<Ban | null>(null);
  const bannedAt = member.bannedAt;
  const id = member.id;
  useEffect(() => {
    if (!allowed || bannedAt == null) {
      return;
    }
    let current = true;
    connection.bans().then(
      (bans) => {
        if (current) {
          setBan(bans.find((b) => b.accountId === id) ?? null);
        }
      },
      () => {
        // The profile shows the ban without its reason.
      },
    );
    return () => {
      current = false;
    };
  }, [connection, id, bannedAt, allowed]);
  // A ban loaded before the member was banned again, or unbanned, is not this one.
  return allowed && ban !== null && ban.createdAt === bannedAt ? ban : null;
}
