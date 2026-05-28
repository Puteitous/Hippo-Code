import { escapeHtml } from '../../utils.js';
import { parseToolArgs } from './shared.js';

export function renderReadFileDetail(tool) {
  const content = tool.resultContent || '';
  let html = '';
  if (content) {
    html += `<div class="timeline-detail-output"><pre><code>${escapeHtml(content)}</code></pre></div>`;
  }
  return html;
}

export function renderGrepDetail(tool) {
  const content = tool.resultContent || '';
  let html = '';
  if (content) {
    html += `<div class="timeline-detail-output"><pre><code>${escapeHtml(content)}</code></pre></div>`;
  }
  return html;
}

export function renderGlobDetail(tool) {
  const content = tool.resultContent || '';
  let html = '';
  if (content) {
    html += `<div class="timeline-detail-output"><pre><code>${escapeHtml(content)}</code></pre></div>`;
  }
  return html;
}

export function renderSearchDetail(tool) {
  const args = parseToolArgs(tool.args);
  const query = args.information_request || '';
  const content = tool.resultContent || '';
  let html = `<div class="timeline-detail-meta"><span class="timeline-detail-query">${escapeHtml(query)}</span></div>`;
  if (content) {
    html += `<div class="timeline-detail-output"><pre><code>${escapeHtml(content)}</code></pre></div>`;
  }
  return html;
}
