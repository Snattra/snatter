import { useEffect, useRef } from "react";
import type { Account, Channel, VoiceState } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import { isMuted } from "../state/moderation";
import { type ServerView, can, voiceIn } from "../state/serverView";
import { type LocalVoice, type VoiceScene, micOff, quietScene, voiceSound, voiceStatus } from "../state/voice";
import { classes } from "./classes";
import { Button, IconButton } from "./controls";
import { Icon, type IconName } from "./icons";
import { useCollapsed } from "./layout";
import { Avatar } from "./people";
import { playVoiceSound, unlockSounds } from "./sounds";
import { Tooltip } from "./surfaces";

/** Joins voice from a click, which is also what lets the join and leave sounds play. */
function join(connection: ServerConnection, channelId: string): void {
  unlockSounds();
  connection.joinVoice(channelId);
}

/** After a member in voice: muted by a moderator (in `danger`) or by themselves, and deafened. */
function VoiceMarks({ state, member }: { state: VoiceState; member: Account }) {
  return (
    <span className="sn-voice-marks">
      {isMuted(member) ? (
        <VoiceMark icon="mic-off" label="Muted by a moderator" moderated />
      ) : (
        state.selfMuted && !state.selfDeafened && <VoiceMark icon="mic-off" label="Muted" />
      )}
      {state.selfDeafened && <VoiceMark icon="headset-off" label="Deafened" />}
    </span>
  );
}

function VoiceMark({ icon, label, moderated = false }: { icon: IconName; label: string; moderated?: boolean }) {
  return (
    <Tooltip label={label} side="left">
      <span className={classes("sn-voice-mark", moderated && "sn-voice-mark-moderated")} role="img" aria-label={label}>
        <Icon name={icon} />
      </span>
    </Tooltip>
  );
}

/** Those in voice in a channel, with the member, in the order they joined. */
function present(view: ServerView, channelId: string): { state: VoiceState; member: Account }[] {
  return voiceIn(view, channelId).flatMap((state) => {
    const member = view.members[state.accountId];
    return member === undefined ? [] : [{ state, member }];
  });
}

interface VoiceMembersProps {
  origin: string;
  view: ServerView;
  channel: Channel;
  onOpen: (accountId: string) => void;
}

/**
 * Those in a voice channel, under its row in the sidebar. Their avatars sit
 * centred where the channel icons do, so collapsing never moves them; each
 * row opens the member's profile.
 */
