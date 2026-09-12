/* Handgeschriebene SVG-Charts fuer wahlen.fherrmann.com.
 *
 * Keine Bibliothek — der ganze Punkt der Seite ist, dass die Darstellung
 * genau so aussieht, wie sie soll, und dass nichts nachgeladen werden muss.
 *
 * Barrierefreiheit: Parteifarben sind semantisch vorgegeben und liegen bei
 * Rot-Gruen-Sehschwaeche zu dicht beieinander (SPD/Gruene/FDP). Farbe ist hier
 * deshalb NICHT der Identitaetskanal — jede Serie traegt zusaetzlich eine
 * Direktbeschriftung am Kurvenende, einen Legendeneintrag mit Text und das
 * Kuerzel im Tooltip, und es gibt eine Tabellenansicht mit denselben Zahlen.
 */

const NS = 'http://www.w3.org/2000/svg';

const MONTHS = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun',
                'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];

export const darkMode = () =>
  window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;

export const partyColor = (party) =>
  (darkMode() ? party.colorDark : party.colorLight) || '#8A8F98';

/* Sehr dunkle Farben - die Union ist seit 2026-09-12 auch im Dark Mode schwarz -
 * gehen auf dunklem Grund unter. Sie bekommen deshalb einen hellen Saum: Linien
 * ein Halo darunter, Punkte und Farbfelder eine Hairline. Entscheidend ist die
 * relative Leuchtdichte der Farbe, nicht der Parteiname. */
export const HALO = 'rgba(255, 255, 255, 0.55)';
export function needsHalo(color) {
  if (!darkMode()) return false;
  const m = /^#([0-9a-f]{6})$/i.exec(String(color).trim());
  if (!m) return false;
  const n = parseInt(m[1], 16);
  const lin = (c) => { const v = c / 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; };
  const lum = 0.2126 * lin(n >> 16) + 0.7152 * lin((n >> 8) & 255) + 0.0722 * lin(n & 255);
  return lum < 0.05;
}
export const swatchStyle = (color) =>
  `background:${color}` + (needsHalo(color) ? `;box-shadow:inset 0 0 0 1px ${HALO}` : '');

export function fmt(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return '–';
  return value.toFixed(digits).replace('.', ',');
}

export function fmtSigned(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return '';
  const s = value.toFixed(digits).replace('.', ',');
  return value > 0 ? `+${s}` : s;
}

export function fmtDate(iso, style = 'short') {
  const d = new Date(iso + 'T12:00:00');
  if (style === 'long') {
    return d.toLocaleDateString('de-DE', { day: 'numeric', month: 'long', year: 'numeric' });
  }
  return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: '2-digit' });
}

function el(name, attrs = {}, parent = null) {
  const node = document.createElementNS(NS, name);
  for (const [k, v] of Object.entries(attrs)) {
    if (v !== null && v !== undefined) node.setAttribute(k, v);
  }
  if (parent) parent.appendChild(node);
  return node;
}

