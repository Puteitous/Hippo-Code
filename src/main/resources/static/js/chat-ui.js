import { escapeHtml, formatTime, safeParseJSON } from './utils.js';
import { renderMarkdown } from './markdown-renderer.js';

export class ChatUI {
  constructor(container) {
    this.container = container;
  }

  clear() {
    this.container.innerHTML = '<div class="empty-state">发送消息开始对话</div>';
  }

  removeEmptyState() {
    const emptyState = this.container.querySelector('.empty-state');
    if (emptyState) emptyState.remove();
  }

  appendUserMessage(content, messageId) {
    this.removeEmptyState();
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message user';
    if (messageId) msgDiv.dataset.messageId = messageId;
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = content;
    msgDiv.appendChild(contentDiv);

    const btnContainer = document.createElement('div');
    btnContainer.className = 'message-actions';

    const editBtn = document.createElement('button');
    editBtn.className = 'message-action-btn';
    editBtn.title = '编辑';
    editBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>';
    btnContainer.appendChild(editBtn);

    msgDiv.appendChild(btnContainer);

    const timeDiv = document.createElement('div');
    timeDiv.className = 'message-time';
    timeDiv.textContent = formatTime(new Date());
    msgDiv.appendChild(timeDiv);
    
    this.container.appendChild(msgDiv);
    this.scrollToBottom();
    return { msgDiv, contentDiv, editBtn, btnContainer };
  }

  appendAssistantMessage(initialHTML = '<span class="typing-indicator">...</span>') {
    this.removeEmptyState();
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant';
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.innerHTML = initialHTML;
    msgDiv.appendChild(contentDiv);

    const btnContainer = document.createElement('div');
    btnContainer.className = 'message-actions';
    btnContainer.style.display = 'none';

    const retryBtn = document.createElement('button');
    retryBtn.className = 'message-action-btn';
    retryBtn.title = '重试';
    retryBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>';
    btnContainer.appendChild(retryBtn);

    const copyBtn = document.createElement('button');
    copyBtn.className = 'message-action-btn';
    copyBtn.title = '复制';
    copyBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';
    btnContainer.appendChild(copyBtn);

    msgDiv.appendChild(btnContainer);

    const timeDiv = document.createElement('div');
    timeDiv.className = 'message-time';
    timeDiv.textContent = formatTime(new Date());
    msgDiv.appendChild(timeDiv);
    
    this.container.appendChild(msgDiv);
    this.scrollToBottom();
    return { contentDiv, copyBtn, retryBtn, btnContainer };
  }

  async appendAssistantMessageFromHistory(content, timestamp) {
    this.removeEmptyState();
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant';
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.innerHTML = await renderMarkdown(content);
    msgDiv.appendChild(contentDiv);

    const btnContainer = document.createElement('div');
    btnContainer.className = 'message-actions';

    const copyBtn = document.createElement('button');
    copyBtn.className = 'message-action-btn';
    copyBtn.title = '复制';
    copyBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';
    btnContainer.appendChild(copyBtn);

    copyBtn.onclick = () => {
      const textToCopy = contentDiv.innerText;
      navigator.clipboard.writeText(textToCopy).then(() => {
        copyBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>';
        copyBtn.classList.add('copied');
        setTimeout(() => {
          copyBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';
          copyBtn.classList.remove('copied');
        }, 2000);
      });
    };
    msgDiv.appendChild(btnContainer);

    const timeDiv = document.createElement('div');
    timeDiv.className = 'message-time';
    timeDiv.textContent = timestamp ? formatTime(new Date(timestamp)) : formatTime(new Date());
    msgDiv.appendChild(timeDiv);
    
    this.container.appendChild(msgDiv);
    return msgDiv;
  }

  appendToolCallCard(tool) {
    this.removeEmptyState();
    const cardHTML = this.renderToolCard(tool);
    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = cardHTML;
    const card = tempDiv.firstElementChild;
    this.container.appendChild(card);
    this.scrollToBottom();
    return card;
  }

  renderToolCard(tool) {
    let argsDisplay = '';
    if (tool.args) {
      try {
        const parsed = typeof tool.args === 'string' ? JSON.parse(tool.args) : tool.args;
        argsDisplay = `<div class="detail-row"><span class="detail-label">参数:</span><span class="detail-value">${escapeHtml(JSON.stringify(parsed, null, 2))}</span></div>`;
      } catch (e) {
        argsDisplay = `<div class="detail-row"><span class="detail-label">参数:</span><span class="detail-value">${escapeHtml(String(tool.args))}</span></div>`;
      }
    }

    let resultDisplay = '';
    if (tool.resultContent) {
      resultDisplay = `<div class="detail-row"><span class="detail-label">结果:</span><span class="detail-value tool-result-content">${escapeHtml(tool.resultContent)}</span></div>`;
    }
    
    return `
      <div class="tool-call-card">
        <div class="tool-call-header" onclick="window.toggleToolCall(this)">
          <span class="tool-icon">🔧</span>
          <span class="tool-name">${escapeHtml(tool.name)}</span>
          <span class="tool-status ${tool.result || 'running'}">${tool.result === 'success' ? '✓ 成功' : tool.result === 'error' ? '✗ 失败' : '⋯ 运行中'}</span>
          <span class="arrow">▶</span>
        </div>
        <div class="tool-call-details">
          ${argsDisplay}
          ${resultDisplay}
          ${tool.error ? `<div class="detail-row"><span class="detail-label">错误:</span><span class="detail-value" style="color: var(--error-color);">${escapeHtml(tool.error)}</span></div>` : ''}
        </div>
      </div>
    `;
  }

  scrollToBottom() {
    this.container.scrollTop = this.container.scrollHeight;
  }

  isNearBottom(threshold = 80) {
    return this.container.scrollHeight - this.container.scrollTop - this.container.clientHeight < threshold;
  }
}

window.toggleToolCall = function(header) {
  header.classList.toggle('expanded');
  const details = header.nextElementSibling;
  details.classList.toggle('show');
};
