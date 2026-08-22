/* Seitenlogik: nimmt das vom Server mitgelieferte JSON und zeichnet daraus die
 * Charts. Ohne JavaScript bleiben Tabellen, Zahlen und Balken vollstaendig
 * lesbar — die Charts sind eine Ergaenzung, kein Zugangstor zu den Werten. */

import { trendChart, sparkline, seatArc, partyColor, darkMode, fmt, fmtDate } from './chart.js';

const boot = (() => {
  const node = document.getElementById('bootstrap');
  if (!node) return {};
  try {
    return JSON.parse(node.textContent);
  } catch (e) {
    console.error('[wahlen] Bootstrap-JSON unlesbar', e);
    return {};
  }
})();

/* Farbtupfer in HTML sind serverseitig auf den Light-Wert gesetzt; im Dark Mode
 * werden sie hier umgestellt (und bei Themewechsel erneut). */
function applyThemeColors() {
  const dark = darkMode();
  document.querySelectorAll('[data-color-light]').forEach((node) => {
    const color = dark ? node.dataset.colorDark : node.dataset.colorLight;
    if (!color) return;
    if (node.classList.contains('bar-fill') || node.classList.contains('key')
        || node.tagName === 'I') {
      node.style.background = color;
    }
  });
}
applyThemeColors();
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyThemeColors);

/* Legende: immer vorhanden, mit Text — Farbe allein traegt hier keine Identitaet.
 * Klick blendet eine Serie aus. */
function buildLegend(container, detail, chart, hidden) {
  container.replaceChildren();
  for (const party of detail.parties) {
    if (!detail.trend.series.some((s) => s.partyId === party.id)) continue;
    const value = detail.current.value[party.id];
    const button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('aria-pressed', String(!hidden.has(party.id)));
    button.innerHTML =
      `<span class="key" style="background:${partyColor(party)}"></span>` +
      `<span>${party.shortcut}</span>` +
      (value === undefined ? '' : `<span class="val">${fmt(value)}</span>`);
    button.addEventListener('click', () => {
      if (hidden.has(party.id)) hidden.delete(party.id);
      else hidden.add(party.id);
      button.setAttribute('aria-pressed', String(!hidden.has(party.id)));
      chart.update({ hidden });
    });
    container.appendChild(button);
  }
}

/** Blendet den Hinweis zu gestrichelten Abschnitten ein, sobald welche auftreten. */
function wireGapNote(host) {
  const note = document.querySelector('.gap-note');
  if (!note) return;
  host.addEventListener('chart:rendered', (ev) => {
    note.hidden = !ev.detail.hasGaps;
  });
}

function markersFor(detail) {
  const markers = [];
  if (detail.lastElection) {
    markers.push({ date: detail.lastElection.date, label: 'Wahl' });
  }
  if (detail.nextElection && detail.nextElection.confirmed) {
    markers.push({ date: detail.nextElection.date, label: 'Wahl' });
  }
  return markers;
}

function chartData(detail) {
  return {
    dates: detail.trend.dates,
    series: detail.trend.series,
    parties: detail.parties,
    polls: detail.polls,
    markers: markersFor(detail),
  };
}

/* ------------------------------------------------------------- Startseite */

if (boot.hero) {
  const host = document.getElementById('hero-chart');
  if (host) {
    const hidden = new Set();
    wireGapNote(host);
    const chart = trendChart(host, chartData(boot.hero), {
      threshold: boot.hero.parliament.threshold || null,
      hidden,
    });
    buildLegend(document.getElementById('hero-legend'), boot.hero, chart, hidden);
  }
}

if (boot.overview) {
  for (const summary of boot.overview) {
    const svg = document.querySelector(`svg.spark[data-slug="${summary.parliament.slug}"]`);
    if (!svg || !summary.spark) continue;
    sparkline(svg, {
      dates: summary.spark.dates,
      series: summary.spark.series,
      parties: summary.parties,
    });
  }
}

