import { cloneElement, type ReactElement, type ReactNode, type RefObject, useEffect, useId, useRef } from "react";
import { classes } from "./classes";
import { Spinner } from "./controls";

/** The full-screen ground with the accent glows, centring a Card. */
export function Backdrop({ children }: { children: ReactNode }) {
  return <div className="sn-backdrop">{children}</div>;
}

/** A single task on the bare ground, such as signing in. It rises in when it mounts. */
export function Card({ title, description, children }: { title: ReactNode; description?: ReactNode; children: ReactNode }) {
  return (
    <div className="sn-card">
      <h1 className="sn-card-title">{title}</h1>
      {description && <p className="sn-card-description">{description}</p>}
      <div className="sn-card-body">{children}</div>
    </div>
  );
}

export function Callout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="sn-callout" role="note">
      <strong className="sn-callout-title">{title}</strong>
      <div>{children}</div>
    </div>
  );
}

/** A connection-wide state across the top of the channel pane; it slides down when it mounts. */
export function Banner({ busy = false, children }: { busy?: boolean; children: ReactNode }) {
  return (
    <div className="sn-banner-slot">
      <div className="sn-banner" role="status">
        {busy && <Spinner />}
        {children}
      </div>
    </div>
  );
}

interface TooltipProps {
  label: string;
  side?: "top" | "right" | "bottom";
  /** One focusable element, which the tooltip describes. */
  children: ReactElement<{ "aria-describedby"?: string }>;
}

/** A short label for an icon-only control, shown on hover and focus. */
export function Tooltip({ label, side = "top", children }: TooltipProps) {
  const id = useId();
  return (
    <span className="sn-tooltip-anchor">
      {cloneElement(children, { "aria-describedby": id })}
      <span id={id} role="tooltip" className={`sn-tooltip sn-tooltip-${side}`}>
        {label}
      </span>
    </span>
  );
}

/** Between a popover and its control, and from the window's edge: `space-8`. */
const POPOVER_GAP = 8;

interface PopoverProps {
  /** The control opens it with `popoverTarget` set to this. */
  id: string;
  title: ReactNode;
  description?: ReactNode;
  /** The control it opens from; their right edges line up. */
  anchor: RefObject<HTMLElement | null>;
  side?: "top" | "bottom";
  /** Called as it opens and closes, including on Escape and clicks outside. */
  onToggle?: (open: boolean) => void;
  children: ReactNode;
}

/**
 * A small task beside the control that opened it. It lives in the top layer,
 * so no pane clips it, and the browser closes it on Escape or a click outside.
 */
export function Popover({ id, title, description, anchor, side = "top", onToggle, children }: PopoverProps) {
  const popover = useRef<HTMLDivElement>(null);

  // Its place was worked out for the old window size.
  useEffect(() => {
    const close = () => {
      if (popover.current?.matches(":popover-open")) {
        popover.current.hidePopover();
      }
    };
    window.addEventListener("resize", close);
    return () => window.removeEventListener("resize", close);
  }, []);

  return (
    <div
      id={id}
      ref={popover}
      popover="auto"
      role="dialog"
      aria-labelledby={`${id}-title`}
      className={classes("sn-popover", side === "bottom" && "sn-popover-bottom")}
      onBeforeToggle={(event) => {
        if (event.newState === "open" && popover.current !== null && anchor.current !== null) {
          place(popover.current, anchor.current, side);
        }
      }}
      onToggle={(event) => onToggle?.(event.newState === "open")}
    >
      <h2 id={`${id}-title`} className="sn-popover-title">
        {title}
      </h2>
      {description && <p className="sn-popover-description">{description}</p>}
      {children}
    </div>
  );
}

/** Right edges together, above or below the control; the stylesheet narrows it to fit the window. */
function place(popover: HTMLElement, anchor: HTMLElement, side: "top" | "bottom") {
  const rect = anchor.getBoundingClientRect();
  const { clientWidth, clientHeight } = document.documentElement;
  popover.style.right = `${Math.max(POPOVER_GAP, clientWidth - rect.right)}px`;
  popover.style.top = side === "bottom" ? `${rect.bottom + POPOVER_GAP}px` : "auto";
  popover.style.bottom = side === "bottom" ? "auto" : `${clientHeight - rect.top + POPOVER_GAP}px`;
}

/** A placeholder in the shape of content still loading. Mark the region it fills as busy. */
export function Skeleton({ variant = "line", width }: { variant?: "line" | "circle" | "message"; width?: string }) {
  if (variant === "message") {
    return (
      <div className="sn-skeleton-message" aria-hidden="true">
        <span className="sn-skeleton sn-skeleton-circle" />
        <span className="sn-skeleton-lines">
          <span className="sn-skeleton" style={{ width: "28%" }} />
          <span className="sn-skeleton" style={{ width: width ?? "76%" }} />
          <span className="sn-skeleton" style={{ width: "52%" }} />
        </span>
      </div>
    );
  }
  return (
    <span
      className={variant === "circle" ? "sn-skeleton sn-skeleton-circle" : "sn-skeleton"}
      aria-hidden="true"
      style={width === undefined ? undefined : { width }}
    />
  );
}