export function VoiceMembers({ origin, view, channel, onOpen }: VoiceMembersProps) {
  const collapsed = useCollapsed();
  const inVoice = present(view, channel.id);
  if (inVoice.length === 0) {
    return null;
  }
  return (
    <ul className="sn-voice-members" aria-label={`In voice in ${channel.name}`}>
      {inVoice.map(({ state, member }) => (
        <li key={member.id}>
          <button
            type="button"
            className="sn-voice-member"
            aria-haspopup="dialog"
            title={collapsed ? member.displayName : undefined}
            onClick={() => onOpen(member.id)}
          >
            <Avatar origin={origin} account={member} size="sm" />
            <span className="sn-voice-member-name sn-truncate sn-collapse-fade">{member.displayName}</span>
            <span className="sn-collapse-fade">
              <VoiceMarks state={state} member={member} />
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

interface VoicePanelProps {
  connection: ServerConnection;
  view: ServerView;
  local: LocalVoice;
  /** The view is stale until the gateway is back, and the server has taken the member out of voice meanwhile. */
  reconnecting: boolean;
}

/**
 * Above the user panel while the member is in voice here: where, with
 * their microphone and sound and the way out. When the server refused to
 * let them join or move, or took them out, it says why until dismissed.
 * While reconnecting it says so; the app joins again once it is back.
 */
export function VoicePanel({ connection, view, local, reconnecting }: VoicePanelProps) {
  const collapsed = useCollapsed();
  const status = reconnecting && local.channelId !== null ? "joining" : voiceStatus(local, view.voice[view.account.id]);
  if (status === "out") {
    if (local.notice === null) {
      return null;
    }
    return (
      <section className="sn-voice-panel" aria-label="Voice">
        <div className="sn-voice-panel-row">
          <span className="sn-voice-panel-icon" title={collapsed ? local.notice : undefined}>
            <Icon name="headset-off" />
          </span>
          <span className="sn-voice-panel-notice sn-collapse-fade" role="status">
            {local.notice}
          </span>
          <span className="sn-collapse-fade">
            <IconButton icon="close" label="Dismiss" onClick={() => connection.dismissVoiceNotice()} />
          </span>
        </div>
      </section>
    );
  }
  const channel = local.channelId === null ? undefined : view.channels[local.channelId];
  const moderated = isMuted(view.account);
  const statusText = reconnecting
    ? "Reconnecting…"
    : status === "joining"
      ? "Joining voice…"
      : moderated
        ? "Muted by a moderator"
        : "Voice connected";
  const muted = micOff(local);
  return (
    <section
      className={classes(
        "sn-voice-panel",
        status === "connected" && "sn-voice-panel-connected",
        moderated && "sn-voice-panel-moderated",
      )}
      aria-label="Voice"
    >
      <div className="sn-voice-panel-row">
        <span className="sn-voice-panel-icon" title={collapsed ? `${statusText} ${channel?.name ?? ""}` : undefined}>
          <Icon name="headset" />
        </span>
        <span className="sn-voice-panel-text sn-collapse-fade">
          <span className="sn-voice-panel-status" role="status">
            {statusText}
          </span>
          <span className="sn-voice-panel-channel sn-truncate">{channel?.name}</span>
        </span>
        <span className="sn-collapse-fade">
          <IconButton icon="call-end" label="Leave voice" className="sn-voice-leave" onClick={() => connection.leaveVoice()} />
        </span>
      </div>
      {local.notice !== null && (
        <div className="sn-voice-panel-row sn-collapse-fade">
          <span className="sn-voice-panel-notice" role="status">
            {local.notice}
          </span>
          <IconButton icon="close" label="Dismiss" onClick={() => connection.dismissVoiceNotice()} />
        </div>
      )}
      <div className="sn-voice-panel-controls sn-collapse-fade">
        <VoiceToggle
          icon={muted ? "mic-off" : "mic"}
          label="Mute"
          pressed={muted}
          onClick={() => connection.setSelfMuted(!muted)}
        />
        <VoiceToggle
          icon={local.selfDeafened ? "headset-off" : "headset"}
          label="Deafen"
          pressed={local.selfDeafened}
          onClick={() => connection.setSelfDeafened(!local.selfDeafened)}
        />
      </div>
    </section>
  );
}

/** A labelled toggle in the voice panel, tinted `danger` while it is on, since on means not heard or not hearing. */
function VoiceToggle(props: { icon: IconName; label: string; pressed: boolean; onClick: () => void }) {
  const { icon, label, pressed, onClick } = props;
  return (
    <button type="button" className="sn-voice-toggle" aria-pressed={pressed} onClick={onClick}>
      <Icon name={icon} />
      {label}
    </button>
  );
}

/** In the channel header of a channel with voice: join it, or leave it. */
export function VoiceHeaderButton({ connection, channel, local }: { connection: ServerConnection; channel: Channel; local: LocalVoice }) {
  return local.channelId === channel.id ? (
    <IconButton icon="call-end" label="Leave voice" onClick={() => connection.leaveVoice()} />
  ) : (
    <IconButton icon="headset" label="Join voice" onClick={() => join(connection, channel.id)} />
  );
}

interface VoiceRoomProps {
  connection: ServerConnection;
  view: ServerView;
  channel: Channel;
  local: LocalVoice;
  onOpenProfile: (accountId: string) => void;
}

/** The body of a voice channel: who is in it, and the way in or out. */
export function VoiceRoom({ connection, view, channel, local, onOpenProfile }: VoiceRoomProps) {
  const inVoice = present(view, channel.id);
  const here = local.channelId === channel.id;
  const joining = here && voiceStatus(local, view.voice[view.account.id]) === "joining";
  const limit = channel.userLimit ?? 0;
  const others = inVoice.filter(({ member }) => member.id !== view.account.id).length;
  const full = limit > 0 && others >= limit && !can(view, "MOVE_MEMBERS");
  const allowed = can(view, "CONNECT");
  return (
    <div className="sn-voice-room">
      {inVoice.length === 0 ? (
        <p className="sn-voice-room-empty">No one is in voice here.</p>
      ) : (
        <ul className="sn-voice-room-members" aria-label="In voice">
          {inVoice.map(({ state, member }) => (
            <li key={member.id}>
              <button
                type="button"
                className="sn-voice-room-member"
                aria-haspopup="dialog"
                onClick={() => onOpenProfile(member.id)}
              >
                <Avatar origin={connection.origin} account={member} size="lg" />
                <span className="sn-voice-room-name">
                  <span className="sn-truncate">{member.displayName}</span>
                  <VoiceMarks state={state} member={member} />
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {limit > 0 && (
        <p className="sn-voice-room-count">
          {inVoice.length} of {limit}
        </p>
      )}
      {here ? (
        <Button busy={joining} onClick={() => connection.leaveVoice()}>
          {joining ? "Joining…" : "Leave voice"}
        </Button>
      ) : (
        <Button variant="primary" disabled={!allowed || full} onClick={() => join(connection, channel.id)}>
          Join voice
        </Button>
      )}
      {!here && (!allowed || full) && (
        <p className="sn-voice-room-note">{allowed ? "It's full." : "You don't have permission to join voice."}</p>
      )}
      <p className="sn-voice-room-note">
        Sound isn't built yet. Joining shows the others you're here, but nobody can hear anybody.
      </p>
    </div>
  );
}

/**
 * Plays the join and leave sounds for the voice channel the member is in
 * here, unless they are deafened. While joining or reconnecting it keeps the
 * last scene, so coming back to the same people makes no sound.
 */
export function useVoiceSounds(view: ServerView | null, local: LocalVoice): void {
  const scene = useRef<VoiceScene>(quietScene);
  const me = view?.account.id;
  const status = view === null || me === undefined ? (local.channelId === null ? "out" : "joining") : voiceStatus(local, view.voice[me]);
  // A string, so the effect runs when the people change rather than on every new view.
  const others =
    view === null || local.channelId === null
      ? ""
      : voiceIn(view, local.channelId)
          .map((state) => state.accountId)
          .filter((id) => id !== me)
          .join(" ");
  const channelId = local.channelId;
  const deafened = local.selfDeafened;
  useEffect(() => {
    if (status === "joining") {
      return;
    }
    const next = status === "connected" ? { channelId, others: others === "" ? [] : others.split(" ") } : quietScene;
    const sound = voiceSound(scene.current, next);
    scene.current = next;
    if (sound !== null && !deafened) {
      playVoiceSound(sound);
    }
  }, [status, channelId, others, deafened]);
}
