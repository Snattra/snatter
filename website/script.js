/*
 * Small touches of life on the page. The page reads the same without them:
 * the render shows a conversation and the bot check shows its result.
 */

(function () {
  "use strict";

  /** Runs `start` once, the first time `element` is mostly on screen. */
  function whenSeen(element, start) {
    if (!("IntersectionObserver" in window)) {
      start();
      return;
    }
    var observer = new IntersectionObserver(function (entries) {
      if (entries.some(function (entry) { return entry.isIntersecting; })) {
        observer.disconnect();
        start();
      }
    }, { threshold: 0.5 });
    observer.observe(element);
  }

  function el(tag, className, children) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    (children || []).forEach(function (child) {
      node.append(child);
    });
    return node;
  }

  /* ---------- The app render: a short evening on the server ---------- */

  var messages = document.querySelector('[data-demo="messages"]');
  var typing = document.querySelector('[data-demo="typing"]');
  var online = document.querySelector('[data-demo="online"]');
  var onlineTitle = document.querySelector('[data-demo="online-title"]');
  var offlineTitle = document.querySelector('[data-demo="offline-title"]');
  var eider = document.querySelector('[data-member="eider"]');

  var people = {
    Mallard: { avatar: "av-mallard", role: true },
    Teal: { avatar: "av-teal" },
    Wigeon: { avatar: "av-wigeon" },
    Eider: { avatar: "av-eider" }
  };
  var lastAuthor = "Wigeon";

  /** "Teal is typing…", "Mallard and Wigeon are typing…", as the app writes it. */
  function setTyping(names) {
    typing.replaceChildren();
    if (names.length === 0) return;
    var dots = el("span", "typing-dots", [el("span", "typing-dot"), el("span", "typing-dot"), el("span", "typing-dot")]);
    var line = el("span");
    names.forEach(function (name, i) {
      if (i > 0) line.append(" and ");
      line.append(el("strong", null, [name]));
    });
    line.append(names.length === 1 ? " is typing…" : " are typing…");
    typing.append(dots, line);
  }

  function post(author, text, time) {
    var person = people[author];
    var row;
    if (author === lastAuthor) {
      row = el("div", "msg msg-new", [el("p", null, [text])]);
    } else {
      var name = el("strong", "msg-author" + (person.role ? " msg-author-role" : ""), [author]);
      row = el("div", "msg msg-head msg-new", [
        el("span", "avatar " + person.avatar, [author.charAt(0)]),
        el("div", "msg-meta", [name, el("span", "msg-time", ["Today at " + time])]),
        el("p", null, [text])
      ]);
    }
    lastAuthor = author;
    messages.append(row);
    // Keep the list short; the oldest rows are long out of sight by now.
    while (messages.children.length > 12) messages.firstElementChild.remove();
  }

  /** Eider comes online: the row moves under Online and the dot pings twice. */
  function comeOnline() {
    eider.remove();
    eider.classList.remove("is-offline");
    eider.classList.add("is-new");
    var status = eider.querySelector(".status");
    status.classList.add("status-online", "status-announce");
    online.append(eider);
    onlineTitle.textContent = "Online — 4";
    offlineTitle.textContent = "Offline — 1";
  }

  var evening = [
    [1200, comeOnline],
    [2200, function () { setTyping(["Eider"]); }],
    [4400, function () { setTyping([]); post("Eider", "Just got in. Room for one more?", "18:36"); }],
    [5400, function () { setTyping(["Teal"]); }],
    [7000, function () { setTyping([]); post("Teal", "Always. Squad up in five.", "18:36"); }],
    [8000, function () { setTyping(["Mallard", "Wigeon"]); }],
    [9800, function () { setTyping(["Wigeon"]); post("Mallard", "Joining now.", "18:37"); }],
    [11400, function () { setTyping([]); post("Wigeon", "Same. It really does sound better.", "18:37"); }]
  ];

  if (messages && typing && online && eider) {
    whenSeen(messages, function () {
      evening.forEach(function (step) {
        setTimeout(step[1], step[0]);
      });
    });
  }

  /* ---------- The bot check ---------- */

  var botCheck = document.querySelector('[data-demo="bot-check"]');
  if (botCheck) {
    botCheck.classList.add("is-busy");
    whenSeen(botCheck, function () {
      setTimeout(function () {
        botCheck.classList.remove("is-busy");
      }, 1800);
    });
  }
})();
