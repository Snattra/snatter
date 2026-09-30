import { useId, useState } from "react";
import type { Channel, ChannelType } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import {
  type ChannelForm,
  type ChannelFormErrors,
  blankChannelForm,
  channelChanges,
  channelForm,
  creation,
  creationErrors,
  hasVoice,
  kbps,
  MAX_BITRATE,
  rulesFor,
  toggledRole,
  updateErrors,
} from "../state/channelForm";
import { sameData } from "../state/forms";
import { type ServerView, sortedRoles } from "../state/serverView";
import { Button, Choice, ChoiceGroup, Field, TextArea } from "./controls";
import { describeError } from "./errors";
import { ChannelIcon, type IconName } from "./icons";
import { Modal } from "./surfaces";

const channelTypes: { type: ChannelType; icon: IconName; label: string; description: string }[] = [
  { type: "text", icon: "hash", label: "Text", description: "Messages, for everyone who can see it." },
  { type: "voice", icon: "speaker", label: "Voice", description: "Talk together, without messages." },
  {
    type: "voice_text",
    icon: "speaker",
    label: "Voice and text",
    description: "Talk together, with messages alongside.",
  },
];

interface CreateChannelProps {
  connection: ServerConnection;
  view: ServerView;
  onClose: () => void;
  /** Called once the channel is in the list, so it can be opened. */
  onCreated: (channel: Channel) => void;
}

