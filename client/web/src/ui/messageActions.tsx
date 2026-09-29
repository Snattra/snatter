import { useState } from "react";
import type { Account, SystemMessage, UserMessage } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import { type MentionQuery, type Mentionables, withNames, withTokens } from "../state/mentions";
import { Button } from "./controls";
import { describeError } from "./errors";
import { Composer, type Suggestion } from "./messages";
import { MessageText, type TextContext } from "./messageText";
import { Modal } from "./surfaces";

/**
 * A member's own message being edited, in place of its text. It opens with
 * the text as they would type it, mentions as names, and saves them as
 * tokens again. Saving the same text, or Escape, just closes it.
 */
export function MessageEditor(props: {
  connection: ServerConnection;
  message: UserMessage;
  mentionables: Mentionables;
  suggest: (query: MentionQuery) => Suggestion[];
  onDone: () => void;
}) {
  const { connection, message, mentionables, suggest, onDone } = props;
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  // Taken once: names that change while editing must not reset the field.
  const [initial] = useState(() => withNames(message.content, mentionables.members, mentionables.channels));

  async function save(text: string) {
    const content = withTokens(text, mentionables.members, mentionables.channels);
    if (content === message.content) {
      onDone();
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      await connection.edit(message.channelId, message.id, content);
      onDone();
    } catch (e) {
      setFailure(describeError(e));
      setBusy(false);
    }
  }

  return (
    <Composer
      mode="edit"
      initialText={initial}
      placeholder="Edit message"
      disabled={busy}
      suggest={suggest}
      onCancel={onDone}
      onSend={(text) => void save(text)}
      footer={
        failure ? (
          <span className="sn-message-error" role="alert">
            {failure}
          </span>
        ) : (
          <span className="sn-editor-hint">
            Escape to{" "}
            <Button variant="link" onClick={onDone}>
              cancel
            </Button>
            , Enter to save
          </span>
        )
      }
    />
  );
}

/**
 * Asks before a message is deleted, showing what goes. A member's message
 * stays as a line saying it was deleted, by a moderator when it is not the
 * member's own; a notice goes entirely.
 */
export function DeleteMessageModal(props: {
  connection: ServerConnection;
  message: UserMessage | SystemMessage;
  author: Account | undefined;
  own: boolean;
  text: TextContext;
  onClose: () => void;
}) {
  const { connection, message, author, own, text, onClose } = props;
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  async function remove() {
    setBusy(true);
    setFailure(null);
    try {
      await connection.delete(message);
      onClose();
    } catch (e) {
      setFailure(describeError(e));
      setBusy(false);
    }
  }

  return (
    <Modal
      title={message.kind === "system" ? "Delete notice" : "Delete message"}
      onClose={onClose}
      error={failure}
      footer={
        <>
          <Button data-autofocus onClick={onClose}>
            Cancel
          </Button>
          <Button variant="danger" busy={busy} onClick={() => void remove()}>
            {busy ? "Deleting…" : "Delete"}
          </Button>
        </>
      }
    >
      <p className="sn-modal-text">
        {message.kind === "system"
          ? "The notice disappears from the channel."
          : own
            ? "It will read “This message was deleted.” What it said is gone for good."
            : "It will read “Removed by a moderator.” What it said is gone for good."}
      </p>
      {message.kind === "user" && (
        <div className="sn-message-preview">
          <span className="sn-message-author">{author?.displayName ?? "Unknown member"}</span>
          <div className="sn-message-content">
            <MessageText content={message.content} context={text} />
          </div>
        </div>
      )}
      <p className="sn-modal-hint">Hold Shift when you click delete to skip this question.</p>
    </Modal>
  );
}
