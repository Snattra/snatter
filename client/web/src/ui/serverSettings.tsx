import { useEffect, useId, useState } from "react";
import type { ServerSettings } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import { sameData } from "../state/forms";
import { type ServerView, sortedChannels, sortedRoles } from "../state/serverView";
import {
  type PolicyForm,
  RATE_LIMIT_POLICIES,
  type RateLimitPolicyName,
  type SettingsErrors,
  type SettingsField,
  type SettingsForm,
  settingsChanges,
  settingsErrors,
  settingsForm,
} from "../state/settingsForm";
import { classes } from "./classes";
import { Button, Choice, ChoiceGroup, Field, Select, Spinner, Tabs, TextArea } from "./controls";
import { describeError } from "./errors";
import { Modal } from "./surfaces";

type Section = "overview" | "joining" | "limits";

const sections: { id: Section; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "joining", label: "Joining" },
  { id: "limits", label: "Rate limits" },
];

/** Where each field that can be wrong lives, so a save can show it. */
const sectionOf: Record<SettingsField, Section> = {
  name: "overview",
  publicUrl: "joining",
  message: "limits",
  login: "limits",
  register: "limits",
  challenge: "limits",
  invite: "limits",
};

const policyLabels: Record<RateLimitPolicyName, string> = {
  message: "Messages sent by one member",
  login: "Sign-in attempts",
  register: "Accounts created",
  challenge: "Bot checks requested",
  invite: "Invite links looked up",
};

type Loaded =
  { status: "loading" } | { status: "failed"; error: string } | { status: "ready"; settings: ServerSettings };

/**
 * "Server settings", in three tabs: the community itself, how people join,
 * and rate limits. The settings are fetched as it opens, and only what the
 * member changed is saved.
 */
export function ServerSettingsModal(props: { connection: ServerConnection; view: ServerView; onClose: () => void }) {
  const { connection, view, onClose } = props;
  const [loaded, setLoaded] = useState<Loaded>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [form, setForm] = useState<SettingsForm | null>(null);
  const [section, setSection] = useState<Section>("overview");
  const [errors, setErrors] = useState<SettingsErrors>({});
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    let current = true;
    connection.serverSettings().then(
      (settings) => {
        if (current) {
          setLoaded({ status: "ready", settings });
          setForm(settingsForm(settings));
        }
      },
      (e: unknown) => {
        if (current) {
          setLoaded({ status: "failed", error: describeError(e) });
        }
      },
    );
    return () => {
      current = false;
    };
  }, [connection, attempt]);

  const settings = loaded.status === "ready" ? loaded.settings : null;
  const changed = settings !== null && form !== null && !sameData(form, settingsForm(settings));

  function edit(changes: Partial<SettingsForm>, field?: SettingsField) {
    setForm((current) => (current === null ? current : { ...current, ...changes }));
    if (field !== undefined) {
      setErrors((current) => ({ ...current, [field]: undefined }));
    }
  }

  async function save() {
    if (settings === null || form === null) {
      return;
    }
    const found = settingsErrors(form);
    setErrors(found);
    const wrong = Object.keys(found) as SettingsField[];
    const first = wrong[0];
    if (first !== undefined) {
      if (!wrong.some((field) => sectionOf[field] === section)) {
        setSection(sectionOf[first]);
      }
      return;
    }
    const update = settingsChanges(settings, form);
    if (Object.keys(update).length === 0) {
      onClose();
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      await connection.updateServerSettings(update);
      onClose();
    } catch (e) {
      setFailure(describeError(e));
      setBusy(false);
    }
  }

  return (
    <Modal
      title="Server settings"
      wide
      onClose={onClose}
      onSubmit={() => void save()}
      tabs={form !== null && <Tabs label="Server settings" tabs={sections} value={section} onChange={setSection} />}
      error={failure}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" type="submit" busy={busy} disabled={!changed}>
            {busy ? "Saving…" : "Save changes"}
          </Button>
        </>
      }
    >
      {loaded.status === "loading" && (
        <p className="sn-modal-status" role="status">
          <Spinner />
          Loading the settings…
        </p>
      )}
      {loaded.status === "failed" && (
        <div className="sn-modal-status">
          <span className="sn-modal-status-error" role="alert">
            {loaded.error}
          </span>
          <Button
            size="sm"
            onClick={() => {
              setLoaded({ status: "loading" });
              setAttempt(attempt + 1);
            }}
          >
            Try again
          </Button>
        </div>
      )}
      {form !== null && section === "overview" && <Overview view={view} form={form} errors={errors} edit={edit} />}
      {form !== null && section === "joining" && <Joining view={view} form={form} errors={errors} edit={edit} />}
      {form !== null && section === "limits" && <RateLimits form={form} errors={errors} edit={edit} />}
    </Modal>
  );
}

interface SectionProps {
  view: ServerView;
  form: SettingsForm;
  errors: SettingsErrors;
  edit: (changes: Partial<SettingsForm>, field?: SettingsField) => void;
}