function cssVar(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

/* Achsenticks auf runde Zahlen — sie tragen die Werte, die nicht direkt
 * beschriftet sind, also duerfen sie nicht krumm sein. */
function niceTicks(min, max, count = 5) {
  const span = Math.max(max - min, 1e-9);
  const raw = span / count;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const norm = raw / mag;
  const step = (norm >= 5 ? 10 : norm >= 2 ? 5 : norm >= 1 ? 2 : 1) * mag;
  const ticks = [];
  for (let t = Math.ceil(min / step) * step; t <= max + 1e-9; t += step) {
    ticks.push(Math.round(t * 1e6) / 1e6);
  }
  return ticks;
}

const dayOf = (iso) => Date.parse(iso + 'T12:00:00') / 86400000;

/* --------------------------------------------------------------- Trendchart */

/**
 * @param {HTMLElement} host       Container (position: relative)
 * @param {Object} data            { dates, series, parties, polls, markers }
 * @param {Object} options         { showRaw, patterns, hidden, threshold, compact }
 */
export function trendChart(host, data, options = {}) {
  // Die Daten liegen im State, damit ein Zeitraumwechsel nur `update({ data })`
  // ist und keinen zweiten Chart (samt ResizeObserver und Tooltip) anlegt.
  const state = {
    data,
    showRaw: options.showRaw ?? false,
    patterns: options.patterns ?? false,
    hidden: options.hidden ?? new Set(),
    threshold: options.threshold ?? null,
    compact: options.compact ?? false,
  };

  let svg = host.querySelector('svg.chart');
  if (!svg) {
    svg = el('svg', { class: `chart ${state.compact ? 'chart-md' : 'chart-lg'}` });
    host.appendChild(svg);
  }

  let tooltip = host.querySelector('.tooltip');
  if (!tooltip) {
    tooltip = document.createElement('div');
    tooltip.className = 'tooltip';
    tooltip.setAttribute('role', 'status');
    host.appendChild(tooltip);
  }

  const render = () => draw(svg, tooltip, host, state.data, state);
  render();

  const ro = new ResizeObserver(() => render());
  ro.observe(host);
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', render);

  return {
    update(patch) { Object.assign(state, patch); render(); },
    state,
  };
}

function draw(svg, tooltip, host, data, state) {
  const width = Math.max(host.clientWidth || 640, 280);
  const height = svg.clientHeight || (state.compact ? 260 : 380);
  const narrow = width < 560;

  // Rechts Platz fuer die Direktbeschriftungen, unten fuer das Achsenband.
  const pad = {
    top: 14,
    right: narrow ? 44 : 62,
    bottom: 26,
    left: 34,
  };

  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.replaceChildren();

  const visible = data.series.filter((s) => !state.hidden.has(s.partyId));
  const partyById = new Map(data.parties.map((p) => [p.id, p]));

  const plotW = width - pad.left - pad.right;
  const plotH = height - pad.top - pad.bottom;
  if (plotW <= 0 || plotH <= 0 || !data.dates.length) return;

  // --- Skalen -------------------------------------------------------------
  const xs = data.dates.map(dayOf);
  const x0 = xs[0];
  const x1 = xs[xs.length - 1] || x0 + 1;
  const X = (day) => pad.left + ((day - x0) / Math.max(x1 - x0, 1e-9)) * plotW;

  let lo = Infinity;
  let hi = -Infinity;
  for (const s of visible) {
    for (const v of s.values) {
      if (v === null) continue;
      if (v < lo) lo = v;
      if (v > hi) hi = v;
    }
  }
  if (state.showRaw && data.polls) {
    for (const poll of data.polls) {
      for (const s of visible) {
        const v = poll.results[s.partyId];
        if (v === undefined) continue;
        if (v < lo) lo = v;
        if (v > hi) hi = v;
      }
    }
  }
  if (!Number.isFinite(lo)) { lo = 0; hi = 40; }
  const headroom = Math.max((hi - lo) * 0.12, 1.5);
  const yMin = Math.max(0, Math.floor((lo - headroom) / 5) * 5);
  const yMax = Math.ceil((hi + headroom) / 5) * 5;
  const Y = (v) => pad.top + plotH - ((v - yMin) / Math.max(yMax - yMin, 1e-9)) * plotH;

  const gridColor = cssVar('--grid', '#ececee');
  const borderColor = cssVar('--border', '#e5e5e7');
  const faint = cssVar('--faint', '#8e8e93');
  const surface = cssVar('--surface', '#ffffff');

  // --- Gitter: durchgezogene Haarlinien, eine Stufe neben der Flaeche ------
  const gridG = el('g', {}, svg);
  for (const t of niceTicks(yMin, yMax, 5)) {
    const y = Y(t);
    el('line', { x1: pad.left, x2: pad.left + plotW, y1: y, y2: y,
                 stroke: gridColor, 'stroke-width': 1 }, gridG);
    const label = el('text', { x: pad.left - 7, y: y + 3.5, 'text-anchor': 'end',
                               class: 'axis-label', fill: faint }, gridG);
    label.textContent = t;
  }

  // Sperrklausel: nur die Linie. Genau in diesem Bereich draengen sich die
  // kleinen Parteien — jede Inline-Beschriftung kollidiert dort. Erklaert wird
  // sie in der Bildunterschrift unter dem Chart.
  if (state.threshold && state.threshold > yMin && state.threshold < yMax) {
    const y = Y(state.threshold);
    el('line', { x1: pad.left, x2: pad.left + plotW, y1: y, y2: y,
                 stroke: faint, 'stroke-width': 1, opacity: 0.5 }, gridG);
  }

  // --- Zeitachse ----------------------------------------------------------
  el('line', { x1: pad.left, x2: pad.left + plotW, y1: pad.top + plotH,
               y2: pad.top + plotH, stroke: borderColor, 'stroke-width': 1 }, gridG);

  for (const tick of timeTicks(data.dates, narrow ? 4 : Math.round(plotW / 78))) {
    const x = X(dayOf(tick.iso));
    if (x < pad.left - 1 || x > pad.left + plotW + 1) continue;
    const t = el('text', { x, y: height - 8, 'text-anchor': 'middle',
                           class: 'axis-label', fill: faint }, gridG);
    t.textContent = tick.label;
  }

  // --- Wahltermine als Marken --------------------------------------------
  for (const marker of data.markers || []) {
    const day = dayOf(marker.date);
    if (day < x0 || day > x1) continue;
    const x = X(day);
    el('line', { x1: x, x2: x, y1: pad.top, y2: pad.top + plotH,
                 stroke: faint, 'stroke-width': 1, 'stroke-dasharray': '2 3',
                 opacity: 0.7 }, gridG);
    const t = el('text', { x: x + 4, y: pad.top + 10, class: 'marker-label', fill: faint }, gridG);
    t.textContent = marker.label;
  }

  // --- Rohwerte (optional) ------------------------------------------------
  if (state.showRaw && data.polls) {
    const dotsG = el('g', {}, svg);
    for (const s of visible) {
      const color = partyColor(partyById.get(s.partyId) || {});
      for (const poll of data.polls) {
        const v = poll.results[s.partyId];
        if (v === undefined) continue;
        const day = dayOf(poll.effective);
        if (day < x0 || day > x1) continue;
        el('circle', { cx: X(day), cy: Y(v), r: 2.6, fill: color,
                       class: needsHalo(color) ? 'series-dot dot-halo' : 'series-dot' }, dotsG);
      }
    }
  }

  // --- Linien -------------------------------------------------------------
  //
  // Wo ueber laengere Zeit gar nicht befragt wurde, liefert die Glaettung
  // bewusst keinen Wert. Die Luecke einfach offen zu lassen sieht aber wie ein
  // Fehler aus (und genau so wurde sie auch gemeldet). Deshalb wird ueber die
  // Luecke eine duenne gestrichelte Bruecke gezogen: der Verlauf bleibt als
  // Linie lesbar, und man sieht trotzdem, wo interpoliert statt gemessen ist.
  const linesG = el('g', {}, svg);
  const ends = [];
  let hasGaps = false;

  for (const s of visible) {
    const party = partyById.get(s.partyId) || {};
    const color = partyColor(party);
    const halo = needsHalo(color);

    // Zusammenhaengende Abschnitte mit Daten sammeln.
    const runs = [];
    let run = null;
    s.values.forEach((v, i) => {
      if (v === null) { run = null; return; }
      if (!run) { run = []; runs.push(run); }
      run.push({ x: X(xs[i]), y: Y(v), v });
    });
    if (!runs.length) continue;

    // Bruecken zuerst, damit sie unter den echten Linien liegen.
    for (let i = 1; i < runs.length; i++) {
      const from = runs[i - 1][runs[i - 1].length - 1];
      const to = runs[i][0];
      hasGaps = true;
      el('line', {
        x1: from.x.toFixed(1), y1: from.y.toFixed(1),
        x2: to.x.toFixed(1), y2: to.y.toFixed(1),
        stroke: halo ? HALO : color, 'stroke-width': 1.4, 'stroke-dasharray': '2 4',
        'stroke-linecap': 'round', opacity: 0.5,
      }, linesG);
    }

    for (const points of runs) {
      // Ein einzelner Punkt ergibt keinen Pfad — als Marke zeichnen, sonst
      // verschwindet eine isolierte Umfrage komplett.
      if (points.length === 1) {
        el('circle', { cx: points[0].x, cy: points[0].y, r: 2.4, fill: color,
                       stroke: halo ? HALO : null, 'stroke-width': halo ? 1 : null }, linesG);
        continue;
      }
      const d = points
        .map((pt, i) => `${i ? 'L' : 'M'}${pt.x.toFixed(1)},${pt.y.toFixed(1)}`)
        .join('');
      if (halo) el('path', { d, stroke: HALO, class: 'series-halo' }, linesG);
      el('path', {
        d, stroke: color, class: 'series-line',
        'stroke-dasharray': state.patterns ? dashFor(s.partyId) : null,
      }, linesG);
    }

    const last = runs[runs.length - 1];
    const lastPoint = last[last.length - 1];
    ends.push({ partyId: s.partyId, shortcut: party.shortcut || '?', color, halo, ...lastPoint });
  }

  // Die Seite blendet daraufhin den erklaerenden Hinweis ein.
  host.dispatchEvent(new CustomEvent('chart:rendered', {
    detail: { hasGaps }, bubbles: false,
  }));

  // --- Direktbeschriftung am Kurvenende -----------------------------------
  // Bei Ueberschneidung werden die Labels auseinandergeschoben UND mit einer
  // duennen Fuehrungslinie an ihre Kurve gebunden, damit die Zuordnung bleibt.
  const labelsG = el('g', {}, svg);
  ends.sort((a, b) => a.y - b.y);
  const minGap = 13;
  for (let i = 1; i < ends.length; i++) {
    if (ends[i].y - ends[i - 1].y < minGap) ends[i].y = ends[i - 1].y + minGap;
  }
  const overflow = ends.length ? ends[ends.length - 1].y - (pad.top + plotH) : 0;
  if (overflow > 0) ends.forEach((e) => { e.y -= overflow; });

  // Ein Label, das zu weit von seiner Kurve wegrutscht, verliert die Zuordnung
  // und wird zu Rauschen — dann lieber weglassen; die Legende traegt es ohnehin.
  const MAX_SHIFT = 13;
  for (const e of ends) {
    const trueY = Y(e.v);

    // Der Endpunkt sitzt dort, wo die Daten tatsaechlich aufhoeren — nicht am
    // rechten Rand. In Laendern, in denen seit Monaten nicht mehr befragt
    // wurde, endet die Kurve sichtbar frueh, statt Aktualitaet vorzutaeuschen.
    el('circle', { cx: e.x, cy: trueY, r: 3.5, fill: e.color,
                   stroke: e.halo ? HALO : surface, 'stroke-width': 2 }, labelsG);
    if (Math.abs(e.y - trueY) > MAX_SHIFT) continue;

    const lx = e.x + 7;
    if (Math.abs(e.y - trueY) > 1.5 || e.x < pad.left + plotW - 1) {
      el('line', { x1: e.x + 3.5, y1: trueY, x2: lx - 2, y2: e.y,
                   stroke: e.halo ? HALO : e.color, class: 'leader' }, labelsG);
    }
    const t = el('text', { x: lx, y: e.y + 3.8, class: 'end-label',
                           fill: cssVar('--text', '#1d1d1f') }, labelsG);
    t.textContent = narrow ? e.shortcut : `${e.shortcut} ${fmt(e.v)}`;
  }

  // --- Hover: Crosshair + Tooltip ----------------------------------------
  const hoverG = el('g', { opacity: 0 }, svg);
  const crossLine = el('line', { y1: pad.top, y2: pad.top + plotH,
                                 stroke: cssVar('--muted', '#6e6e73'),
                                 'stroke-width': 1 }, hoverG);
  const hoverDots = el('g', {}, hoverG);

  const overlay = el('rect', {
    x: pad.left, y: pad.top, width: plotW, height: plotH,
    fill: 'transparent', style: 'cursor: crosshair',
  }, svg);

  const showAt = (clientX) => {
    const box = svg.getBoundingClientRect();
    const px = ((clientX - box.left) / box.width) * width;
    const day = x0 + ((px - pad.left) / Math.max(plotW, 1e-9)) * (x1 - x0);
    let idx = 0;
    let best = Infinity;
    xs.forEach((v, i) => {
      const dist = Math.abs(v - day);
      if (dist < best) { best = dist; idx = i; }
    });

    const cx = X(xs[idx]);
    crossLine.setAttribute('x1', cx);
    crossLine.setAttribute('x2', cx);
    hoverDots.replaceChildren();

    const rows = [];
    for (const s of visible) {
      const v = s.values[idx];
      if (v === null || v === undefined) continue;
      const party = partyById.get(s.partyId) || {};
      const color = partyColor(party);
      rows.push({ shortcut: party.shortcut || '?', v, color });
      el('circle', { cx, cy: Y(v), r: 3.6, fill: color,
                     stroke: needsHalo(color) ? HALO : surface, 'stroke-width': 2 }, hoverDots);
    }
    rows.sort((a, b) => b.v - a.v);

    hoverG.setAttribute('opacity', 1);
    tooltip.innerHTML =
      `<div class="tt-date">${fmtDate(data.dates[idx], 'long')}</div>` +
      rows.map((r) =>
        `<div class="tt-row"><span class="key" style="${swatchStyle(r.color)}"></span>` +
        `<span class="nm">${r.shortcut}</span><span class="vl">${fmt(r.v)} %</span></div>`).join('');
    tooltip.dataset.visible = 'true';

    const hostBox = host.getBoundingClientRect();
    const scale = hostBox.width / width;
    const ttw = tooltip.offsetWidth;
    let left = cx * scale + 14;
    if (left + ttw > hostBox.width) left = cx * scale - ttw - 14;
    tooltip.style.left = `${Math.max(0, left)}px`;
    tooltip.style.top = `${Math.max(0, pad.top * scale + 4)}px`;
  };

  const hide = () => {
    hoverG.setAttribute('opacity', 0);
    tooltip.dataset.visible = 'false';
  };

  overlay.addEventListener('pointermove', (ev) => showAt(ev.clientX));
  overlay.addEventListener('pointerdown', (ev) => showAt(ev.clientX));
  overlay.addEventListener('pointerleave', hide);
}

/* Strichmuster als Ersatzkanal, wenn Farbe nicht reicht (Schalter "Muster"). */
function dashFor(partyId) {
  const patterns = [null, '6 3', '2 3', '9 3 2 3', '1 3', '12 4', '5 2 1 2'];
  return patterns[partyId % patterns.length];
}

function timeTicks(dates, count) {
  if (dates.length <= 1) return dates.map((iso) => ({ iso, label: fmtDate(iso) }));
  const first = new Date(dates[0] + 'T12:00:00');
  const last = new Date(dates[dates.length - 1] + 'T12:00:00');
  const months = (last.getFullYear() - first.getFullYear()) * 12 + (last.getMonth() - first.getMonth());
  const step = Math.max(1, Math.ceil(months / count));
  const ticks = [];
  const cursor = new Date(first.getFullYear(), first.getMonth(), 1);
  if (cursor < first) cursor.setMonth(cursor.getMonth() + 1);
  while (cursor <= last) {
    const iso = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}-01`;
    const label = (cursor.getMonth() === 0 || ticks.length === 0 || step >= 12)
      ? `${MONTHS[cursor.getMonth()]} ${String(cursor.getFullYear()).slice(2)}`
      : MONTHS[cursor.getMonth()];
    ticks.push({ iso, label });
    cursor.setMonth(cursor.getMonth() + step);
  }
  return ticks;
}

/* ---------------------------------------------------------------- Sparkline */

export function sparkline(svg, data) {
  const render = () => {
    const width = svg.clientWidth || 240;
    const height = svg.clientHeight || 46;
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
    svg.setAttribute('preserveAspectRatio', 'none');
    svg.replaceChildren();
    if (!data.dates || data.dates.length < 2) return;

    const partyById = new Map(data.parties.map((p) => [p.id, p]));
    const xs = data.dates.map(dayOf);
    const x0 = xs[0];
    const x1 = xs[xs.length - 1];
    let lo = Infinity;
    let hi = -Infinity;
    for (const s of data.series) {
      for (const v of s.values) {
        if (v === null) continue;
        if (v < lo) lo = v;
        if (v > hi) hi = v;
      }
    }
    if (!Number.isFinite(lo)) return;
    const padY = Math.max((hi - lo) * 0.15, 0.6);
    lo -= padY;
    hi += padY;

    const X = (d) => ((d - x0) / Math.max(x1 - x0, 1e-9)) * (width - 2) + 1;
    const Y = (v) => height - 3 - ((v - lo) / Math.max(hi - lo, 1e-9)) * (height - 6);

    for (const s of data.series) {
      const party = partyById.get(s.partyId);
      if (!party) continue;
      const color = partyColor(party);
      const halo = needsHalo(color);

      // Gleiche Logik wie im grossen Chart: Luecken werden gestrichelt
      // ueberbrueckt statt die Linie abreissen zu lassen.
      const runs = [];
      let run = null;
      s.values.forEach((v, i) => {
        if (v === null) { run = null; return; }
        if (!run) { run = []; runs.push(run); }
        run.push({ x: X(xs[i]), y: Y(v) });
      });

      for (let i = 1; i < runs.length; i++) {
        const from = runs[i - 1][runs[i - 1].length - 1];
        const to = runs[i][0];
        el('line', { x1: from.x.toFixed(1), y1: from.y.toFixed(1),
                     x2: to.x.toFixed(1), y2: to.y.toFixed(1),
                     stroke: halo ? HALO : color, 'stroke-width': 1.2,
                     'stroke-dasharray': '1.5 2.5', opacity: 0.5 }, svg);
      }

      for (const points of runs) {
        if (points.length < 2) continue;
        const d = points
          .map((pt, i) => `${i ? 'L' : 'M'}${pt.x.toFixed(1)},${pt.y.toFixed(1)}`)
          .join('');
        if (halo) el('path', { d, stroke: HALO, class: 'spark-halo' }, svg);
        el('path', { d, stroke: color, fill: 'none',
                     'stroke-width': 1.6, 'stroke-linejoin': 'round',
                     'stroke-linecap': 'round' }, svg);
      }
    }
  };

  render();
  new ResizeObserver(render).observe(svg);
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', render);
}

/* --------------------------------------------------------------- Sitzbogen */

/** Klassischer Halbkreis. Sitze werden von links nach rechts in Ringen gelegt. */
export function seatArc(svg, entries, parties, majority) {
  const render = () => {
    const width = svg.clientWidth || 420;
    const height = Math.round(width * 0.55);
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
    svg.style.height = `${height}px`;
    svg.replaceChildren();

    const total = entries.reduce((sum, e) => sum + e.seats, 0);
    if (!total) return;

    const partyById = new Map(parties.map((p) => [p.id, p]));

    // Fraktionen von links nach rechts nach politischem Spektrum — so kennt
    // man Sitzbogen. Nach Groesse sortiert saehe der Bogen falsch aus.
    const ordered = [...entries].sort((a, b) => {
      const pa = partyById.get(a.partyId);
      const pb = partyById.get(b.partyId);
      return (pa?.spectrum ?? 99) - (pb?.spectrum ?? 99);
    });

    const cx = width / 2;
    const cy = height - 4;
    const rOuter = Math.min(width / 2 - 3, height - 8);
    const rInner = rOuter * 0.46;

    // Mehr Sitze -> mehr Ringe, sonst quetschen sich die Punkte im Bogen.
    const rows = Math.max(3, Math.min(14, Math.round(Math.sqrt(total) / 2.2)));
    const ringSpacing = (rOuter - rInner) / Math.max(rows - 1, 1);
    const radii = Array.from({ length: rows }, (_, i) => rInner + ringSpacing * i);

    // Sitze proportional zum Ringumfang verteilen.
    const radiusSum = radii.reduce((a, b) => a + b, 0);
    const perRow = radii.map((r) => Math.max(1, Math.round((r / radiusSum) * total)));
    let diff = total - perRow.reduce((a, b) => a + b, 0);
    for (let i = 0; diff !== 0; i = (i + 1) % rows) {
      perRow[i] += diff > 0 ? 1 : -1;
      diff += diff > 0 ? -1 : 1;
    }

    // Punktgroesse so, dass sie weder im Ring noch zwischen den Ringen kollidiert.
    let minArcGap = Infinity;
    radii.forEach((r, i) => {
      const gap = (Math.PI * r) / Math.max(perRow[i], 1);
      if (gap < minArcGap) minArcGap = gap;
    });
    const dotR = Math.max(1.6, Math.min(ringSpacing, minArcGap) * 0.4);

    // Ein Sitzplatz je Winkelposition, ueber alle Ringe hinweg von links nach
    // rechts durchnummeriert — dadurch stehen Fraktionen als Bloecke zusammen.
    const slots = [];
    perRow.forEach((count, row) => {
      for (let i = 0; i < count; i++) {
        slots.push({ row, frac: count === 1 ? 0.5 : i / (count - 1) });
      }
    });
    slots.sort((a, b) => a.frac - b.frac || a.row - b.row);

    const seats = [];
    for (const entry of ordered) {
      const party = partyById.get(entry.partyId);
      for (let i = 0; i < entry.seats; i++) seats.push(party);
    }

    slots.forEach((slot, i) => {
      const party = seats[i];
      if (!party) return;
      const angle = Math.PI - slot.frac * Math.PI;
      const r = radii[slot.row];
      const dot = el('circle', {
        cx: cx + Math.cos(angle) * r,
        cy: cy - Math.sin(angle) * r,
        r: dotR,
        fill: partyColor(party),
        class: needsHalo(partyColor(party)) ? 'seat seat-halo' : 'seat',
      }, svg);
      const title = el('title', {}, dot);
      title.textContent = party.shortcut;
    });

    if (majority) {
      const t = el('text', {
        x: cx, y: cy - 6, 'text-anchor': 'middle',
        fill: cssVar('--muted', '#6e6e73'), 'font-size': 12,
      }, svg);
      t.textContent = `${majority} f\u00fcr die Mehrheit`;
    }
  };

  render();
  new ResizeObserver(render).observe(svg);
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', render);
}
