import {
  cloneElement,
  type ReactElement,
  type ReactNode,
  type RefObject,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
} from "react";
import { classes } from "./classes";
import { IconButton, Spinner } from "./controls";

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

/** A tinted explanation: `accent` for information, `warning` for caution. */
export function Callout(props: { title: string; tone?: "accent" | "warning"; children: ReactNode }) {
  const { title, tone = "accent", children } = props;
  return (
    <div className={classes("sn-callout", tone === "warning" && "sn-callout-warning")} role="note">
      <strong className="sn-callout-title">{title}</strong>
      <div>{children}</div>
    </div>
  );
}

/**
 * A connection-wide state across the top of the channel pane; it slides down
 * when it mounts. `warning` for degraded, `accent` for information.
 */
export function Banner(props: { tone?: "warning" | "accent"; busy?: boolean; children: ReactNode }) {
  const { tone = "warning", busy = false, children } = props;
  return (
    <div className="sn-banner-slot">
      <div className={classes("sn-banner", tone === "accent" && "sn-banner-accent")} role="status">
        {busy && <Spinner />}
        {children}
      </div>
    </div>
  );
}

/** A small pill naming something about a person: a role, with its colour as a dot (null for none), or "Owner" as `accent`. */
export function Tag(props: { color?: string | null; accent?: boolean; children: ReactNode }) {
  const { color, accent = false, children } = props;
  return (
    <span className={classes("sn-tag", accent && "sn-tag-accent")}>
      {color !== undefined && (
        <span className="sn-tag-dot" aria-hidden="true" style={color ? { background: color } : undefined} />
      )}
      {children}
    </span>
  );
}

interface TooltipProps {
  label: string;
  side?: "top" | "right" | "bottom" | "left";
  /** One focusable element, which the tooltip describes. */
  children: ReactElement<{ "aria-describedby"?: string }>;
}

/** A short label for an icon-only control, shown on hover and focus. */
export function Tooltip({ label, side = "top", children }: TooltipProps) {
  const id = useId();
  return (
    <span className="sn-tooltip-anchor">
      {cloneElement(children, { "aria-describedby": id })}
      {/* Hidden from names, so a tooltip inside a button is not read as part of it; aria-describedby still reads it. */}
      <span id={id} role="tooltip" aria-hidden="true" className={`sn-tooltip sn-tooltip-${side}`}>
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

interface ModalProps {
  title: ReactNode;
  /** One line under the title, such as the channel it is about. */
  description?: ReactNode;
  /** Called by the close button and Escape; stop rendering the modal to close it. */
  onClose: () => void;
  /** Makes the body and footer one form, with the browser's own validation off; check the fields yourself. */
  onSubmit?: () => void;
  /** Under the title, outside the scrolling body: Tabs. */
  tabs?: ReactNode;
  /** What went wrong, above the footer. */
  error?: ReactNode;
  /** Cancel, then the primary action. */
  footer: ReactNode;
  /** A destructive action at the footer's other end, such as Delete channel. */
  footerStart?: ReactNode;
  /** Wider, for settings with Tabs. */
  wide?: boolean;
  /** Beside the title, such as the member's avatar on a profile. */
  leading?: ReactNode;
  /** For a modal whose content changes: a new step focuses its own `data-autofocus` control. */
  step?: string;
  /** The body, which scrolls. Mark the control to focus first with `data-autofocus`. */
  children: ReactNode;
}

/**
 * A task over the app, open while it is mounted. It is a native modal
 * dialog, so the top layer, the focus trap and Escape come from the browser;
 * focus goes back to what opened it. A click on the scrim does not close it,
 * so a half-filled form is never lost to a stray click.
 */
export function Modal(props: ModalProps) {
  const {
    title,
    description,
    onClose,
    onSubmit,
    tabs,
    error,
    footer,
    footerStart,
    wide = false,
    leading,
    step,
    children,
  } = props;
  const id = useId();
  const dialog = useRef<HTMLDialogElement>(null);
  const shownStep = useRef(step);

  useLayoutEffect(() => {
    const element = dialog.current;
    if (element === null) {
      return;
    }
    if (!element.open) {
      element.showModal();
    }
    element.querySelector<HTMLElement>("[data-autofocus]")?.focus();
    // Closing it, not just removing it, is what hands focus back.
    return () => element.close();
  }, []);

  // The control that had focus went with the step it was in.
  useEffect(() => {
    if (shownStep.current === step) {
      return;
    }
    shownStep.current = step;
    dialog.current?.querySelector<HTMLElement>("[data-autofocus]")?.focus();
  }, [step]);

  const content = (
    <>
      <div className="sn-modal-body">{children}</div>
      {error && (
        <p className="sn-modal-error" role="alert">
          {error}
        </p>
      )}
      <footer className="sn-modal-footer">
        {footerStart && <div className="sn-modal-footer-start">{footerStart}</div>}
        {footer}
      </footer>
    </>
  );

  // Only Escape (cancel) is heard, not the dialog's close event: that one also
  // follows the close() above, late, which in development's mount, unmount and
  // mount again would close the modal that just opened.
  return (
    <dialog
      ref={dialog}
      className={classes("sn-modal", wide && "sn-modal-wide")}
      aria-labelledby={`${id}-title`}
      aria-describedby={description ? `${id}-description` : undefined}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <header className="sn-modal-header">
        {leading && <div className="sn-modal-leading">{leading}</div>}
        <div className="sn-modal-heading">
          <h2 id={`${id}-title`} className="sn-modal-title">
            {title}
          </h2>
          {description && (
            <p id={`${id}-description`} className="sn-modal-description">
              {description}
            </p>
          )}
        </div>
        <IconButton icon="close" label="Close" onClick={onClose} />
      </header>
      {tabs && <div className="sn-modal-tabs">{tabs}</div>}
      {onSubmit ? (
        <form
          className="sn-modal-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            onSubmit();
          }}
        >
          {content}
        </form>
      ) : (
        content
      )}
    </dialog>
  );
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