function Overview({ view, form, errors, edit }: SectionProps) {
  // Notices need a channel with messages. The current one may be hidden from someone who is not the owner.
  const channels = sortedChannels(view).filter((channel) => channel.type !== "voice");
  const hidden = form.systemChannelId !== "" && !channels.some((channel) => channel.id === form.systemChannelId);
  return (
    <>
      <Field
        label="Server name"
        required
        maxLength={100}
        autoComplete="off"
        value={form.name}
        error={errors.name}
        onChange={(event) => edit({ name: event.target.value }, "name")}
      />
      <TextArea
        label="Description"
        optional
        maxLength={1000}
        hint="Shown to people on the sign-in screen."
        value={form.description}
        onChange={(event) => edit({ description: event.target.value })}
      />
      <Select
        label="Notices channel"
        hint="Where Snatter announces new members and changes to the server."
        value={form.systemChannelId}
        onChange={(event) => edit({ systemChannelId: event.target.value })}
      >
        <option value="">Don't post notices</option>
        {channels.map((channel) => (
          <option key={channel.id} value={channel.id}>
            {channel.name}
          </option>
        ))}
        {hidden && <option value={form.systemChannelId}>A channel you cannot see</option>}
      </Select>
    </>
  );
}

function Joining({ view, form, errors, edit }: SectionProps) {
  const modeGroup = useId();
  const roles = sortedRoles(view);
  return (
    <>
      <ChoiceGroup legend="Who can create an account">
        <Choice
          type="radio"
          name={modeGroup}
          label="Anyone"
          description="Anyone who can reach this server can sign up."
          checked={form.registrationMode === "open"}
          onChange={() => edit({ registrationMode: "open" })}
        />
        <Choice
          type="radio"
          name={modeGroup}
          label="People with an invite"
          description="Members share invite links, and only those let new people in."
          checked={form.registrationMode === "invite_only"}
          onChange={() => edit({ registrationMode: "invite_only" })}
        />
      </ChoiceGroup>
      <Choice
        type="checkbox"
        label="Check for bots at sign-up"
        description="The browser of someone signing up solves a small puzzle first. No outside service is involved."
        checked={form.challengeRequired}
        onChange={(event) => edit({ challengeRequired: event.target.checked })}
      />
      <Select
        label="Role for new members"
        hint="Every new account gets it. Without a role, new members can read but not write."
        value={form.newMemberRoleId}
        onChange={(event) => edit({ newMemberRoleId: event.target.value })}
      >
        <option value="">No role</option>
        {roles.map((role) => (
          <option key={role.id} value={role.id}>
            {role.name}
          </option>
        ))}
      </Select>
      <Field
        label="Server address"
        optional
        type="url"
        placeholder="https://chat.example.com"
        autoComplete="off"
        hint="Invite links start with it. Leave it empty to use the address the app is opened at."
        value={form.publicUrl}
        error={errors.publicUrl}
        onChange={(event) => edit({ publicUrl: event.target.value }, "publicUrl")}
      />
    </>
  );
}

function RateLimits({ form, errors, edit }: Omit<SectionProps, "view">) {
  return (
    <>
      <Choice
        type="checkbox"
        label="Limit repeated attempts"
        description="Slows down anyone flooding a channel, guessing passwords or signing up in bulk. Messages are counted per member, everything else per IP address."
        checked={form.rateLimitsEnabled}
        onChange={(event) => edit({ rateLimitsEnabled: event.target.checked })}
      />
      {RATE_LIMIT_POLICIES.map((name) => (
        <RatePolicy
          key={name}
          label={policyLabels[name]}
          policy={form.rateLimits[name]}
          error={errors[name]}
          disabled={!form.rateLimitsEnabled}
          onChange={(policy) => edit({ rateLimits: { ...form.rateLimits, [name]: policy } }, name)}
        />
      ))}
    </>
  );
}

interface RatePolicyProps {
  label: string;
  policy: PolicyForm;
  error: string | undefined;
  disabled: boolean;
  onChange: (policy: PolicyForm) => void;
}

/** One policy as a sentence: "[10] every [60] seconds". */
function RatePolicy({ label, policy, error, disabled, onChange }: RatePolicyProps) {
  const id = useId();
  return (
    <div
      className={classes("sn-field", error && "sn-field-invalid")}
      role="group"
      aria-labelledby={`${id}-label`}
      aria-describedby={error ? `${id}-error` : undefined}
    >
      <span id={`${id}-label`}>{label}</span>
      <span className="sn-rate">
        <input
          className="sn-input"
          type="number"
          inputMode="numeric"
          min={1}
          max={100_000}
          aria-label="Tries"
          disabled={disabled}
          value={policy.limit}
          onChange={(event) => onChange({ ...policy, limit: event.target.value })}
        />
        every
        <input
          className="sn-input"
          type="number"
          inputMode="numeric"
          min={1}
          max={86_400}
          aria-label="Seconds"
          disabled={disabled}
          value={policy.periodSeconds}
          onChange={(event) => onChange({ ...policy, periodSeconds: event.target.value })}
        />
        seconds
      </span>
      {error && (
        <span className="sn-field-error" id={`${id}-error`} role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
