/* Shared by the launcher, the game page and the second-screen dashboard:
   language, gamepad bindings and touch-control layout (+ its renderer). */
"use strict";
(function (G) {
  const store = {
    get(k, d) { try { const v = localStorage.getItem("ml." + k); return v === null ? d : v; } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem("ml." + k, v); } catch (e) {} },
    del(k) { try { localStorage.removeItem("ml." + k); } catch (e) {} }
  };

  /* ------------------------------------------------------------------ language */
  function lang() {
    const l = store.get("lang", "auto");
    if (l === "fr" || l === "en") return l;
    return /^fr/i.test(navigator.language || "") ? "fr" : "en";
  }
  const STR = {
    fr: {
      // actions
      "act.cursor": "Afficher le curseur (boutons du haut)", "act.pause": "Pause",
      "act.fuel": "Réservoir de secours", "act.repair": "Nanobots de réparation", "act.dynamite": "Dynamite",
      "act.c4": "Explosif plastique (C4)", "act.quantum": "Téléporteur quantique", "act.matter": "Transmetteur de matière",
      "act.inventory": "Inventaire", "act.click": "Cliquer", "act.back": "Retour / fermer",
      "act.scrollUp": "Défiler vers le haut", "act.scrollDown": "Défiler vers le bas",
      "act.help": "Aide en jeu", "act.display": "Changer le mode d'affichage", "act.touch": "Afficher / masquer le tactile",
      "grp.drive": "Pilotage", "grp.menu": "Menus du jeu", "grp.global": "Partout",
      // touch labels
      "tb.f": "Réservoir", "tb.r": "Réparer", "tb.x": "Dynamite", "tb.c": "C4", "tb.q": "Téléport.", "tb.m": "Transm.",
      "tb.i": "Inventaire", "tb.p": "Pause", "tb.move": "Déplacement",
      // game overlay
      "g.badge": "A : cliquer · B : retour / fermer", "g.hint": "A : curseur · Start : aide",
      "g.saved": "Partie sauvegardée sur l'appareil", "g.typing": "Saisie : ",
      "g.mode.original": "Original 4:3", "g.mode.etire": "Plein écran étiré 16:9", "g.mode.elargi": "Élargi (plus de décor)",
      "g.display": "Affichage : ", "g.helpTitle": "Commandes", "g.helpMove": "Croix / stick gauche",
      "g.helpMoveDrive": "Piloter et forer", "g.helpMoveMenu": "Sauter au bouton voisin (maintenir = répéter)",
      "g.helpAuto": "Dans les magasins et écrans du jeu, le curseur se place tout seul sur le bouton le plus utile.",
      "g.helpFoot": "Commandes modifiables dans le lanceur (Commandes). Appuyez sur Start ou B pour fermer.",
      "g.backHint": "Appuyez encore sur Retour pour revenir au menu",
      // dashboard
      "d.cash": "Argent", "d.depth": "Profondeur", "d.score": "Score", "d.fuel": "Carburant", "d.hull": "Coque", "d.bay": "Soute",
      "d.cargo": "Cargaison", "d.places": "places", "d.items": "Objets", "d.gear": "Équipement", "d.empty": "Soute vide",
      "d.value": "Valeur estimée", "d.lowFuel": "Carburant bas !", "d.damaged": "Coque endommagée !", "d.full": "Soute pleine",
      "d.wait": "Les informations s'afficheront dès le début de la partie.",
      "d.drill": "Foreuse", "d.engine": "Moteur", "d.tank": "Réservoir", "d.radiator": "Radiateur",
      "d.i0": "Réservoir", "d.i1": "Nanobots", "d.i2": "Dynamite", "d.i3": "Explosif C4", "d.i4": "Téléporteur", "d.i5": "Transmetteur", "d.i6": "Vers le noyau"
    },
    en: {
      "act.cursor": "Show the cursor (top buttons)", "act.pause": "Pause",
      "act.fuel": "Reserve fuel tank", "act.repair": "Hull repair nanobots", "act.dynamite": "Dynamite",
      "act.c4": "Plastic explosives (C4)", "act.quantum": "Quantum teleporter", "act.matter": "Matter transmitter",
      "act.inventory": "Inventory", "act.click": "Click", "act.back": "Back / close",
      "act.scrollUp": "Scroll up", "act.scrollDown": "Scroll down",
      "act.help": "In-game help", "act.display": "Change display mode", "act.touch": "Show / hide touch controls",
      "grp.drive": "Driving", "grp.menu": "Game menus", "grp.global": "Anywhere",
      "tb.f": "Fuel", "tb.r": "Repair", "tb.x": "Dynamite", "tb.c": "C4", "tb.q": "Teleport", "tb.m": "Transmit",
      "tb.i": "Inventory", "tb.p": "Pause", "tb.move": "Movement",
      "g.badge": "A: click · B: back / close", "g.hint": "A: cursor · Start: help",
      "g.saved": "Game saved on this device", "g.typing": "Typing: ",
      "g.mode.original": "Original 4:3", "g.mode.etire": "Stretched 16:9", "g.mode.elargi": "Wide (more scenery)",
      "g.display": "Display: ", "g.helpTitle": "Controls", "g.helpMove": "D-pad / left stick",
      "g.helpMoveDrive": "Drive and drill", "g.helpMoveMenu": "Jump to the next button (hold = repeat)",
      "g.helpAuto": "In shops and game screens, the cursor jumps to the most useful button by itself.",
      "g.helpFoot": "Controls can be changed in the launcher (Controls). Press Start or B to close.",
      "g.backHint": "Press Back again to return to the menu",
      "d.cash": "Cash", "d.depth": "Depth", "d.score": "Score", "d.fuel": "Fuel", "d.hull": "Hull", "d.bay": "Cargo bay",
      "d.cargo": "Cargo", "d.places": "slots", "d.items": "Items", "d.gear": "Equipment", "d.empty": "Cargo bay empty",
      "d.value": "Estimated value", "d.lowFuel": "Low fuel!", "d.damaged": "Hull damaged!", "d.full": "Cargo bay full",
      "d.wait": "Information will appear once the game starts.",
      "d.drill": "Drill", "d.engine": "Engine", "d.tank": "Fuel tank", "d.radiator": "Radiator",
      "d.i0": "Fuel tank", "d.i1": "Nanobots", "d.i2": "Dynamite", "d.i3": "C4", "d.i4": "Teleporter", "d.i5": "Transmitter", "d.i6": "Core teleporter"
    }
  };
  function t(k) { const L = lang(); return (STR[L] && STR[L][k]) || STR.fr[k] || k; }

  /* ------------------------------------------------------------------ gamepad bindings */
  const BUTTONS = ["A", "B", "X", "Y", "L1", "R1", "L2", "R2", "L3", "R3", "START", "SELECT"];
  // group: drive (while driving), menu (cursor shown), global (always). key = game key sent.
  const ACTIONS = [
    {id: "cursor", grp: "drive", def: "A"}, {id: "pause", grp: "drive", def: "B", key: "p"},
    {id: "fuel", grp: "drive", def: "Y", key: "f"}, {id: "repair", grp: "drive", def: "R1", key: "r"},
    {id: "dynamite", grp: "drive", def: "X", key: "x"}, {id: "c4", grp: "drive", def: "L1", key: "c"},
    {id: "quantum", grp: "drive", def: "L2", key: "q"}, {id: "matter", grp: "drive", def: "R2", key: "m"},
    {id: "inventory", grp: "drive", def: "SELECT", key: "i"},
    {id: "click", grp: "menu", def: "A"}, {id: "back", grp: "menu", def: "B"},
    {id: "scrollUp", grp: "menu", def: "L1"}, {id: "scrollDown", grp: "menu", def: "R1"},
    {id: "help", grp: "global", def: "START"}, {id: "display", grp: "global", def: "L3"}, {id: "touch", grp: "global", def: "R3"}
  ];
  function loadBindings() {
    let b = {}; try { b = JSON.parse(store.get("bind", "{}")) || {}; } catch (e) {}
    const out = {};
    for (const a of ACTIONS) out[a.id] = (b[a.id] === "" || BUTTONS.includes(b[a.id])) ? b[a.id] : a.def;
    return out;
  }
  function conflicts(g1, g2) { return g1 === g2 || g1 === "global" || g2 === "global"; }
  /* Assign a button to an action; an action of an overlapping group that had it gets the old one (swap). */
  function bindButton(actionId, button) {
    const b = loadBindings(), a = ACTIONS.find(x => x.id === actionId), old = b[actionId];
    for (const o of ACTIONS) if (o.id !== actionId && b[o.id] === button && conflicts(o.grp, a.grp)) b[o.id] = old;
    b[actionId] = button;
    store.set("bind", JSON.stringify(b));
    return b;
  }
  function resetBindings() { store.del("bind"); }

  /* ------------------------------------------------------------------ touch layout */
  /* The touch controls are a virtual gamepad laid out like the AYN Thor: left stick above the
     d-pad, A/B/X/Y diamond above a right stick, shoulder buttons in the top corners, Start /
     Select at the bottom. Every touch button goes through the same path as the real gamepad,
     so the cursor, the menus and the custom bindings behave exactly the same. */
  const TOUCH_IDS = ["stick", "dpad", "A", "B", "X", "Y", "rstick", "L1", "L2", "R1", "R2", "SELECT", "START"];
  const ACT_ICON = {cursor: "➤", pause: "⏸", fuel: "⛽", repair: "🔧", dynamite: "🧨", c4: "💥", quantum: "✦", matter: "⌂",
                    inventory: "🎒", help: "?", display: "⛶", touch: "👆"};
  const BTN_COLOR = {A: "#e5484d", B: "#f5c518", X: "#3e8ef7", Y: "#46a758"};
  /* Default layout, built for the current screen shape: each element is anchored to the left or
     right edge (or the centre) at a distance measured in screen heights, so the controls keep the
     same shape on a 16:9 handheld and on a 20:9 phone. */
  function defaultLayout(aspect) {
    aspect = aspect || (innerWidth / innerHeight) || 16 / 9;
    const Lx = d => d / aspect, Rx = d => 1 - d / aspect, Cx = d => 0.5 + d / aspect;
    const fx = 0.24, fy = 0.40, o = 0.115, sz = 0.12;     // face-button diamond (Thor: A right, B bottom, X top, Y left)
    return {v: 2, opacity: 0.5, labels: "buttons", scale: 1,
      stick:  {x: Lx(0.21), y: 0.42, s: 0.30, show: true},
      dpad:   {x: Lx(0.21), y: 0.79, s: 0.33, show: true},
      X: {x: Rx(fx), y: fy - o, s: sz, show: true}, A: {x: Rx(fx - o), y: fy, s: sz, show: true},
      B: {x: Rx(fx), y: fy + o, s: sz, show: true}, Y: {x: Rx(fx + o), y: fy, s: sz, show: true},
      rstick: {x: Rx(0.24), y: 0.80, s: 0.26, show: true},
      L2: {x: Lx(0.10), y: 0.075, s: 0.10, show: true}, L1: {x: Lx(0.275), y: 0.075, s: 0.10, show: true},
      R1: {x: Rx(0.275), y: 0.075, s: 0.10, show: true}, R2: {x: Rx(0.10), y: 0.075, s: 0.10, show: true},
      SELECT: {x: Cx(-0.13), y: 0.93, s: 0.085, show: true}, START: {x: Cx(0.13), y: 0.93, s: 0.085, show: true}};
  }
  function loadLayout() {
    const d = defaultLayout(); let l = {};
    try { l = JSON.parse(store.get("touchLayout", "{}")) || {}; } catch (e) {}
    if (l.v !== 2) return d;                                   // old layout format: start from the new default
    for (const k in d) if (l[k] !== undefined) d[k] = typeof d[k] === "object" ? Object.assign(d[k], l[k]) : l[k];
    return d;
  }
  function saveLayout(L) { L.v = 2; store.set("touchLayout", JSON.stringify(L)); }
  /* Label of a touch button: its own name, or what it does while driving (with your bindings). */
  function touchName(id) {
    if (id === "stick") return lang() === "fr" ? "Joystick gauche" : "Left stick";
    if (id === "rstick") return lang() === "fr" ? "Joystick droit (curseur)" : "Right stick (cursor)";
    if (id === "dpad") return lang() === "fr" ? "Croix" : "D-pad";
    const a = driveAction(id);
    return id + (a ? " — " + t("act." + a) : "");
  }
  function driveAction(btn) {
    const B = loadBindings();
    for (const a of ACTIONS) if (a.grp !== "menu" && B[a.id] === btn) return a.id;
    return null;
  }

  /* Builds the virtual gamepad into `root` (positions relative to the root size). */
  function renderTouch(root, L, opts) {
    opts = opts || {};
    root.innerHTML = "";
    const W = root.clientWidth || innerWidth, H = root.clientHeight || innerHeight, els = {};
    const place = (el, c, w, h) => {
      el.style.position = "absolute"; el.style.width = w + "px"; el.style.height = h + "px";
      el.style.left = (c.x * W - w / 2) + "px"; el.style.top = (c.y * H - h / 2) + "px";
    };
    const mk = (cls, html) => { const e = document.createElement("div"); e.className = cls; if (html != null) e.innerHTML = html; return e; };
    root.style.setProperty("--tc-op", L.opacity);
    for (const id of TOUCH_IDS) {
      const c = L[id]; if (!c || (!c.show && !opts.editor)) continue;
      const size = c.s * H * (L.scale || 1);
      let el;
      if (id === "stick" || id === "rstick") {
        el = mk("tc-move tc-stick", '<div class="tc-knob"></div>');
        if (id === "rstick") el.classList.add("tc-rstick");
        place(el, c, size, size);
      } else if (id === "dpad") {
        el = mk("tc-move tc-dpad", '<div class="tc-b tc-up" data-btn="UP">▲</div><div class="tc-b tc-left" data-btn="LEFT">◀</div>' +
                                   '<div class="tc-b tc-right" data-btn="RIGHT">▶</div><div class="tc-b tc-down" data-btn="DOWN">▼</div>');
        el.style.fontSize = (size * 0.15) + "px";
        place(el, c, size, size);
      } else {
        const shoulder = /^[LR][12]$/.test(id), small = id === "START" || id === "SELECT";
        const act = driveAction(id);
        let label = id === "SELECT" ? "SEL" : id;
        if (L.labels === "icons" && act) label = ACT_ICON[act] || label;
        if (L.labels === "names" && act) label = `<b>${id === "SELECT" ? "SEL" : id}</b><small>${t("act." + act).split(" (")[0]}</small>`;
        el = mk("tc-btn tc-b" + (shoulder ? " tc-sh" : "") + (small ? " tc-sm" : "") + (L.labels === "names" ? " tc-name" : ""), label);
        el.dataset.btn = id;
        if (BTN_COLOR[id] && L.labels !== "names") el.style.color = BTN_COLOR[id];
        const w = shoulder || small ? size * 1.6 : size;
        el.style.fontSize = Math.max(9, size * (L.labels === "names" ? 0.24 : small ? 0.3 : 0.42)) + "px";
        place(el, c, w, size);
      }
      el.dataset.id = id;
      if (!c.show) el.classList.add("tc-hidden");
      root.appendChild(el); els[id] = el;
    }
    return els;
  }
  const TOUCH_CSS = `
    .tc-move,.tc-btn{touch-action:none;-webkit-user-select:none;user-select:none;opacity:var(--tc-op,.5)}
    .tc-b{display:flex;align-items:center;justify-content:center;box-sizing:border-box;color:#fff;font-weight:800;
      background:rgba(20,24,30,.45);border:2px solid rgba(255,255,255,.65);font-family:system-ui,sans-serif}
    .tc-btn{border-radius:50%;text-align:center;line-height:1.05;flex-direction:column}
    .tc-btn.tc-sh{border-radius:12px 12px 8px 8px}.tc-btn.tc-sm{border-radius:999px}
    .tc-btn.tc-name small{font-weight:600;font-size:.72em;opacity:.9;max-width:95%;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
    .tc-b.on{background:rgba(255,190,50,.7)}
    .tc-dpad .tc-b{position:absolute;width:35%;height:35%;border-radius:22%;font-size:inherit}
    .tc-up{left:32.5%;top:0}.tc-down{left:32.5%;bottom:0}.tc-left{left:0;top:32.5%}.tc-right{right:0;top:32.5%}
    .tc-stick{border-radius:50%;background:rgba(20,24,30,.35);border:2px solid rgba(255,255,255,.55);box-sizing:border-box}
    .tc-knob{position:absolute;left:28%;top:28%;width:44%;height:44%;border-radius:50%;background:rgba(255,255,255,.55);
      border:2px solid rgba(255,255,255,.9);box-sizing:border-box;transition:transform .05s;pointer-events:none}
    .tc-hidden{opacity:.18 !important;filter:grayscale(1)}`;

  G.ML = {store, lang, t, STR, BUTTONS, ACTIONS, loadBindings, bindButton, resetBindings,
          TOUCH_IDS, touchName, defaultLayout, loadLayout, saveLayout, renderTouch, TOUCH_CSS};
})(window);
