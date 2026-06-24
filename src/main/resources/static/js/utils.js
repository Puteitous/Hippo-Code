export function escapeHtml(text, forAttribute = false) {
  // 处理 null 和 undefined
  if (text == null) {
    return '';
  }
  
  const html = String(text).replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
  
  // 如果用于 HTML 属性，额外处理单引号
  if (forAttribute) {
    return html.replace(/'/g, '&#39;');
  }
  
  return html;
}

export function safeParseJSON(data, fallback = null) {
  try {
    return JSON.parse(data);
  } catch (err) {
    console.error('JSON解析失败:', err);
    return fallback;
  }
}

export function formatTime(date) {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

export function generateSessionId() {
  return `web-${Date.now()}`;
}

export function truncateText(text, maxLength = 20) {
  if (text.length <= maxLength) return text;
  return text.substring(0, maxLength) + '...';
}

export function debounce(func, wait = 300) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

export function createElement(tag, className = '', innerHTML = '') {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (innerHTML) el.innerHTML = innerHTML;
  return el;
}

/**
 * 轻量 GET 请求封装。
 * 同域 JSON API，自动 check status + 解析 JSON。
 */
export async function apiGet(url) {
  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}

/**
 * 轻量 POST/PUT/DELETE 请求封装。
 * body 自动序列化为 JSON，自动 check status + 解析 JSON。
 */
export async function apiPost(url, body, method = 'POST') {
  const resp = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body != null ? JSON.stringify(body) : undefined
  });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}
