import {
  type ButtonHTMLAttributes,
  type CSSProperties,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
  useEffect,
  useId,
  useRef,
} from "react";
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

interface FieldFrameProps {
  label: ReactNode;
  /** Appends "(optional)" to the label. */
  optional?: boolean;
  /** Helper copy under the control; an error replaces it. */
  hint?: ReactNode;
  /** Marks the field invalid and shakes it once. Each new message shakes it again. */
  error?: ReactNode;
}

interface FieldProps extends FieldFrameProps, InputHTMLAttributes<HTMLInputElement> {}

/** A labelled text input on a `bg` well; focus draws the sheen edge. */
export function Field({ label, optional, hint, error, id, className, ...rest }: FieldProps) {
  const control = useControl<HTMLInputElement>(id, hint, error);
  return (
    <FieldFrame label={label} optional={optional} hint={hint} error={error} className={className} inputId={control.id}>
      <input {...rest} {...control} />
    </FieldFrame>
  );
}

interface TextAreaProps extends FieldFrameProps, TextareaHTMLAttributes<HTMLTextAreaElement> {}

/** A Field around a textarea, three lines tall to start with, for descriptions and topics. */
export function TextArea({ label, optional, hint, error, id, className, rows = 3, ...rest }: TextAreaProps) {
  const control = useControl<HTMLTextAreaElement>(id, hint, error);
  return (
    <FieldFrame label={label} optional={optional} hint={hint} error={error} className={className} inputId={control.id}>
      <textarea {...rest} rows={rows} {...control} />
    </FieldFrame>
  );
}

interface SelectProps extends FieldFrameProps, SelectHTMLAttributes<HTMLSelectElement> {
  /** The options. */
  children: ReactNode;
}

/** A Field around a native select, its arrow drawn as `chevron-down`. */
export function Select({ label, optional, hint, error, id, className, children, ...rest }: SelectProps) {
  const control = useControl<HTMLSelectElement>(id, hint, error);
  return (
    <FieldFrame label={label} optional={optional} hint={hint} error={error} className={className} inputId={control.id}>
      <span className="sn-select">
        <select {...rest} {...control}>
          {children}
        </select>
        <Icon name="chevron-down" />
      </span>
    </FieldFrame>
  );
}

function FieldFrame(props: FieldFrameProps & { className?: string; inputId: string; children: ReactNode }) {
  const { label, optional = false, hint, error, className, inputId, children } = props;
  return (
    <div className={classes("sn-field", error ? "sn-field-invalid" : null, className)}>
      <label htmlFor={inputId}>
        {label}
        {optional && <span className="sn-field-optional"> (optional)</span>}
      </label>
      {children}
      <Help id={inputId} hint={hint} error={error} />
    </div>
  );
}

/** The props a field's control needs: its id, what describes it, and a ref that shakes it on each new error. */
function useControl<T extends HTMLElement>(id: string | undefined, hint: ReactNode, error: ReactNode) {
  const generated = useId();
  const controlId = id ?? generated;
  const ref = useRef<T>(null);
  useEffect(() => {
    const control = ref.current;
    if (!error || control === null) {
      return;
    }
    // Restarting the animation needs a style change the browser has seen.
    control.style.animation = "none";
    void control.offsetWidth;
    control.style.animation = "";
  }, [error]);
  return {
    ref,
    id: controlId,
    className: "sn-input",
    "aria-invalid": error ? true : undefined,
    "aria-describedby": describedBy(controlId, hint, error),
  };
}

/** Under a field or a set of choices: the error while there is one, else the hint. */
function Help({ id, hint, error }: { id: string; hint: ReactNode; error: ReactNode }) {
  if (error) {
    return (
      <span className="sn-field-error" id={`${id}-error`} role="alert">
        {error}
      </span>
    );
  }
  return hint ? (
    <span className="sn-field-hint" id={`${id}-hint`}>
      {hint}
    </span>
  ) : null;
}

function describedBy(id: string, hint: ReactNode, error: ReactNode): string | undefined {
  if (error) {
    return `${id}-error`;
  }
  return hint ? `${id}-hint` : undefined;
}

interface ChoiceProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "type"> {
  /** `radio` for one of a set sharing a `name`; `checkbox` for a setting that is on or off. */
  type: "radio" | "checkbox";
  label: ReactNode;
  /** A line under the label, read out after it. */
  description?: ReactNode;
  /** Beside the label, such as a channel type; it takes the accent when chosen. */
  icon?: IconName;
}

/** A radio or checkbox as a row, its mark filling with the sheen when chosen. The native input stays, out of sight. */
export function Choice({ type, label, description, icon, id, className, ...rest }: ChoiceProps) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <label className={classes("sn-choice", type === "radio" && "sn-choice-radio", className)}>
      <input
        {...rest}
        type={type}
        id={inputId}
        className="sn-choice-input"
        aria-labelledby={`${inputId}-label`}
        aria-describedby={description ? `${inputId}-description` : undefined}
      />
      <span className="sn-choice-mark" aria-hidden="true">
        {type === "checkbox" && <Icon name="check" />}
      </span>
      {icon && <Icon name={icon} />}
      <span className="sn-choice-text">
        <span id={`${inputId}-label`} className="sn-choice-label">
          {label}
        </span>
        {description && (
          <span id={`${inputId}-description`} className="sn-choice-description">
            {description}
          </span>
        )}
      </span>
    </label>
  );
}

interface ChoiceGroupProps {
  /** Names the set, as a label names a field. */
  legend: ReactNode;
  hint?: ReactNode;
  /** Replaces the hint and edges the empty marks in `danger`. */
  error?: ReactNode;
  children: ReactNode;
}

/** Choices that belong together, under a legend. */
export function ChoiceGroup({ legend, hint, error, children }: ChoiceGroupProps) {
  const id = useId();
  return (
    <fieldset
      className={classes("sn-choices", error ? "sn-choices-invalid" : null)}
      aria-describedby={describedBy(id, hint, error)}
    >
      <legend className="sn-choices-legend">{legend}</legend>
      <div className="sn-choices-list">{children}</div>
      <Help id={id} hint={hint} error={error} />
    </fieldset>
  );
}
