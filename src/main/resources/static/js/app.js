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

/* ---------------------------------------------------------------- Wahlabend */

/* Der Block kommt fertig gerendert vom Server und wird am Wahlabend als Ganzes
 * ausgetauscht — ein Template fuer Erstaufruf und Nachladen. Hier passiert nur,
 * was der Server nicht kann: Sitzbogen zeichnen, Farben ans Theme anpassen,
 * und im richtigen Rhythmus nachfragen. */
function renderWahlabend(block) {
  const node = block.querySelector('script.wahlabend-data');
  if (!node) return;
  let wa;
  try {
    wa = JSON.parse(node.textContent);
  } catch (e) {
    return;
  }
  const arc = block.querySelector('svg.seat-arc');
  if (arc && wa && wa.seats && wa.seats.entries && wa.seats.entries.length) {
    seatArc(arc, wa.seats.entries, wa.parties, wa.seats.majority);
  }
}

function wireWahlabend(initial) {
  let block = initial;
  renderWahlabend(block);
  let refresh = Number(block.dataset.refresh || 0);
  if (!refresh) return;

  const compact = block.dataset.wahlabend === 'compact';
  let timer = null;

  const stamp = (text) => {
    const node = block.querySelector('.stamp .checked');
    if (node) node.textContent = text;
  };

  const tick = async () => {
    // Ein verborgener Tab fragt nicht — beim Zurueckkommen sofort.
    if (document.hidden) { schedule(); return; }
    try {
      const url = new URL(`${block.dataset.slug}/wahlabend/fragment`, apiBase());
      if (compact) url.searchParams.set('compact', 'true');
      const response = await fetch(url, { cache: 'no-store' });
      if (response.status === 404) {
        // Kein Wahlabend mehr (amtliches Ergebnis da): Seite neu laden, damit
        // der Block an seinen Archivplatz wandert.
        window.location.reload();
        return;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const html = await response.text();
      const tmp = document.createElement('div');
      tmp.innerHTML = html;
      const fresh = tmp.firstElementChild;
      if (!fresh) throw new Error('leere Antwort');

      const changed = fresh.dataset.latest !== block.dataset.latest
        || fresh.dataset.count !== block.dataset.count
        || fresh.dataset.phase !== block.dataset.phase;
      if (changed) {
        block.replaceWith(fresh);
        block = fresh;
        applyThemeColors();
        renderWahlabend(block);
        refresh = Number(block.dataset.refresh || 0);
        if (!refresh) return;
      }
      stamp(`Zuletzt nachgefragt ${new Date().toLocaleTimeString('de-DE')}.`);
    } catch (e) {
      console.warn('[wahlen] Wahlabend konnte nicht nachgeladen werden', e);
      stamp('Nachladen gerade nicht möglich — der letzte Stand bleibt stehen.');
    }
    schedule();
  };

  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(tick, refresh * 1000);
  };
  schedule();
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) { clearTimeout(timer); tick(); }
  });
}

document.querySelectorAll('[data-wahlabend]').forEach(wireWahlabend);

/* ------------------------------------------------------ Wahlabend eintragen */

const reportForm = document.getElementById('report-form');
if (reportForm) {
  const TOKEN_KEY = 'wahlen.wahlabend.token';
  const tokenInput = reportForm.querySelector('input[name="token"]');
  const status = document.getElementById('form-status');
  const rows = document.getElementById('party-rows');
  try {
    const saved = localStorage.getItem(TOKEN_KEY);
    if (saved && tokenInput) tokenInput.value = saved;
  } catch (e) { /* privater Modus o.ae. */ }

  const timeInput = reportForm.querySelector('input[name="reportedAt"]');
  if (timeInput && !timeInput.value) {
    timeInput.value = new Date().toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  }

  const setStatus = (text, ok) => {
    status.textContent = text;
    status.className = `hint ${ok ? 'form-status-ok' : 'form-status-error'}`;
  };

  const addRow = (party = '') => {
    const row = document.createElement('div');
    row.className = 'party-row';
    row.innerHTML =
      '<input name="party" list="known-parties">' +
      '<input name="percent" type="text" inputmode="decimal" placeholder="0,0">' +
      '<input name="seats" type="text" inputmode="numeric" placeholder="–">' +
      '<button type="button" class="remove" aria-label="Zeile entfernen">×</button>';
    row.querySelector('input[name="party"]').value = party;
    rows.appendChild(row);
  };
  document.getElementById('add-row')?.addEventListener('click', () => addRow());
  rows.addEventListener('click', (ev) => {
    if (ev.target.classList.contains('remove')) ev.target.closest('.party-row').remove();
  });

  const num = (value) => {
    const s = String(value ?? '').trim().replace(',', '.');
    if (!s) return null;
    const n = Number(s);
    return Number.isFinite(n) ? n : NaN;
  };

  reportForm.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const data = new FormData(reportForm);
    const results = {};
    const seats = {};
    let bad = null;
    rows.querySelectorAll('.party-row:not(.head)').forEach((row) => {
      const party = row.querySelector('input[name="party"]').value.trim();
      const percent = num(row.querySelector('input[name="percent"]').value);
      const seat = num(row.querySelector('input[name="seats"]').value);
      if (!party || percent === null) return;
      if (Number.isNaN(percent) || Number.isNaN(seat)) { bad = party; return; }
      results[party] = percent;
      if (seat !== null) seats[party] = Math.round(seat);
    });
    if (bad) { setStatus(`Zahl bei ${bad} nicht lesbar.`, false); return; }
    if (!Object.keys(results).length) { setStatus('Keine Prozentwerte eingetragen.', false); return; }

    const body = {
      kind: data.get('kind'),
      reportedAt: data.get('reportedAt') || null,
      source: data.get('source'),
      sourceUrl: data.get('sourceUrl') || null,
      turnout: num(data.get('turnout')),
      note: data.get('note') || null,
      results,
      seats: Object.keys(seats).length ? seats : null,
    };
    const token = data.get('token');
    try { localStorage.setItem(TOKEN_KEY, token); } catch (e) { /* egal */ }

    const button = document.getElementById('submit');
    button.disabled = true;
    setStatus('Wird eingetragen …', true);
    try {
      const url = new URL(`api/wahlabend/${reportForm.dataset.slug}/${reportForm.dataset.date}/reports`, apiBase());
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Wahlabend-Token': token },
        body: JSON.stringify(body),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.error || `HTTP ${response.status}`);
      setStatus(`Eingetragen: ${payload.kindLabel} ${payload.timeLabel} Uhr (${payload.source}). Seite neu laden zeigt die Liste.`, true);
    } catch (e) {
      setStatus(`Fehler: ${e.message}`, false);
    } finally {
      button.disabled = false;
    }
  });

  document.getElementById('report-list')?.addEventListener('click', async (ev) => {
    const button = ev.target.closest('button.delete');
    if (!button) return;
    const id = button.dataset.id;
    button.disabled = true;
    try {
      const url = new URL(`api/wahlabend/reports/${id}`, apiBase());
      const response = await fetch(url, {
        method: 'DELETE',
        headers: { 'X-Wahlabend-Token': tokenInput ? tokenInput.value : '' },
      });
      if (!response.ok) {
        const payload = await response.json().catch(() => ({}));
        throw new Error(payload.error || `HTTP ${response.status}`);
      }
      button.closest('li').remove();
      setStatus(`Stand ${id} gelöscht.`, true);
    } catch (e) {
      button.disabled = false;
      setStatus(`Löschen fehlgeschlagen: ${e.message}`, false);
    }
  });
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