/** "Create channel": its type, its name, and who can see it. Voice settings start at the server's defaults. */
export function CreateChannelModal({ connection, view, onClose, onCreated }: CreateChannelProps) {
  const typeGroup = useId();
  const [type, setType] = useState<ChannelType>("text");
  const [form, setForm] = useState(blankChannelForm);
  const [errors, setErrors] = useState<ChannelFormErrors>({});
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  function edit(changes: Partial<ChannelForm>, field: keyof ChannelFormErrors) {
    setForm((current) => ({ ...current, ...changes }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  async function create() {
    const found = creationErrors(form, rulesFor(view));
    setErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      onCreated(await connection.createChannel(creation(type, form)));
    } catch (e) {
      setFailure(describeError(e));
      setBusy(false);
    }
  }

  return (
    <Modal
      title="Create channel"
      onClose={onClose}
      onSubmit={() => void create()}
      error={failure}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" type="submit" busy={busy}>
            {busy ? "Creating…" : "Create channel"}
          </Button>
        </>
      }
    >
      <ChoiceGroup legend="Type">
        {channelTypes.map((option) => (
          <Choice
            key={option.type}
            type="radio"
            name={typeGroup}
            value={option.type}
            icon={option.icon}
            label={option.label}
            description={option.description}
            checked={type === option.type}
            onChange={() => setType(option.type)}
          />
        ))}
      </ChoiceGroup>
      <Field
        label="Name"
        data-autofocus
        required
        maxLength={100}
        placeholder="new-channel"
        autoComplete="off"
        value={form.name}
        error={errors.name}
        onChange={(event) => edit({ name: event.target.value }, "name")}
      />
      <Access view={view} form={form} error={errors.roles} onChange={(changes) => edit(changes, "roles")} />
    </Modal>
  );
}

interface ChannelSettingsProps {
  connection: ServerConnection;
  view: ServerView;
  /** The channel as it is now; the form starts from it. */
  channel: Channel;
  onClose: () => void;
}

/**
 * "Channel settings": name, topic, voice settings and who can see the
 * channel, and deleting it after asking first. Only what the member changed
 * is sent, so a change someone else makes meanwhile survives.
 */
export function ChannelSettingsModal({ connection, view, onClose, ...props }: ChannelSettingsProps) {
  const [channel] = useState(props.channel);
  const [form, setForm] = useState(() => channelForm(channel));
  const [errors, setErrors] = useState<ChannelFormErrors>({});
  const [busy, setBusy] = useState<"saving" | "deleting" | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  // After backing out of deleting, focus goes back to Delete channel rather than the name.
  const [backedOut, setBackedOut] = useState(false);
  const changed = !sameData(form, channelForm(channel));
  const maxKbps = kbps(MAX_BITRATE);
  const subject = (
    <>
      <ChannelIcon channel={props.channel} />
      {props.channel.name}
    </>
  );

  function edit(changes: Partial<ChannelForm>, field: keyof ChannelFormErrors) {
    setForm((current) => ({ ...current, ...changes }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  async function save() {
    const found = updateErrors(channel, form, rulesFor(view));
    setErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }
    const update = channelChanges(channel, form);
    if (Object.keys(update).length === 0) {
      onClose();
      return;
    }
    setBusy("saving");
    setFailure(null);
    try {
      await connection.updateChannel(channel.id, update);
      onClose();
    } catch (e) {
      setFailure(describeError(e));
      setBusy(null);
    }
  }

  async function remove() {
    setBusy("deleting");
    setFailure(null);
    try {
      await connection.deleteChannel(channel.id);
      onClose();
    } catch (e) {
      setFailure(describeError(e));
      setBusy(null);
    }
  }

  function confirm(asking: boolean) {
    setConfirming(asking);
    setBackedOut(!asking);
    setFailure(null);
  }

  if (confirming) {
    return (
      <Modal
        title="Delete channel"
        description={subject}
        step="delete"
        onClose={onClose}
        onSubmit={() => void remove()}
        error={failure}
        footer={
          <>
            <Button data-autofocus onClick={() => confirm(false)}>
              Cancel
            </Button>
            <Button variant="danger" type="submit" busy={busy === "deleting"}>
              {busy === "deleting" ? "Deleting…" : "Delete channel"}
            </Button>
          </>
        }
      >
        <p className="sn-modal-text">
          {channel.type === "voice"
            ? "The channel is deleted for everyone. This can't be undone."
            : "The channel and all of its messages are deleted for everyone. This can't be undone."}
        </p>
      </Modal>
    );
  }

  return (
    <Modal
      title="Channel settings"
      description={subject}
      step="settings"
      onClose={onClose}
      onSubmit={() => void save()}
      error={failure}
      footerStart={
        <Button variant="danger" data-autofocus={backedOut || undefined} onClick={() => confirm(true)}>
          Delete channel
        </Button>
      }
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" type="submit" busy={busy === "saving"} disabled={!changed}>
            {busy === "saving" ? "Saving…" : "Save changes"}
          </Button>
        </>
      }
    >
      <Field
        label="Name"
        data-autofocus={!backedOut || undefined}
        required
        maxLength={100}
        autoComplete="off"
        value={form.name}
        error={errors.name}
        onChange={(event) => edit({ name: event.target.value }, "name")}
      />
      <TextArea
        label="Topic"
        optional
        maxLength={1024}
        hint="Shown beside the name at the top of the channel."
        value={form.topic}
        onChange={(event) => setForm((current) => ({ ...current, topic: event.target.value }))}
      />
      {hasVoice(channel.type) && (
        <>
          <Field
            label="Bitrate (kbps)"
            type="number"
            inputMode="decimal"
            min={8}
            max={maxKbps}
            step="any"
            hint={`From 8 to ${maxKbps}. Higher sounds better and takes more bandwidth.`}
            value={form.bitrate}
            error={errors.bitrate}
            onChange={(event) => edit({ bitrate: event.target.value }, "bitrate")}
          />
          <Field
            label="User limit"
            type="number"
            inputMode="numeric"
            min={0}
            max={99}
            hint="How many can be in the channel at once, up to 99; 0 for no limit."
            value={form.userLimit}
            error={errors.userLimit}
            onChange={(event) => edit({ userLimit: event.target.value }, "userLimit")}
          />
        </>
      )}
      <Access view={view} form={form} error={errors.roles} onChange={(changes) => edit(changes, "roles")} />
    </Modal>
  );
}

interface AccessProps {
  view: ServerView;
  form: ChannelForm;
  error: string | undefined;
  onChange: (changes: Partial<ChannelForm>) => void;
}

/** Whether the channel is private, and then which roles see it; the member's own roles say so. */
function Access({ view, form, error, onChange }: AccessProps) {
  const roles = sortedRoles(view);
  const held = new Set(view.account.roleIds);
  return (
    <>
      <Choice
        type="checkbox"
        label="Private channel"
        description="Only members with the roles you choose, and the server owner, can see it."
        checked={form.private}
        onChange={(event) => onChange({ private: event.target.checked })}
      />
      {form.private && (
        <ChoiceGroup
          legend="Who can see it"
          error={error}
          hint={roles.length === 0 ? "This server has no roles yet." : undefined}
        >
          {roles.map((role) => (
            <Choice
              key={role.id}
              type="checkbox"
              label={role.name}
              description={held.has(role.id) ? "You have this role" : undefined}
              checked={form.roleIds.includes(role.id)}
              onChange={() => onChange({ roleIds: toggledRole(form.roleIds, role.id) })}
            />
          ))}
        </ChoiceGroup>
      )}
    </>
  );
}
