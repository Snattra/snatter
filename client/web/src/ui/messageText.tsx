import { type ReactNode, useMemo, useState } from "react";
import type { Account, Channel } from "../api/types";
import { Button } from "./controls";
import { ChannelIcon, Icon } from "./icons";
import { type Block, type Inline, parseMarkdown } from "./markdown";
import { Modal } from "./surfaces";

/** What message text needs from around it: who and what mentions name, and how to open them. */
export interface TextContext {
  /** Undefined for someone who is not a member, or no longer one. */
  member(accountId: string): Account | undefined;
  /** Undefined for a channel that is gone or that the member cannot see. */
  channel(channelId: string): Channel | undefined;
  openProfile(accountId: string): void;
  openChannel(channelId: string): void;
  /** Asks before opening. */
  openLink(url: string): void;
}

/**
 * A message's content, its Markdown rendered as elements: nothing the author
 * wrote ever becomes HTML. Mentions show the current name of whom or what they
 * name, and links open only through `context.openLink`, which asks first.
 */
export function MessageText({ content, context }: { content: string; context: TextContext }) {
  const blocks = useMemo(() => parseMarkdown(content), [content]);
  return <>{renderBlocks(blocks, context)}</>;
}

function renderBlocks(blocks: Block[], context: TextContext): ReactNode[] {
  return blocks.map((block, i) => {
    switch (block.type) {
      case "paragraph":
        return <p key={i}>{renderInlines(block.content, context)}</p>;
      case "code-block":
        return (
          <pre key={i} className="sn-code-block" data-language={block.language ?? undefined}>
            <code>{block.text}</code>
          </pre>
        );
      case "quote":
        return (
          <blockquote key={i} className="sn-quote">
            {renderBlocks(block.content, context)}
          </blockquote>
        );
      case "list": {
        const items = block.items.map((item, j) => (
          <li key={j}>
            {renderInlines(item.content, context)}
            {renderBlocks(item.lists, context)}
          </li>
        ));
        return block.ordered ? (
          <ol key={i} start={block.start === 1 ? undefined : block.start}>
            {items}
          </ol>
        ) : (
          <ul key={i}>{items}</ul>
        );
      }
    }
  });
}

function renderInlines(inlines: Inline[], context: TextContext): ReactNode[] {
  return inlines.map((inline, i) => {
    switch (inline.type) {
      case "text":
        return inline.text;
      case "code":
        return <code key={i}>{inline.text}</code>;
      case "link":
        return <MessageLink key={i} url={inline.url} onOpen={context.openLink} />;
      case "member":
        return <MemberMention key={i} accountId={inline.accountId} context={context} />;
      case "channel":
        return <ChannelMention key={i} channelId={inline.channelId} context={context} />;
      case "strong":
        return <strong key={i}>{renderInlines(inline.content, context)}</strong>;
      case "emphasis":
        return <em key={i}>{renderInlines(inline.content, context)}</em>;
      case "underline":
        return (
          <span key={i} className="sn-underline">
            {renderInlines(inline.content, context)}
          </span>
        );
      case "strike":
        return <s key={i}>{renderInlines(inline.content, context)}</s>;
      case "spoiler":
        return <Spoiler key={i}>{renderInlines(inline.content, context)}</Spoiler>;
    }
  });
}

/** A member by their display name as it is now, opening their profile. */
function MemberMention({ accountId, context }: { accountId: string; context: TextContext }) {
  const member = context.member(accountId);
  if (member === undefined) {
    return <span className="sn-mention sn-mention-unknown">@Unknown member</span>;
  }
  return (
    <button type="button" className="sn-mention" aria-haspopup="dialog" onClick={() => context.openProfile(accountId)}>
      @{member.displayName}
    </button>
  );
}

/**
 * A channel by its type icon and name, opening it. One the reader cannot
 * see shows no name, so a private channel's name never shows outside it.
 */
function ChannelMention({ channelId, context }: { channelId: string; context: TextContext }) {
  const channel = context.channel(channelId);
  if (channel === undefined) {
    return (
      <span className="sn-mention sn-mention-unknown">
        <Icon name="hash" />
        Unknown channel
      </span>
    );
  }
  return (
    <button type="button" className="sn-mention" onClick={() => context.openChannel(channelId)}>
      <ChannelIcon channel={channel} />
      {channel.name}
    </button>
  );
}

/**
 * A link showing its whole address. Clicking it, with any button or
 * modifier, asks before anything opens; the address stays a real link, so it
 * can still be copied.
 */
function MessageLink({ url, onOpen }: { url: string; onOpen: (url: string) => void }) {
  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(event) => {
        event.preventDefault();
        onOpen(url);
      }}
      onAuxClick={(event) => {
        if (event.button === 1) {
          event.preventDefault();
          onOpen(url);
        }
      }}
    >
      {url}
    </a>
  );
}

/**
 * Hidden until the reader chooses to see it. While hidden, what is inside is
 * inert, so a link in it cannot be clicked or reached unseen.
 */
function Spoiler({ children }: { children: ReactNode }) {
  const [shown, setShown] = useState(false);
  if (shown) {
    return <span className="sn-spoiler sn-spoiler-shown">{children}</span>;
  }
  return (
    <span
      className="sn-spoiler"
      role="button"
      tabIndex={0}
      aria-label="Spoiler, select to show"
      onClick={() => setShown(true)}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          setShown(true);
        }
      }}
    >
      <span inert>{children}</span>
    </span>
  );
}

/**
 * Asks before a link in a message opens, showing the whole address and the
 * host it really goes to: an address that looks familiar can use lookalike
 * letters, which the host shows in their encoded form. Cancel has focus, so
 * opening is always a choice of its own.
 */
export function LinkModal({ url, onOpen, onClose }: { url: string; onOpen: () => void; onClose: () => void }) {
  const host = new URL(url).host;
  return (
    <Modal
      title="Open this link?"
      onClose={onClose}
      footer={
        <>
          <Button data-autofocus onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" onClick={onOpen}>
            Open link
          </Button>
        </>
      }
    >
      <p className="sn-link-host">
        It goes to <strong>{host}</strong>, outside Snatter. Open it only if you trust where it leads.
      </p>
      <p className="sn-link-address">{url}</p>
    </Modal>
  );
}
