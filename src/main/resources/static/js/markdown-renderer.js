import { escapeHtml } from './utils.js';

let markedInstance = null;
let hljsInstance = null;

async function loadMarked() {
  if (!markedInstance) {
    if (typeof window.marked !== 'undefined') {
      markedInstance = window.marked;
    } else {
      const module = await import('./vendor/marked.min.js');
      markedInstance = module.marked || module.default || module;
    }
  }
  return markedInstance;
}

async function loadHighlight() {
  if (!hljsInstance) {
    if (typeof window.hljs !== 'undefined') {
      hljsInstance = window.hljs;
    } else {
      const module = await import('./vendor/highlight.min.js');
      hljsInstance = module.hljs || module.default || module;
    }
  }
  return hljsInstance;
}

export async function initMarkdownRenderer(options = {}) {
  const marked = await loadMarked();
  const hljs = options.enableHighlight ? await loadHighlight() : null;

  const renderer = new marked.Renderer();

  if (hljs && options.enableHighlight !== false) {
    renderer.code = function(obj) {
      const code = (typeof obj === 'object' && obj !== null) ? obj.text : obj;
      const language = (typeof obj === 'object' && obj !== null) ? (obj.lang || '') : '';
      const lang = language || 'plaintext';
      
      let highlighted;
      try {
        if (hljs.getLanguage(lang)) {
          highlighted = hljs.highlight(code, { language: lang }).value;
        } else {
          highlighted = hljs.highlightAuto(code).value;
        }
      } catch (e) {
        highlighted = escapeHtml(code);
      }

      const codeId = 'code-' + Math.random().toString(36).substr(2, 9);
      return `<div class="code-block-wrapper">
        <div class="code-block-header">
          <span class="code-lang">${lang}</span>
          <button class="copy-btn" onclick="window.copyCode('${codeId}')">📋 复制</button>
        </div>
        <pre><code id="${codeId}" class="hljs language-${lang}">${highlighted}</code></pre>
      </div>`;
    };
  }

  marked.setOptions({
    renderer: renderer,
    breaks: options.breaks !== false,
    gfm: options.gfm !== false
  });

  return marked;
}

export async function renderMarkdown(text) {
  const marked = await initMarkdownRenderer({ enableHighlight: true });
  return marked.parse(text);
}

export function copyCode(codeId) {
  const codeEl = document.getElementById(codeId);
  if (codeEl) {
    navigator.clipboard.writeText(codeEl.textContent).then(() => {
      const btn = codeEl.closest('.code-block-wrapper').querySelector('.copy-btn');
      btn.textContent = '✅ 已复制!';
      btn.classList.add('copied');
      setTimeout(() => {
        btn.textContent = '📋 复制';
        btn.classList.remove('copied');
      }, 2000);
    });
  }
}

window.copyCode = copyCode;
