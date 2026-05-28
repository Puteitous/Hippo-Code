import { escapeHtml } from '../../utils.js';

export function parseToolArgs(args) {
  try {
    return typeof args === 'string' ? JSON.parse(args) : args;
  } catch (e) {
    return {};
  }
}

export function parseTodos(args) {
  try {
    const parsed = typeof args === 'string' ? JSON.parse(args) : args;
    return parsed.todos || [];
  } catch (e) {
    return [];
  }
}

export function computeUnifiedDiff(oldText, newText) {
  const oldLines = (oldText || '').split('\n');
  const newLines = (newText || '').split('\n');

  const m = oldLines.length;
  const n = newLines.length;

  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = oldLines[i - 1] === newLines[j - 1]
        ? dp[i - 1][j - 1] + 1
        : Math.max(dp[i - 1][j], dp[i][j - 1]);
    }
  }

  const reversed = [];
  let i = m, j = n;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      reversed.push({ type: 'same', content: oldLines[i - 1] });
      i--; j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      reversed.push({ type: 'added', content: newLines[j - 1] });
      j--;
    } else {
      reversed.push({ type: 'removed', content: oldLines[i - 1] });
      i--;
    }
  }
  return reversed.reverse();
}

export function countDiffStats(oldText, newText) {
  const diffLines = computeUnifiedDiff(oldText, newText);
  let insertions = 0, deletions = 0;
  for (const line of diffLines) {
    if (line.type === 'added') insertions++;
    else if (line.type === 'removed') deletions++;
  }
  return { insertions, deletions };
}

export function renderUnifiedDiff(diffLines) {
  let html = `<div class="unified-diff">`;
  for (const line of diffLines) {
    html += renderDiffLine(line);
  }
  html += `</div>`;
  return html;
}

export function renderDiffLine(line) {
  const cls = line.type === 'added' ? 'diff-added'
            : line.type === 'removed' ? 'diff-removed'
            : 'diff-context';
  const gutter = line.type === 'added' ? '+'
               : line.type === 'removed' ? '-'
               : ' ';
  return `<div class="diff-line ${cls}">
    <span class="diff-gutter">${gutter}</span>
    <span class="diff-line-content">${escapeHtml(line.content)}</span>
  </div>`;
}
