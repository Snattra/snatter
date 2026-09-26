import { type ButtonHTMLAttributes, type CSSProperties, type InputHTMLAttributes, type ReactNode, useId, useRef } from "react";
import { classes } from "./classes";
import { Icon, type IconName } from "./icons";

export function Spinner() {
  return <span className="sn-spinner" aria-hidden="true" />;
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** `primary` is the one action of a screen; `link` is for quiet text actions such as Sign out. */
  variant?: "primary" | "secondary" | "danger" | "link";
  size?: "md" | "sm";
  /** Disables the button and shows a spinner while its action runs. */
  busy?: boolean;
  /** Full width, as in the sign-in card. */
  block?: boolean;
}

export function Button(props: ButtonProps) {
  const { variant = "secondary", size = "md", busy = false, block = false, type = "button", disabled, className, children, ...rest } =
    props;
  return (
    <button
      {...rest}
      type={type}
      className={classes(
        "sn-button",
        `sn-button-${variant}`,
        size === "sm" && "sn-button-sm",
        block && "sn-button-block",
        className,
      )}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
    >
      {busy && <Spinner />}
      {children}
    </button>
  );
}

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  /** The accessible name, also shown as the native tooltip. */
  label: string;
  icon: IconName;
  /** For toggles, such as showing the member list. */
  pressed?: boolean;
}

export function IconButton({ label, icon, pressed, className, ...rest }: IconButtonProps) {
  return (
    <button
      {...rest}
      type="button"
      className={classes("sn-icon-button", className)}
      aria-label={label}
      title={label}
      aria-pressed={pressed}
    >
      <Icon name={icon} />
    </button>
  );
}

interface TabsProps<T extends string> {
  /** The accessible name of the tab list. */
  label: string;
  tabs: { id: T; label: string }[];
  value: T;
  onChange: (id: T) => void;
}

/** A segmented choice; the selected fill slides to the new tab. Arrow keys move the choice. */
export function Tabs<T extends string>({ label, tabs, value, onChange }: TabsProps<T>) {
  const list = useRef<HTMLDivElement>(null);
  const index = Math.max(
    0,
    tabs.findIndex((tab) => tab.id === value),
  );

  function move(to: number) {
    const next = (to + tabs.length) % tabs.length;
    const tab = tabs[next];
    if (tab === undefined) {
      return;
    }
    onChange(tab.id);
    list.current?.querySelectorAll<HTMLElement>('[role="tab"]')[next]?.focus();
  }

  const position = { "--sn-count": tabs.length, "--sn-index": index } as CSSProperties;
  return (
    <div ref={list} className="sn-tabs" role="tablist" aria-label={label} style={position}>
      <span className="sn-tabs-indicator" aria-hidden="true" />
      {tabs.map((tab, i) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          className="sn-tab"
          aria-selected={i === index}
          tabIndex={i === index ? 0 : -1}
          onClick={() => onChange(tab.id)}
          onKeyDown={(event) => {
            if (event.key === "ArrowRight") {
              move(i + 1);
            } else if (event.key === "ArrowLeft") {
              move(i - 1);
            }
          }}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: ReactNode;
  /** Appends "(optional)" to the label. */
  optional?: boolean;
}

/** A labelled text input on a `bg` well; focus draws the sheen edge. */
export function Field({ label, optional = false, id, className, ...rest }: FieldProps) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <div className={classes("sn-field", className)}>
      <label htmlFor={inputId}>
        {label}
        {optional && <span className="sn-field-optional"> (optional)</span>}
      </label>
      <input {...rest} id={inputId} className="sn-input" />
    </div>
  );
}
