import { cloneElement, type ReactElement, type ReactNode, useId } from "react";
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
