/* @ds-bundle: {"format":4,"namespace":"Snatter","components":[{"name":"AppShell"},{"name":"ServerRail"},{"name":"Sidebar"},{"name":"ChannelHeader"},{"name":"Message"},{"name":"NewMessages"},{"name":"Composer"},{"name":"TypingIndicator"},{"name":"MemberList"},{"name":"UserPanel"},{"name":"Avatar"},{"name":"Button"},{"name":"IconButton"},{"name":"Tabs"},{"name":"Field"},{"name":"Choice"},{"name":"Card"},{"name":"Callout"},{"name":"Tag"},{"name":"Banner"},{"name":"Tooltip"},{"name":"Popover"},{"name":"Modal"},{"name":"Invite"},{"name":"Profile"},{"name":"Skeleton"},{"name":"Icon"}]} */
(function () {
  "use strict";
  var React = window.React;
  var h = React.createElement;
  var useState = React.useState;
  var useEffect = React.useEffect;
  var useLayoutEffect = React.useLayoutEffect;
  var useRef = React.useRef;
  var useContext = React.useContext;

  function cx() {
    return Array.prototype.filter.call(arguments, Boolean).join(" ");
  }

  function omit(props, keys) {
    var out = {};
    for (var k in props) {
      if (Object.prototype.hasOwnProperty.call(props, k) && keys.indexOf(k) === -1) out[k] = props[k];
    }
    return out;
  }

  var uid = 0;
  function useStableId(given) {
    var ref = useRef(null);
    if (ref.current === null) ref.current = "sn-" + ++uid;
    return given || ref.current;
  }

  function prefersReducedMotion() {
    return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  /* ---------- Icons ---------- */

  var PATHS = {
    hash: "M10 3 8.5 9H4v2h4l-1 4H3v2h3.5L5 21h2l1.5-4h4L11 21h2l1.5-4H19v-2h-4l1-4h4V9h-3.5L18 3h-2l-1.5 6h-4L12 3h-2zm0 8h4l-1 4H9l1-4z",
    speaker: "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05A4.5 4.5 0 0 0 16.5 12z",
    members:
      "M16 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm-8 0a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5C15 14.17 10.33 13 8 13zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
    "chevron-left": "M15 6l1.4 1.4-4.6 4.6 4.6 4.6L15 18l-6-6z",
    "chevron-right": "M9 6l6 6-6 6-1.4-1.4 4.6-4.6-4.6-4.6z",
    plus: "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z",
    close: "M6.4 5 12 10.6 17.6 5 19 6.4 13.4 12 19 17.6 17.6 19 12 13.4 6.4 19 5 17.6 10.6 12 5 6.4z",
    send: "M3.4 20.4 21 12 3.4 3.6v6.6L16 12 3.4 13.8z",
    "arrow-right": "M4 11h12.2l-5.6-5.6L12 4l8 8-8 8-1.4-1.4 5.6-5.6H4z",
    "arrow-up": "M11 20V7.8l-5.6 5.6L4 12l8-8 8 8-1.4 1.4L13 7.8V20z",
    "person-add":
      "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
    settings:
      "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94L14.4 2.81c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41L9.25 5.35c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
    "chevron-down": "M18 9l-6 6-6-6 1.4-1.4 4.6 4.6 4.6-4.6z",
    check: "M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
    edit: "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
    delete: "M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
    ban: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-4.42 3.58-8 8-8 1.85 0 3.55.63 4.9 1.69L5.69 16.9A7.9 7.9 0 0 1 4 12zm8 8c-1.85 0-3.55-.63-4.9-1.69L18.31 7.1A7.9 7.9 0 0 1 20 12c0 4.42-3.58 8-8 8z",
    mic: "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z",
    "mic-off": "M19 11h-1.7c0 .74-.16 1.43-.43 2.05l1.23 1.23c.56-.98.9-2.09.9-3.28zm-4.02.17c0-.06.02-.11.02-.17V5c0-1.66-1.34-3-3-3S9 3.34 9 5v.18l5.98 5.99zM4.27 3 3 4.27l6.01 6.01V11c0 1.66 1.33 3 2.99 3 .22 0 .44-.03.65-.08l1.66 1.66c-.71.33-1.5.52-2.31.52-2.76 0-5.3-2.1-5.3-5.1H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c.91-.13 1.77-.45 2.54-.9L19.73 21 21 19.73 4.27 3z",
    headset: "M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z",
    "headset-off": "M12 4c3.87 0 7 3.13 7 7v2h-2.92L21 17.92V11c0-4.97-4.03-9-9-9-1.95 0-3.76.62-5.23 1.68l1.44 1.44C9.3 4.41 10.6 4 12 4zM2.27 1.72 1 3l3.33 3.32C3.49 7.68 3 9.29 3 11v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-1.17.29-2.26.79-3.22L15 17v4h3c.3 0 .59-.06.86-.14L21 23l1.27-1.27-20-20.01z",
    "call-end": "M12 9c-1.6 0-3.15.25-4.6.72v3.1c0 .39-.23.74-.56.9-.98.49-1.87 1.12-2.66 1.85-.18.18-.43.28-.7.28-.28 0-.53-.11-.71-.29L.29 13.08c-.18-.17-.29-.42-.29-.7 0-.28.11-.53.29-.71C3.34 8.78 7.46 7 12 7s8.66 1.78 11.71 4.67c.18.18.29.43.29.71 0 .28-.11.53-.29.71l-2.48 2.48c-.18.18-.43.29-.71.29-.27 0-.52-.11-.7-.28-.79-.74-1.69-1.36-2.67-1.85-.33-.16-.56-.5-.56-.9v-3.1C15.15 9.25 13.6 9 12 9z"
  };

  function Icon(props) {
    var size = props.size;
    return h(
      "svg",
      {
        className: cx("sn-icon", props.className),
        viewBox: "0 0 24 24",
        "aria-hidden": "true",
        style: size ? { width: size, height: size } : undefined
      },
      h("path", { fill: "currentColor", d: PATHS[props.name] || "" })
    );
  }

  function ChannelIcon(props) {
    return h(Icon, { name: props.type === "text" ? "hash" : "speaker" });
  }

  function Spinner() {
    return h("span", { className: "sn-spinner", "aria-hidden": "true" });
  }

  /* ---------- Actions ---------- */

  function Button(props) {
    var variant = props.variant || "secondary";
    var rest = omit(props, ["variant", "size", "busy", "block", "className", "children"]);
    return h(
      "button",
      Object.assign({ type: "button" }, rest, {
        className: cx(
          "sn-button",
          "sn-button-" + variant,
          props.size === "sm" && "sn-button-sm",
          props.block && "sn-button-block",
          props.className
        ),
        disabled: props.disabled || props.busy,
        "aria-busy": props.busy ? "true" : undefined
      }),
      props.busy && h(Spinner),
      props.children
    );
  }

  function IconButton(props) {
    var rest = omit(props, ["label", "icon", "pressed", "className", "children"]);
    return h(
      "button",
      Object.assign({ type: "button" }, rest, {
        className: cx("sn-icon-button", props.className),
        "aria-label": props.label,
        title: props.label,
        "aria-pressed": props.pressed === undefined ? undefined : props.pressed
      }),
      props.icon ? h(Icon, { name: props.icon }) : props.children
    );
  }

  function Tabs(props) {
    var tabs = props.tabs || [];
    var index = Math.max(
      0,
      tabs.findIndex(function (t) {
        return t.id === props.value;
      })
    );
    var listRef = useRef(null);

    function choose(i) {
      var tab = tabs[(i + tabs.length) % tabs.length];
      if (props.onChange) props.onChange(tab.id);
      var buttons = listRef.current ? listRef.current.querySelectorAll('[role="tab"]') : [];
      var el = buttons[(i + tabs.length) % tabs.length];
      if (el) el.focus();
    }

    return h(
      "div",
      {
        ref: listRef,
        className: cx("sn-tabs", props.className),
        role: "tablist",
        "aria-label": props.label,
        style: { "--sn-count": tabs.length, "--sn-index": index }
      },
      h("span", { className: "sn-tabs-indicator", "aria-hidden": "true" }),
      tabs.map(function (tab, i) {
        var selected = i === index;
        return h(
          "button",
          {
            key: tab.id,
            type: "button",
            role: "tab",
            className: "sn-tab",
            "aria-selected": selected,
            tabIndex: selected ? 0 : -1,
            onClick: function () {
              if (props.onChange) props.onChange(tab.id);
            },
            onKeyDown: function (e) {
              if (e.key === "ArrowRight") choose(i + 1);
              else if (e.key === "ArrowLeft") choose(i - 1);
            }
          },
          tab.label
        );
      })
    );
  }

  /* ---------- Forms ---------- */

  // A new error shakes the control again, even when it was already invalid.
  function useShake(error) {
    var ref = useRef(null);
    useEffect(
      function () {
        var el = ref.current;
        if (!error || !el) return;
        el.style.animation = "none";
        void el.offsetWidth;
        el.style.animation = "";
      },
      [error]
    );
    return ref;
  }

  /** Label, control, and a hint or error under it, for Field, TextArea and Select. */
  function fieldFrame(props, id, control) {
    return h(
      "div",
      { className: cx("sn-field", props.error && "sn-field-invalid", props.className) },
      h(
        "label",
        { htmlFor: id },
        props.label,
        props.optional && h("span", { className: "sn-field-optional" }, " (optional)")
      ),
      control,
      props.error
        ? h("span", { className: "sn-field-error", id: id + "-error", role: "alert" }, props.error)
        : props.hint && h("span", { className: "sn-field-hint", id: id + "-hint" }, props.hint)
    );
  }

  function controlProps(props, id, ref) {
    return Object.assign(omit(props, ["label", "hint", "error", "optional", "className", "id", "children"]), {
      ref: ref,
      id: id,
      className: "sn-input",
      "aria-invalid": props.error ? true : undefined,
      "aria-describedby": props.error ? id + "-error" : props.hint ? id + "-hint" : undefined
    });
  }

  function Field(props) {
    var id = useStableId(props.id);
    var ref = useShake(props.error);
    return fieldFrame(props, id, h("input", controlProps(props, id, ref)));
  }

  function TextArea(props) {
    var id = useStableId(props.id);
    var ref = useShake(props.error);
    return fieldFrame(props, id, h("textarea", Object.assign({ rows: 3 }, controlProps(props, id, ref))));
  }

  function Select(props) {
    var id = useStableId(props.id);
    var ref = useShake(props.error);
    return fieldFrame(
      props,
      id,
      h("span", { className: "sn-select" }, h("select", controlProps(props, id, ref), props.children), h(Icon, { name: "chevron-down" }))
    );
  }

  /* ---------- Choice ---------- */

  function Choice(props) {
    var id = useStableId(props.id);
    var radio = props.type === "radio";
    var rest = omit(props, ["type", "label", "description", "icon", "className", "id"]);
    return h(
      "label",
      { className: cx("sn-choice", radio && "sn-choice-radio", props.className) },
      h(
        "input",
        Object.assign(rest, {
          type: radio ? "radio" : "checkbox",
          id: id,
          className: "sn-choice-input",
          "aria-labelledby": id + "-label",
          "aria-describedby": props.description ? id + "-description" : undefined
        })
      ),
      h("span", { className: "sn-choice-mark", "aria-hidden": "true" }, !radio && h(Icon, { name: "check" })),
      props.icon && h(Icon, { name: props.icon }),
      h(
        "span",
        { className: "sn-choice-text" },
        h("span", { className: "sn-choice-label", id: id + "-label" }, props.label),
        props.description && h("span", { className: "sn-choice-description", id: id + "-description" }, props.description)
      )
    );
  }

  function ChoiceGroup(props) {
    var id = useStableId();
    return h(
      "fieldset",
      {
        className: cx("sn-choices", props.error && "sn-choices-invalid", props.className),
        "aria-describedby": props.error ? id + "-error" : props.hint ? id + "-hint" : undefined
      },
      h("legend", { className: "sn-choices-legend" }, props.legend),
      h("div", { className: "sn-choices-list" }, props.children),
      props.error
        ? h("span", { className: "sn-field-error", id: id + "-error", role: "alert" }, props.error)
        : props.hint && h("span", { className: "sn-field-hint", id: id + "-hint" }, props.hint)
    );
  }

  function Composer(props) {
    var controlled = props.value !== undefined;
    var state = useState("");
    var value = controlled ? props.value : state[0];

    function setValue(next) {
      if (!controlled) state[1](next);
      if (props.onChange) props.onChange(next);
    }

    function send() {
      var text = value.trim();
      if (!text) return;
      if (props.onSend) props.onSend(text);
      setValue("");
    }

    return h(
      "div",
      { className: "sn-composer-area" },
      h(
        "div",
        { className: cx("sn-composer", value.trim() && "sn-composer-ready") },
        props.onAttach && h(IconButton, { icon: "plus", label: "Attach", onClick: props.onAttach }),
        h("textarea", {
          rows: 1,
          value: value,
          placeholder: props.placeholder,
          disabled: props.disabled,
          "aria-label": props.label || props.placeholder || "Message",
          onChange: function (e) {
            setValue(e.target.value);
          },
          onKeyDown: function (e) {
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              send();
            }
          }
        }),
        h(IconButton, { icon: "send", label: "Send", className: "sn-composer-send", onClick: send, tabIndex: value.trim() ? 0 : -1 })
      ),
      h("div", { className: "sn-composer-foot" }, props.footer)
    );
  }

  /* ---------- Surfaces ---------- */

  function Card(props) {
    return h(
      "div",
      { className: cx("sn-card", props.className) },
      props.title && h(props.titleAs || "h1", { className: "sn-card-title" }, props.title),
      props.description && h("p", { className: "sn-card-description" }, props.description),
      h("div", { className: "sn-card-body" }, props.children)
    );
  }

  function Backdrop(props) {
    return h("div", { className: cx("sn-backdrop", props.className) }, props.children);
  }

  function Callout(props) {
    return h(
      "div",
      { className: cx("sn-callout", props.tone === "warning" && "sn-callout-warning", props.className), role: "note" },
      props.title && h("strong", { className: "sn-callout-title" }, props.title),
      h("div", null, props.children)
    );
  }

  function Tag(props) {
    return h(
      "span",
      { className: cx("sn-tag", props.accent && "sn-tag-accent", props.className) },
      props.color !== undefined &&
        h("span", { className: "sn-tag-dot", "aria-hidden": "true", style: props.color ? { background: props.color } : undefined }),
      props.children
    );
  }

  function Tooltip(props) {
    var id = useStableId();
    var child = React.Children.only(props.children);
    return h(
      "span",
      { className: "sn-tooltip-anchor" },
      React.cloneElement(child, { "aria-describedby": id, title: undefined }),
      // Hidden from names, so a tooltip inside a button is not read as part of it; aria-describedby still reads it.
      h("span", { id: id, role: "tooltip", "aria-hidden": "true", className: "sn-tooltip sn-tooltip-" + (props.side || "top") }, props.label)
    );
  }

  /* ---------- Popover ---------- */

  // var(--space-8): between the popover and its control, and from the window's edge.
  var POPOVER_GAP = 8;

  /** Lines the popover up with its control: right edges together, above or below it. */
  function placePopover(popover, anchor, side) {
    var rect = anchor.getBoundingClientRect();
    var root = document.documentElement;
    popover.style.right = Math.max(POPOVER_GAP, root.clientWidth - rect.right) + "px";
    if (side === "bottom") {
      popover.style.top = rect.bottom + POPOVER_GAP + "px";
      popover.style.bottom = "auto";
    } else {
      popover.style.bottom = root.clientHeight - rect.top + POPOVER_GAP + "px";
      popover.style.top = "auto";
    }
  }

  function Popover(props) {
    var id = useStableId(props.id);
    var ref = useRef(null);
    var side = props.side || "top";
    var anchorRef = props.anchorRef;
    var onToggle = props.onToggle;

    useEffect(
      function () {
        var el = ref.current;
        if (!el) return;
        function before(e) {
          if (e.newState === "open" && anchorRef && anchorRef.current) placePopover(el, anchorRef.current, side);
        }
        function toggled(e) {
          if (onToggle) onToggle(e.newState === "open");
        }
        // Its place was worked out for the old window size.
        function resized() {
          if (el.matches(":popover-open")) el.hidePopover();
        }
        el.addEventListener("beforetoggle", before);
        el.addEventListener("toggle", toggled);
        window.addEventListener("resize", resized);
        return function () {
          el.removeEventListener("beforetoggle", before);
          el.removeEventListener("toggle", toggled);
          window.removeEventListener("resize", resized);
        };
      },
      [anchorRef, side, onToggle]
    );

    return h(
      "div",
      {
        id: id,
        ref: ref,
        popover: "auto",
        role: "dialog",
        "aria-labelledby": id + "-title",
        className: cx("sn-popover", side === "bottom" && "sn-popover-bottom", props.className)
      },
      h("h2", { id: id + "-title", className: "sn-popover-title" }, props.title),
      props.description && h("p", { className: "sn-popover-description" }, props.description),
      props.children
    );
  }

  /* ---------- Modal ---------- */

  function Modal(props) {
    var id = useStableId(props.id);
    var ref = useRef(null);
    var shownStep = useRef(props.step);
    var onClose = props.onClose;

    // A new step takes focus to its own data-autofocus control, as the one that had it is gone.
    useEffect(
      function () {
        if (shownStep.current === props.step) return;
        shownStep.current = props.step;
        var target = ref.current && ref.current.querySelector("[data-autofocus]");
        if (target) target.focus();
      },
      [props.step]
    );

    // Open while mounted, as a modal: the top layer, the focus trap and Escape
    // come with <dialog>. Closing it hands focus back to what opened it.
    useLayoutEffect(function () {
      var el = ref.current;
      if (!el.open) el.showModal();
      var first = el.querySelector("[data-autofocus]");
      if (first) first.focus();
      return function () {
        if (el.open) el.close();
      };
    }, []);

    var content = [
      h("div", { key: "body", className: "sn-modal-body" }, props.children),
      props.error && h("p", { key: "error", className: "sn-modal-error", role: "alert" }, props.error),
      (props.footer || props.footerStart) &&
        h(
          "footer",
          { key: "footer", className: "sn-modal-footer" },
          props.footerStart && h("div", { className: "sn-modal-footer-start" }, props.footerStart),
          props.footer
        )
    ];

    return h(
      "dialog",
      {
        ref: ref,
        className: cx("sn-modal", props.wide && "sn-modal-wide", props.className),
        "aria-labelledby": id + "-title",
        "aria-describedby": props.description ? id + "-description" : undefined,
        // Escape: the owner closes it by no longer rendering it.
        onCancel: function (e) {
          e.preventDefault();
          if (onClose) onClose();
        }
      },
      h(
        "header",
        { className: "sn-modal-header" },
        props.leading && h("div", { className: "sn-modal-leading" }, props.leading),
        h(
          "div",
          { className: "sn-modal-heading" },
          h("h2", { id: id + "-title", className: "sn-modal-title" }, props.title),
          props.description && h("p", { id: id + "-description", className: "sn-modal-description" }, props.description)
        ),
        onClose && h(IconButton, { icon: "close", label: "Close", onClick: onClose })
      ),
      props.tabs && h("div", { className: "sn-modal-tabs" }, props.tabs),
      props.onSubmit
        ? h(
            "form",
            {
              className: "sn-modal-form",
              noValidate: true,
              onSubmit: function (e) {
                e.preventDefault();
                props.onSubmit(e);
              }
            },
            content
          )
        : content
    );
  }

  /* ---------- Invite ---------- */

  function InviteButton(props) {
    return h(
      "button",
      {
        type: "button",
        ref: props.buttonRef,
        className: "sn-invite-button",
        popovertarget: props.popoverId,
        "aria-expanded": !!props.expanded
      },
      h(Icon, { name: "person-add" }),
      props.label || "Invite people"
    );
  }

  function InviteLink(props) {
    var copied = useState(false);
    var blocked = useState(false);
    var urlRef = useRef(null);

    useEffect(
      function () {
        if (!copied[0]) return;
        var t = setTimeout(function () {
          copied[1](false);
        }, 2000);
        return function () {
          clearTimeout(t);
        };
      },
      [copied[0]]
    );

    if (props.error) {
      return h(
        "div",
        { className: "sn-invite-foot" },
        h("p", { className: "sn-invite-status sn-invite-status-error", role: "alert" }, props.error),
        props.onRetry && h(Button, { size: "sm", onClick: props.onRetry }, "Try again")
      );
    }
    if (!props.url) {
      return h("p", { className: "sn-invite-status", role: "status" }, h(Spinner), "Creating a link…");
    }

    function copy() {
      function done() {
        blocked[1](false);
        copied[1](true);
      }
      function fail() {
        window.getSelection().selectAllChildren(urlRef.current);
        blocked[1](true);
      }
      if (navigator.clipboard) navigator.clipboard.writeText(props.url).then(done, fail);
      else fail();
    }

    return h(
      React.Fragment,
      null,
      h("p", { ref: urlRef, className: "sn-invite-url" }, props.url),
      h(
        "div",
        { className: "sn-invite-foot" },
        h("span", null, blocked[0] ? "Your browser blocked copying, so the link is selected instead." : props.expiry),
        h(Button, { size: "sm", variant: copied[0] ? "secondary" : "primary", onClick: copy }, copied[0] ? "Copied" : "Copy")
      ),
      h("span", { className: "sn-visually-hidden", role: "status" }, copied[0] ? "Link copied" : "")
    );
  }

  /* ---------- Feedback ---------- */

  function Banner(props) {
    return h(
      "div",
      { className: "sn-banner-slot" },
      h(
        "div",
        { className: cx("sn-banner", props.tone && props.tone !== "warning" && "sn-banner-" + props.tone), role: "status" },
        props.busy && h(Spinner),
        props.children
      )
    );
  }

  function Skeleton(props) {
    if (props.variant === "message") {
      return h(
        "div",
        { className: "sn-skeleton-message", "aria-hidden": "true" },
        h("span", { className: "sn-skeleton sn-skeleton-circle" }),
        h(
          "span",
          { className: "sn-skeleton-lines" },
          h("span", { className: "sn-skeleton", style: { width: "28%" } }),
          h("span", { className: "sn-skeleton", style: { width: props.width || "76%" } }),
          h("span", { className: "sn-skeleton", style: { width: "52%" } })
        )
      );
    }
    return h("span", {
      className: cx("sn-skeleton", props.variant === "circle" && "sn-skeleton-circle"),
      "aria-hidden": "true",
      style: props.width ? { width: props.width } : undefined
    });
  }

  function TypingIndicator(props) {
    var names = props.names || [];
    var dots = h(
      "span",
      { className: "sn-typing-dots", "aria-hidden": "true" },
      h("span", { className: "sn-typing-dot" }),
      h("span", { className: "sn-typing-dot" }),
      h("span", { className: "sn-typing-dot" })
    );
    if (props.compact) {
      return h("span", { className: "sn-typing", role: "img", "aria-label": "typing" }, dots);
    }
    if (names.length === 0) {
      return h("span", { className: "sn-typing", role: "status", "aria-live": "polite" });
    }
    var who =
      names.length === 1
        ? [h("strong", { key: 0 }, names[0]), " is typing…"]
        : names.length === 2
          ? [h("strong", { key: 0 }, names[0]), " and ", h("strong", { key: 1 }, names[1]), " are typing…"]
          : names.length === 3
            ? [h("strong", { key: 0 }, names[0]), ", ", h("strong", { key: 1 }, names[1]), " and ", h("strong", { key: 2 }, names[2]), " are typing…"]
            : ["Several people are typing…"];
    return h("span", { className: "sn-typing", role: "status", "aria-live": "polite" }, dots, h("span", null, who));
  }

  /* ---------- People ---------- */

  function initials(name) {
    var words = String(name || "").trim().split(/\s+/).filter(Boolean);
    return words
      .slice(0, 2)
      .map(function (w) {
        return Array.from(w)[0] || "";
      })
      .join("")
      .toUpperCase();
  }

  /** A steady fill per account: same hash as the app, at one perceived lightness so initials always read. */
  function colourFor(seed) {
    var hash = 0;
    for (var ch of String(seed || "")) {
      hash = (hash * 31 + ch.charCodeAt(0)) | 0;
    }
    return "oklch(50% 0.06 " + (Math.abs(hash) % 360) + ")";
  }

  function Avatar(props) {
    var status = props.status;
    var previous = useRef(status);
    var announceState = useState(false);
    var announce = announceState[0];

    // Only a change to online pings; members already online when the list loads stay still.
    useEffect(
      function () {
        var was = previous.current;
        previous.current = status;
        if (was !== undefined && was !== status && status === "online" && !prefersReducedMotion()) {
          announceState[1](true);
          var t = setTimeout(function () {
            announceState[1](false);
          }, 1900);
          return function () {
            clearTimeout(t);
          };
        }
      },
      [status]
    );

    var size = props.size || "md";
    return h(
      "span",
      {
        className: cx(
          "sn-avatar",
          size !== "md" && "sn-avatar-" + size,
          status === "offline" && "sn-avatar-offline",
          props.className
        ),
        style: props.ring ? { "--sn-ring": "var(--" + props.ring + ")" } : undefined
      },
      h(
        "span",
        { className: "sn-avatar-face", style: props.src ? undefined : { background: colourFor(props.seed || props.name) } },
        props.src ? h("img", { src: props.src, alt: "" }) : initials(props.name)
      ),
      status &&
        h("span", {
          className: cx("sn-status", status === "online" && "sn-status-online", announce && "sn-status-announce"),
          role: "img",
          "aria-label": status === "online" ? "Online" : "Offline"
        })
    );
  }

  var CollapsedContext = React.createContext(false);

  function UserPanel(props) {
    var collapsed = useContext(CollapsedContext);
    var status = props.status || "online";
    return h(
      "footer",
      { className: "sn-user-panel" },
      h(Avatar, { name: props.name, src: props.src, seed: props.seed, status: status }),
      h(
        "span",
        { className: "sn-user-panel-text sn-collapse-fade", "aria-hidden": collapsed ? "true" : undefined },
        h("span", { className: "sn-user-panel-name sn-truncate" }, props.name),
        h("span", { className: "sn-user-panel-status sn-truncate" }, props.statusText || (status === "online" ? "Online" : "Offline"))
      ),
      props.action && h("span", { className: "sn-collapse-fade" }, props.action)
    );
  }

  function Member(props) {
    var banned = !!props.banned;
    return h(
      "li",
      null,
      h(
        "button",
        {
          type: "button",
          className: cx("sn-member", !props.online && "sn-member-offline", banned && "sn-member-banned"),
          "aria-haspopup": "dialog",
          onClick: props.onClick
        },
        h(Avatar, {
          name: props.name,
          src: props.src,
          seed: props.seed || props.id,
          status: banned ? undefined : props.online ? "online" : "offline"
        }),
        h(
          "span",
          { className: "sn-member-name sn-truncate", style: props.color && props.online && !banned ? { color: props.color } : undefined },
          props.name
        ),
        props.typing && h(TypingIndicator, { compact: true }),
        banned &&
          h(
            Tooltip,
            { label: props.bannedText || "Banned", side: "left" },
            h("span", { className: "sn-member-mark", role: "img", "aria-label": "Banned" }, h(Icon, { name: "ban" }))
          )
      )
    );
  }

  function MemberList(props) {
    return h(
      "aside",
      { className: cx("sn-members", props.className), "aria-label": props.label || "Members" },
      h("div", { className: "sn-members-list" }, (props.groups || []).map(function (group) {
        if (!group.members || group.members.length === 0) return null;
        return h(
          "section",
          { key: group.title, className: "sn-member-group" },
          h("h2", { className: "sn-member-group-title" }, group.title + " — " + group.members.length),
          h(
            "ul",
            null,
            group.members.map(function (m) {
              return h(
                Member,
                Object.assign({ key: m.id }, m, {
                  online: m.online !== undefined ? m.online : group.online,
                  banned: m.banned !== undefined ? m.banned : group.banned,
                  onClick: props.onOpen && function () { props.onOpen(m.id); }
                })
              );
            })
          )
        );
      })),
      props.footer && h("div", { className: "sn-members-foot" }, props.footer)
    );
  }

  /* ---------- Navigation ---------- */

  function ServerRail(props) {
    return h("nav", { className: "sn-rail", "aria-label": props.label || "Servers" }, props.children);
  }

  function RailDivider() {
    return h("div", { className: "sn-rail-divider", role: "separator" });
  }

  function RailServer(props) {
    var action = props.variant === "action";
    return h(
      "div",
      {
        className: cx(
          "sn-rail-item",
          props.selected && "sn-rail-item-selected",
          props.unread && !props.selected && "sn-rail-item-unread"
        )
      },
      !action && h("span", { className: "sn-rail-pill", "aria-hidden": "true" }),
      h(
        Tooltip,
        { label: props.name, side: "right" },
        h(
          "button",
          {
            type: "button",
            className: cx("sn-rail-server", action && "sn-rail-server-action"),
            "aria-label": props.name,
            "aria-current": props.selected ? "true" : undefined,
            onClick: props.onClick
          },
          props.src ? h("img", { src: props.src, alt: "" }) : props.icon ? h(Icon, { name: props.icon }) : initials(props.name)
        )
      )
    );
  }

  function Sidebar(props) {
    var collapsed = !!props.collapsed;
    return h(
      CollapsedContext.Provider,
      { value: collapsed },
      h(
        "aside",
        { className: cx("sn-sidebar", collapsed && "sn-collapsed", props.className) },
        h(
          "header",
          { className: "sn-pane-header" },
          h("span", { className: "sn-pane-header-name sn-truncate sn-collapse-fade", "aria-hidden": collapsed ? "true" : undefined }, props.name),
          props.actions && h("span", { className: "sn-pane-header-actions sn-collapse-fade" }, props.actions),
          props.onToggle &&
            h(IconButton, {
              icon: collapsed ? "chevron-right" : "chevron-left",
              label: collapsed ? "Expand channels" : "Collapse channels",
              onClick: props.onToggle
            })
        ),
        props.children,
        props.footer
      )
    );
  }

  function ChannelList(props) {
    return h("nav", { className: "sn-channels", "aria-label": props.label || "Channels" }, props.children);
  }

  function ChannelItem(props) {
    var collapsed = useContext(CollapsedContext);
    return h(
      "button",
      {
        type: "button",
        className: cx("sn-channel", props.unread && !props.selected && "sn-channel-unread"),
        "aria-current": props.selected ? "page" : undefined,
        title: collapsed ? props.name : undefined,
        onClick: props.onClick
      },
      h(ChannelIcon, { type: props.type }),
      h("span", { className: "sn-channel-name sn-truncate sn-collapse-fade" }, props.name)
    );
  }

  function ChannelAction(props) {
    var collapsed = useContext(CollapsedContext);
    return h(
      "button",
      {
        type: "button",
        className: "sn-channel sn-channel-action",
        title: collapsed ? props.label : undefined,
        onClick: props.onClick
      },
      h(Icon, { name: props.icon || "plus" }),
      h("span", { className: "sn-channel-name sn-truncate sn-collapse-fade" }, props.label)
    );
  }

  function ChannelHeader(props) {
    return h(
      "header",
      { className: "sn-channel-header" },
      h(
        "div",
        { className: "sn-channel-heading" },
        h(ChannelIcon, { type: props.type }),
        h("strong", null, props.name),
        props.topic && h("span", { className: "sn-topic sn-truncate" }, props.topic)
      ),
      props.children && h("div", { className: "sn-header-actions" }, props.children)
    );
  }

  /* ---------- Messages ---------- */

  function MessageList(props) {
    return h(
      "div",
      { className: cx("sn-messages", props.className), role: "log", "aria-label": props.label || "Messages", "aria-busy": props.busy ? "true" : undefined },
      props.children
    );
  }

  function Message(props) {
    var head = props.head !== false;
    var state = props.state || "sent";
    return h(
      "div",
      {
        className: cx(
          "sn-message",
          head && "sn-message-head",
          props.isNew && "sn-message-new",
          props.mentioned && "sn-message-mentioned",
          state === "pending" && "sn-message-pending",
          state === "failed" && "sn-message-failed"
        ),
        role: "article",
        "aria-label": props.author
      },
      head &&
        (props.onAuthor
          ? h(
              "button",
              { type: "button", className: "sn-message-avatar", tabIndex: -1, "aria-hidden": "true", onClick: props.onAuthor },
              h(Avatar, { name: props.author, src: props.src, seed: props.seed || props.author, size: "lg" })
            )
          : h(Avatar, { name: props.author, src: props.src, seed: props.seed || props.author, size: "lg" })),
      head
        ? h(
            "div",
            { className: "sn-message-meta" },
            h(
              props.onAuthor ? "button" : "span",
              {
                type: props.onAuthor ? "button" : undefined,
                className: cx("sn-message-author", props.onAuthor && "sn-name-button"),
                "aria-haspopup": props.onAuthor ? "dialog" : undefined,
                style: props.color ? { color: props.color } : undefined,
                onClick: props.onAuthor
              },
              props.author
            ),
            props.time && h("time", { className: "sn-message-time", dateTime: props.dateTime }, props.time)
          )
        : props.shortTime && h("time", { className: "sn-message-gutter-time", dateTime: props.dateTime }, props.shortTime),
      h("div", { className: "sn-message-content" }, props.children),
      state === "failed" && h("span", { className: "sn-message-error" }, props.error || "Not sent. Try again."),
      props.actions && h("div", { className: "sn-message-actions" }, props.actions)
    );
  }

  function SystemMessage(props) {
    return h(
      "div",
      { className: cx("sn-message sn-message-system", props.isNew && "sn-message-new"), role: "article" },
      h(Icon, { name: "arrow-right" }),
      h("span", null, props.children),
      props.time && h("time", { className: "sn-message-time", dateTime: props.dateTime }, props.time)
    );
  }

  /* ---------- New messages ---------- */

  function NewMessagesDivider(props) {
    return h("div", { className: "sn-new-divider", role: "separator", "aria-label": "New messages" }, props.label || "New");
  }

  function UnreadBar(props) {
    var count = props.count;
    var what =
      count === undefined || count === null
        ? "New messages"
        : [h("strong", { key: 0 }, count), count === 1 ? " new message" : " new messages"];
    return h(
      "div",
      { className: "sn-unread-bar", role: "status" },
      h("span", { className: "sn-unread-bar-text" }, what, props.since && " since " + props.since),
      props.onMarkRead && h(Button, { variant: "link", size: "sm", onClick: props.onMarkRead }, "Mark as read"),
      h(Button, { size: "sm", onClick: props.onJump }, h(Icon, { name: "arrow-up" }), "Jump to first unread")
    );
  }

  /* ---------- Layout ---------- */

  function AppShell(props) {
    var membersOpen = props.membersOpen !== false;
    return h(
      "div",
      {
        className: cx(
          "sn-shell",
          props.collapsed && "sn-shell-collapsed",
          !membersOpen && "sn-shell-members-closed",
          props.className
        ),
        style: props.style
      },
      props.rail,
      props.sidebar,
      h(
        "main",
        { className: "sn-shell-content" },
        props.banner,
        props.header,
        h("div", { className: "sn-shell-body" }, props.children),
        props.footer
      ),
      h("div", { className: "sn-shell-members", inert: membersOpen ? undefined : "" }, props.members)
    );
  }

  var api = {
    AppShell: AppShell,
    ServerRail: ServerRail,
    RailServer: RailServer,
    RailDivider: RailDivider,
    Sidebar: Sidebar,
    ChannelList: ChannelList,
    ChannelItem: ChannelItem,
    ChannelAction: ChannelAction,
    ChannelIcon: ChannelIcon,
    ChannelHeader: ChannelHeader,
    MessageList: MessageList,
    Message: Message,
    SystemMessage: SystemMessage,
    NewMessagesDivider: NewMessagesDivider,
    UnreadBar: UnreadBar,
    Composer: Composer,
    TypingIndicator: TypingIndicator,
    MemberList: MemberList,
    Member: Member,
    UserPanel: UserPanel,
    Avatar: Avatar,
    Button: Button,
    IconButton: IconButton,
    Tabs: Tabs,
    Field: Field,
    TextArea: TextArea,
    Select: Select,
    Choice: Choice,
    ChoiceGroup: ChoiceGroup,
    Card: Card,
    Backdrop: Backdrop,
    Callout: Callout,
    Tag: Tag,
    Banner: Banner,
    Tooltip: Tooltip,
    Popover: Popover,
    Modal: Modal,
    InviteButton: InviteButton,
    InviteLink: InviteLink,
    Skeleton: Skeleton,
    Spinner: Spinner,
    Icon: Icon,
    initials: initials,
    colourFor: colourFor
  };

  window.Snatter = Object.assign(window.Snatter || {}, api);
})();