/* ----------------------------------------------------------- Detailseite */

if (boot.detail && document.getElementById('detail-chart')) {
  const detail = boot.detail;
  const host = document.getElementById('detail-chart');
  const legend = document.getElementById('detail-legend');
  const hidden = new Set();

  const sigmaNode = document.getElementById('sigma');
  if (sigmaNode) sigmaNode.textContent = String(detail.trend.sigmaDays).replace('.0', '');

  wireGapNote(host);
  const chart = trendChart(host, chartData(detail), {
    threshold: detail.parliament.threshold || null,
    hidden,
  });
  buildLegend(legend, detail, chart, hidden);

  const arc = document.getElementById('seat-arc');
  if (arc && detail.seats && detail.seats.entries.length) {
    seatArc(arc, detail.seats.entries, detail.parties, detail.seats.majority);
  }

  document.getElementById('toggle-raw')?.addEventListener('change', (ev) => {
    chart.update({ showRaw: ev.target.checked });
  });
  document.getElementById('toggle-patterns')?.addEventListener('change', (ev) => {
    chart.update({ patterns: ev.target.checked });
  });

  // Zeitraumwechsel laedt neu vom Server — die Glaettung haengt vom Fenster ab
  // und soll nicht im Browser nachgebaut werden.
  const controls = document.getElementById('controls');
  controls?.querySelectorAll('button[data-range]').forEach((button) => {
    button.addEventListener('click', async () => {
      controls.querySelectorAll('button[data-range]')
        .forEach((b) => b.setAttribute('aria-pressed', String(b === button)));

      const to = detail.range.to;
      let from = null;
      switch (button.dataset.range) {
        case 'election': from = detail.lastElection ? detail.lastElection.date : null; break;
        case '12': from = shift(to, -365); break;
        case '3': from = shift(to, -92); break;
        case 'all': from = '2017-01-01'; break;
        default: from = null;
      }

      // Voriges Bild stehen lassen statt Skelett zu flashen.
      host.style.opacity = '0.55';
      const url = new URL(`api/parliaments/${detail.parliament.slug}`, apiBase());
      if (from) url.searchParams.set('from', from);
      url.searchParams.set('to', to);
      try {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const fresh = await response.json();
        Object.assign(detail, fresh);
        chart.update({
          data: chartData(fresh),
          threshold: fresh.parliament.threshold || null,
          hidden,
        });
        buildLegend(legend, fresh, chart, hidden);
      } catch (e) {
        console.error('[wahlen] Zeitraum konnte nicht geladen werden', e);
      } finally {
        host.style.opacity = '1';
      }
    });
  });
}

/** Basis-URL der API — die App haengt unter /wahlen, nicht unter der Wurzel. */
function apiBase() {
  const path = window.location.pathname;
  const root = path.slice(0, path.indexOf('/', 1) + 1) || '/';
  return new URL(root, window.location.origin);
}

function shift(iso, days) {
  const d = new Date(iso + 'T12:00:00');
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

/* --------------------------------------------------- Umfragen-Vollansicht */

const instituteFilter = document.getElementById('institute-filter');
if (instituteFilter) {
  const table = document.getElementById('surveys-table');
  const rows = Array.from(table.tBodies[0].rows);
  const counter = document.getElementById('row-count');

  const institutes = [...new Set(rows.map((r) => r.dataset.institute).filter(Boolean))].sort();
  for (const name of institutes) {
    const option = document.createElement('option');
    option.value = name;
    option.textContent = name;
    instituteFilter.appendChild(option);
  }

  const apply = () => {
    const wanted = instituteFilter.value;
    let shown = 0;
    for (const row of rows) {
      const visible = !wanted || row.dataset.institute === wanted;
      row.hidden = !visible;
      if (visible) shown++;
    }
    if (counter) counter.textContent = `${shown} von ${rows.length} Umfragen`;
  };
  instituteFilter.addEventListener('change', apply);
  apply();
}
