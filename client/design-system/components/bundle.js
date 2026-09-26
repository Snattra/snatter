/* @ds-bundle: {"format":4,"namespace":"Snatter","components":[{"name":"AppShell"},{"name":"ServerRail"},{"name":"Sidebar"},{"name":"ChannelHeader"},{"name":"Message"},{"name":"Composer"},{"name":"TypingIndicator"},{"name":"MemberList"},{"name":"UserPanel"},{"name":"Avatar"},{"name":"Button"},{"name":"IconButton"},{"name":"Tabs"},{"name":"Field"},{"name":"Card"},{"name":"Callout"},{"name":"Banner"},{"name":"Tooltip"},{"name":"Skeleton"},{"name":"Icon"}]} */
(function () {
  "use strict";
  var React = window.React;
  var h = React.createElement;
  var useState = React.useState;
  var useEffect = React.useEffect;
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
    "arrow-right": "M4 11h12.2l-5.6-5.6L12 4l8 8-8 8-1.4-1.4 5.6-5.6H4z"
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

  function Field(props) {
    var id = useStableId(props.id);
    var inputRef = useRef(null);
    var rest = omit(props, ["label", "hint", "error", "optional", "className", "id"]);
    var describedBy = props.error ? id + "-error" : props.hint ? id + "-hint" : undefined;

    // A new error shakes the field again, even when it was already invalid.
    useEffect(
      function () {
        var el = inputRef.current;
        if (!props.error || !el) return;
        el.style.animation = "none";
        void el.offsetWidth;
        el.style.animation = "";
      },
      [props.error]
    );

    return h(
      "div",
      { className: cx("sn-field", props.error && "sn-field-invalid", props.className) },
      h(
        "label",
        { htmlFor: id },
        props.label,
        props.optional && h("span", { className: "sn-field-optional" }, " (optional)")
      ),
      h(
        "input",
        Object.assign({}, rest, {
          ref: inputRef,
          id: id,
          className: "sn-input",
          "aria-invalid": props.error ? true : undefined,
          "aria-describedby": describedBy
        })
      ),
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

  function Tooltip(props) {
    var id = useStableId();
    var child = React.Children.only(props.children);
    return h(
      "span",
      { className: "sn-tooltip-anchor" },
      React.cloneElement(child, { "aria-describedby": id, title: undefined }),
      h("span", { id: id, role: "tooltip", className: "sn-tooltip sn-tooltip-" + (props.side || "top") }, props.label)
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
    return h(
      "li",
      { className: cx("sn-member", !props.online && "sn-member-offline") },
      h(Avatar, { name: props.name, src: props.src, seed: props.seed || props.id, status: props.online ? "online" : "offline" }),
      h("span", { className: "sn-member-name sn-truncate", style: props.color && props.online ? { color: props.color } : undefined }, props.name),
      props.typing && h(TypingIndicator, { compact: true })
    );
  }

  function MemberList(props) {
    return h(
      "aside",
      { className: cx("sn-members", props.className), "aria-label": props.label || "Members" },
      (props.groups || []).map(function (group) {
        if (!group.members || group.members.length === 0) return null;
        return h(
          "section",
          { key: group.title, className: "sn-member-group" },
          h("h2", { className: "sn-member-group-title" }, group.title + " — " + group.members.length),
          h(
            "ul",
            null,
            group.members.map(function (m) {
              return h(Member, Object.assign({ key: m.id }, m, { online: m.online !== undefined ? m.online : group.online }));
            })
          )
        );
      })
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
          h("span", { className: "sn-truncate sn-collapse-fade", "aria-hidden": collapsed ? "true" : undefined }, props.name),
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
      head && h(Avatar, { name: props.author, src: props.src, seed: props.seed || props.author, size: "lg" }),
      head
        ? h(
            "div",
            { className: "sn-message-meta" },
            h("span", { className: "sn-message-author", style: props.color ? { color: props.color } : undefined }, props.author),
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
    ChannelIcon: ChannelIcon,
    ChannelHeader: ChannelHeader,
    MessageList: MessageList,
    Message: Message,
    SystemMessage: SystemMessage,
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
    Card: Card,
    Backdrop: Backdrop,
    Callout: Callout,
    Banner: Banner,
    Tooltip: Tooltip,
    Skeleton: Skeleton,
    Spinner: Spinner,
    Icon: Icon,
    initials: initials,
    colourFor: colourFor
  };

  window.Snatter = Object.assign(window.Snatter || {}, api);
})();
