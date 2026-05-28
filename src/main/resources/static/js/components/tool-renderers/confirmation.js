import { escapeHtml } from '../../utils.js';

export function renderConfirmationDetail(tool) {
  const data = tool.confirmationData;
  const cmd = data.command || '';
  const riskLevel = data.riskLevel || 'medium';
  const riskReason = data.riskReason || '';
  const riskLabel = riskLevel === 'high' ? '高风险' : riskLevel === 'low' ? '低风险' : '中风险';
  const riskSvg = '<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1z"/><line x1="8" y1="5" x2="8" y2="9"/><line x1="8" y1="11" x2="8.01" y2="11"/></svg>';

  return `
    <div class="timeline-detail-confirmation ${riskLevel}">
      <div class="confirmation-header">
        <span class="confirmation-header-icon">${riskSvg}</span>
        <span class="confirmation-header-title">执行命令</span>
        <span class="risk-badge">${riskLabel}</span>
      </div>
      <div class="confirmation-body">
        <div class="confirmation-command"><pre><code>${escapeHtml(cmd)}</code></pre></div>
        ${riskReason ? `<div class="confirmation-reason">${escapeHtml(riskReason)}</div>` : ''}
        <div class="confirmation-footer">
          <label class="confirmation-auto-allow">
            <input type="checkbox" class="auto-allow-checkbox" data-confirm-id="${escapeHtml(data.confirmId)}">
            <span>本次会话不再询问此类命令</span>
          </label>
          <div class="confirmation-buttons">
            <button class="confirmation-btn deny" data-confirm-id="${escapeHtml(data.confirmId)}">拒绝</button>
            <button class="confirmation-btn allow" data-confirm-id="${escapeHtml(data.confirmId)}">执行</button>
          </div>
        </div>
      </div>
    </div>`;
}
