/**
 * FilePreview — 文件预览/编辑组件
 *
 * 使用 CodeMirror 6 实现语法高亮 + 编辑功能。
 * 文件打开即可以编辑，Ctrl+S 保存。
 *
 * 依赖：
 *   - window.HippoDesktop（桌面端 bridge）
 *   - js/vendor/codemirror.js（esbuild 打包的 CM6 bundle）
 */

import { EditorView, keymap, EditorState, Compartment, basicSetup, oneDark,
  javascript, python, java, html, css, json, markdown, xml, yaml, sql,
  rust, php, go, sass } from '../vendor/codemirror.js'
import { SearchPanel } from './search-panel.js'
import { renderMarkdown } from '../markdown-renderer.js'
import { createDiffExtension } from './FilePreviewDiff.js'

/**
 * 支持文本编辑 + 图片/PDF 预览的扩展文件预览类。
 *
 * 文件类型自动检测：
 *   - 文本/代码文件 → CodeMirror 6 编辑器（可编辑）
 *   - 图片文件 → &lt;img&gt; 标签渲染（只读）
 *   - PDF 文件 → &lt;iframe&gt; 嵌入浏览器原生 PDF 查看器（只读）
 */

export class FilePreview {
  constructor({ container, onError, onDirtyChange }) {
    this._container = container;
    this._onError = onError || (() => {});
    this._onDirtyChange = onDirtyChange || (() => {});
    this._currentPath = null;
    this._content = '';
    this._dirty = false;
    this._view = null;
    /** @private Compartment 用于动态切换主题，避免重建编辑器 */
    this._themeCompartment = new Compartment();
    /** @private MutationObserver 监听 data-theme 变化 */
    this._themeObserver = null;
    /** @private 搜索面板实例 */
    this._searchPanel = null;
    /** @private MD 预览模式状态 */
    this._mdPreviewMode = false;
    /** @private MD 预览渲染容器 */
    this._mdPreviewEl = null;
    /** @private 当前编辑的图像内容（预览切换时重新渲染使用） */
    this._contentForPreview = '';
    /** @private Compartment 用于动态切换 diff 扩展 */
    this._diffCompartment = new Compartment();
    /** @private AI 修改前的文件原始内容（用于 diff 对比） */
    this._originalContent = null;
    /** @private 二进制预览类型：'image' | 'pdf' | null */
    this._binaryViewType = null;

    // 绑定搜索按钮
    this._registerSearchButton();
    // 绑定 MD 预览切换按钮
    this._registerMdToggleBtn();
  }

  get currentPath() { return this._currentPath; }
  get isDirty() { return this._dirty; }

  /** @private 绑定搜索按钮点击事件 */
  _registerSearchButton() {
    const btn = document.getElementById('previewSearchBtn');
    if (!btn) return;
    btn.addEventListener('click', () => {
      if (this._searchPanel) this._searchPanel.openFind();
    });
  }

  /** @private 绑定 MD 预览切换按钮 */
  _registerMdToggleBtn() {
    const btn = document.getElementById('previewMdToggleBtn');
    if (!btn) return;
    btn.addEventListener('click', () => this._toggleMdPreview());
  }

  async show(filePath) {
    // 上游（FileTabs onBeforeSwitch）已处理脏检查弹窗，此处只清理旧 dirty 状态
    if (this._dirty) {
      this._dirty = false;
      this._onDirtyChange(this._currentPath, false);
    }

    this._currentPath = filePath;
    this._container.dataset.currentPath = filePath;
    this._dirty = false;

    // ── 图片 / PDF 等二进制文件 → 直接通过 HTTP 渲染预览 ──
    if (this._isImage(filePath) || this._isPdf(filePath)) {
      this._destroyEditor();
      this._showBinaryPreview(filePath);
      this._updateSaveBtn();
      this._updateMdToggleBtn();
      return;
    }

    // ── 表格文件（XLSX/XLS/CSV）→ 通过 SheetJS 渲染为 HTML 表格 ──
    if (this._isSpreadsheet(filePath)) {
      this._destroyEditor();
      this._showSpreadsheet(filePath);
      this._updateSaveBtn();
      this._updateMdToggleBtn();
      return;
    }

    // ── DOCX 文件 → 通过 mammoth.js 渲染为 HTML ──
    if (this._isDocx(filePath)) {
      this._destroyEditor();
      this._showDocx(filePath);
      this._updateSaveBtn();
      this._updateMdToggleBtn();
      return;
    }

    let content;
    try {
      const result = await window.HippoDesktop.readFile(filePath);
      content = result.content;
    } catch (err) {
      console.error('FilePreview: readFile failed', filePath, err);
      this._showError('无法读取文件: ' + err.message);
      this._onError(err);
      return;
    }

    this._content = content;
    this._contentForPreview = content;
    this._initEditor(content, filePath);
    this._mdPreviewMode = false;
    this._updateSaveBtn();
    this._updateMdToggleBtn();
    // 异步获取原始内容用于 diff 标记（不影响打开速度）
    this._fetchOriginalContent(filePath);
  }

  async reload() {
    if (this._currentPath) {
      const path = this._currentPath;
      this._dirty = false;
      await this.show(path);
    }
  }

  async save() {
    if (!this._currentPath || !this._view || !this._dirty) return;
    const content = this._view.state.doc.toString();
    try {
      await window.HippoDesktop.writeFile(this._currentPath, content);
      this._content = content;
      this._dirty = false;
      this._originalContent = null; // 保存后清空原始内容基准，diff 标记自动清除
      this._onDirtyChange(this._currentPath, false);
      this._updateSaveBtn();
      // 重新配置 diff 扩展为空（清除 gutter 标记和行背景色）
      if (this._view) {
        this._view.dispatch({
          effects: this._diffCompartment.reconfigure([]),
        });
      }
    } catch (err) {
      this._showError('保存失败: ' + err.message);
    }
  }

  clear() {
    this._destroyEditor();
    this._binaryViewType = null;
    this._currentPath = null;
    this._content = '';
    this._dirty = false;
    this._originalContent = null;
    delete this._container.dataset.currentPath;
    this._updateSaveBtn();
  }

  /**
   * 滚动到指定行并聚焦，可选选中范围并居中
   * @param {number} line - 1-based 起始行号
   * @param {number} [endLine] - 1-based 结束行号（可选），提供则选中起始到结束行范围
   */
  scrollToLine(line, endLine) {
    if (!this._view) return;
    const fromLine = Math.max(0, line - 1);
    const docLine = this._view.state.doc.line(fromLine + 1);
    if (!docLine) return;

    let selection;
    if (endLine && endLine > line) {
      const toLine = Math.min(endLine, this._view.state.doc.lines);
      const endDocLine = this._view.state.doc.line(toLine);
      selection = { anchor: docLine.from, head: endDocLine.to };
    } else {
      selection = { anchor: docLine.from };
    }

    this._view.dispatch({ selection });

    // 将目标行定位到视口上方约 1/4 处
    requestAnimationFrame(() => {
      const lineBlock = this._view.lineBlockAt(docLine.from);
      if (lineBlock) {
        const scrollDOM = this._view.scrollDOM;
        scrollDOM.scrollTop = lineBlock.top - scrollDOM.clientHeight * 0.25;
      }
    });

    this._view.focus();
  }

  /** @private 获取 AI 修改前的文件原始内容，用于 diff 标记 */
  async _fetchOriginalContent(filePath) {
    try {
      const resp = await fetch(`/api/diff/original?path=${encodeURIComponent(filePath)}`);
      if (!resp.ok) return;
      const data = await resp.json();
      if (data.content === undefined || data.content === null) return;

      this._originalContent = data.content;

      // 激活 diff 扩展
      if (this._view) {
        this._view.dispatch({
          effects: this._diffCompartment.reconfigure(
            createDiffExtension(this._originalContent)
          ),
        });
      }
    } catch (e) {
      console.debug('FilePreview: no original content for', filePath);
    }
  }

  // ==================== CodeMirror ====================

  _initEditor(content, filePath) {
    this._destroyEditor();

    const lang = this._getLanguageExtension(filePath);
    const isDark = this._isDarkTheme();

    const saveKeyBinding = keymap.of([{
      key: 'Mod-s',
      run: () => { this.save(); return true; }
    }]);

    const state = EditorState.create({
      doc: content,
      extensions: [
        basicSetup,
        lang,
        this._themeCompartment.of(isDark ? oneDark : []),
        this._diffCompartment.of([]), // 暂不启用 diff，等 _fetchOriginalContent 完成后激活
        saveKeyBinding,
        EditorView.theme({
          '&': { height: '100%' },
          '.cm-scroller': { overflow: 'auto' },
        }),
      ],
    });

    this._view = new EditorView({
      state,
      parent: this._container,
      dispatch: (tr) => {
        this._view.update([tr]);
        if (tr.docChanged) {
          const currentContent = this._view.state.doc.toString();
          if (currentContent === this._content) {
            // 撤销回原始内容，清除脏标记
            if (this._dirty) {
              this._dirty = false;
              this._onDirtyChange(this._currentPath, false);
              this._updateSaveBtn();
            }
          } else if (!this._dirty) {
            this._dirty = true;
            this._onDirtyChange(this._currentPath, true);
            this._updateSaveBtn();
          }
        }
      },
    });

    // 挂到 DOM 上，供 selection-actions 计算行号引用
    this._container._cmPreviewView = this._view;

    // 初始化搜索面板
    this._searchPanel = new SearchPanel(this._view);

    // ── MD 预览容器 ──
    this._mdPreviewEl = document.createElement('div');
    this._mdPreviewEl.className = 'file-md-preview';
    this._mdPreviewEl.style.display = 'none';
    this._container.appendChild(this._mdPreviewEl);

    // ── 拦截 Ctrl+F / Ctrl+H ──
    //
    // 使用 capture phase（第三个参数 true）在 CM6 内部 keymap 处理前拦截事件。
    //
    // 为什么不用 CM6 keymap 覆盖？
    //   CM6 defaultKeymap 中 "Ctrl-f" 绑定了 cursorCharRight（Emacs 风格），
    //   这个绑定会优先匹配成功并 return true，导致我们的 Mod-f 覆盖永远无法生效。
    //
    // 为什么用 capture phase？
    //   capture phase 在 CM6 内部 dispatch 之前执行，preventDefault() +
    //   stopImmediatePropagation() 可以直接阻止事件到达 CM6 的 keymap 系统。
    //
    // 注意事项：
    //   - 只在 编辑器内快捷键冲突 时用此方案，新增快捷键优先用 CM6 keymap.of()
    //   - _destroyEditor() 中必须 removeEventListener 清理
    //   - scope: 'editor' 在此场景无效，因为 defaultKeymap 也有相同 key
    this._view.dom.addEventListener('keydown', this._boundSearchShortcut = (e) => {
      if (e.ctrlKey || e.metaKey) {
        if (e.key === 'f' || e.key === 'F') {
          e.preventDefault();
          e.stopImmediatePropagation();
          if (this._searchPanel) this._searchPanel.openFind();
        } else if (e.key === 'h' || e.key === 'H') {
          e.preventDefault();
          e.stopImmediatePropagation();
          if (this._searchPanel) this._searchPanel.openReplace();
        }
      }
    }, true); // capture phase

    // 暴露搜索方法（供外部如 DevTools 调用）
    window.__cmOpenFind = () => {
      if (this._view) {
        this._view.focus();
        if (this._searchPanel) this._searchPanel.openFind();
      }
    };

    this._startThemeObserver();
  }

  _destroyEditor() {
    this._stopThemeObserver();
    this._container._cmPreviewView = null;
    if (this._view) {
      if (this._boundSearchShortcut) {
        this._view.dom.removeEventListener('keydown', this._boundSearchShortcut, true);
        this._boundSearchShortcut = null;
      }
      this._view.destroy();
      this._view = null;
      this._searchPanel = null;
      this._mdPreviewEl = null;
    }
    this._container.innerHTML = '';
  }

  /** 当前是否为深色主题 */
  _isDarkTheme() {
    return document.documentElement.getAttribute('data-theme') === 'dark';
  }

  /** 监听 <html> data-theme 变化，动态切换 CM6 主题 */
  _startThemeObserver() {
    this._stopThemeObserver();
    this._themeObserver = new MutationObserver(() => {
      if (!this._view) return;
      const isDark = this._isDarkTheme();
      this._view.dispatch({
        effects: this._themeCompartment.reconfigure(isDark ? oneDark : []),
      });
    });
    this._themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme'],
    });
  }

  _stopThemeObserver() {
    if (this._themeObserver) {
      this._themeObserver.disconnect();
      this._themeObserver = null;
    }
  }

  _getLanguageExtension(filePath) {
    const ext = filePath.split('.').pop().toLowerCase();
    const map = {
      js: javascript, jsx: javascript, ts: javascript, tsx: javascript, mjs: javascript, cjs: javascript,
      py: python,
      java,
      html, htm: html, vue: html, svelte: html,
      css, scss: sass, less: sass,
      json,
      md: markdown, markdown,
      xml, svg: xml,
      yaml, yml: yaml,
      sql,
      rs: rust,
      php,
      go,
    };
    const langFn = map[ext];
    return langFn ? langFn() : [];
  }

  // ==================== 按钮状态同步 ====================

  _updateSaveBtn() {
    const btn = document.getElementById('previewSaveBtn');
    const searchBtn = document.getElementById('previewSearchBtn');
    if (!btn) return;

    if (this._currentPath) {
      // 二进制文件（图片/PDF）不显示保存和搜索按钮
      if (this._binaryViewType) {
        btn.style.display = 'none';
        if (searchBtn) searchBtn.style.display = 'none';
        return;
      }
      btn.style.display = '';
      if (searchBtn) searchBtn.style.display = this._mdPreviewMode ? 'none' : '';
      if (this._dirty) {
        btn.innerHTML = `
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M13 4l-7 7L3 8"/>
          </svg>`;
        btn.title = '保存 (Ctrl+S)';
        btn.classList.add('dirty');
      } else {
        btn.innerHTML = `
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M13 4l-7 7L3 8"/>
          </svg>`;
        btn.title = '已保存';
        btn.classList.remove('dirty');
      }
    } else {
      btn.style.display = 'none';
      if (searchBtn) searchBtn.style.display = 'none';
    }
  }

  // ==================== MD 预览切换 ====================

  /** 判断是否为 Markdown 文件 */
  _isMarkdown(filePath) {
    return filePath && filePath.toLowerCase().endsWith('.md');
  }

  /** 切换 MD 预览/编辑模式 */
  async _toggleMdPreview() {
    if (!this._isMarkdown(this._currentPath) || !this._view) return;

    if (this._mdPreviewMode) {
      // 切回编辑模式
      this._mdPreviewEl.style.display = 'none';
      this._view.dom.style.display = '';
      this._view.focus();
      this._mdPreviewMode = false;
    } else {
      // 切到预览模式
      const content = this._view.state.doc.toString();
      this._contentForPreview = content;
      this._mdPreviewEl.innerHTML = '<div class="file-md-preview-loading">渲染中...</div>';
      this._mdPreviewEl.style.display = '';
      this._view.dom.style.display = 'none';
      // 关闭搜索面板（预览模式下不可用）
      if (this._searchPanel) this._searchPanel.close();

      try {
        const html = await renderMarkdown(content);
        this._mdPreviewEl.innerHTML = html;
      } catch (err) {
        this._mdPreviewEl.innerHTML = `<div class="file-preview-placeholder" style="color:var(--error-text);">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <p>渲染失败: ${this._escapeHtml(err.message)}</p>
        </div>`;
      }
      this._mdPreviewMode = true;
    }
    this._updateSaveBtn();
    this._updateMdToggleBtn();
  }

  /** 更新 MD 预览切换按钮状态 */
  _updateMdToggleBtn() {
    const btn = document.getElementById('previewMdToggleBtn');
    if (!btn) return;

    if (this._isMarkdown(this._currentPath) && this._view) {
      btn.style.display = '';
      btn.classList.toggle('active', this._mdPreviewMode);
      btn.title = this._mdPreviewMode ? '编辑模式' : '预览模式';
      btn.innerHTML = this._mdPreviewMode
        ? `<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M11 1.5H5a1 1 0 0 0-1 1v11a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1v-11a1 1 0 0 0-1-1z"/>
            <line x1="5" y1="4" x2="11" y2="4"/>
            <line x1="5" y1="7" x2="11" y2="7"/>
            <line x1="5" y1="10" x2="9" y2="10"/>
          </svg>`
        : `<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M1 3v10l5-3.5L11 13l4-2.5V3l-4 2.5L6 3 1 6.5z"/>
            <path d="M6 3v7.5"/>
            <path d="M11 5.5V13"/>
          </svg>`;
    } else {
      btn.style.display = 'none';
    }
  }

  // ==================== 二进制文件预览（图片 / PDF）====================

  /** 判断是否为图片文件 */
  _isImage(filePath) {
    if (!filePath) return false;
    const ext = filePath.split('.').pop().toLowerCase();
    return ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp', 'ico'].includes(ext);
  }

  /** 判断是否为 PDF 文件 */
  _isPdf(filePath) {
    return filePath && filePath.toLowerCase().endsWith('.pdf');
  }

  /** 渲染二进制文件预览（图片用 &lt;img&gt;，PDF 用 &lt;iframe&gt;） */
  _showBinaryPreview(filePath) {
    this._binaryViewType = this._isImage(filePath) ? 'image' : 'pdf';
    const encodedPath = encodeURIComponent(filePath);
    const url = `/api/file/raw?path=${encodedPath}`;
    const fileName = filePath.split('/').pop() || filePath;

    if (this._binaryViewType === 'image') {
      this._container.innerHTML = `
        <div class="file-binary-preview image">
          <img src="${url}" alt="${this._escapeHtml(fileName)}"
               onerror="this.parentElement.innerHTML='<div class=\\'file-preview-placeholder\\'><svg viewBox=\\'0 0 24 24\\' width=\\'32\\' height=\\'32\\' fill=\\'none\\' stroke=\\'currentColor\\' stroke-width=\\'1.5\\'><circle cx=\\'12\\' cy=\\'12\\' r=\\'10\\'/><line x1=\\'12\\' y1=\\'8\\' x2=\\'12\\' y2=\\'12\\'/><line x1=\\'12\\' y1=\\'16\\' x2=\\'12.01\\' y2=\\'16\\'/></svg><p>图片加载失败</p></div>'" />
        </div>`;
    } else {
      this._container.innerHTML = `
        <div class="file-binary-preview pdf">
          <iframe src="${url}" title="${this._escapeHtml(fileName)}"></iframe>
        </div>`;
    }
  }

  // ==================== 表格文件预览（XLSX / XLS / CSV）====================

  /** 判断是否为表格文件 */
  _isSpreadsheet(filePath) {
    if (!filePath) return false;
    const ext = filePath.split('.').pop().toLowerCase();
    return ['xlsx', 'xls', 'csv'].includes(ext);
  }

  /** 通过 SheetJS 将表格文件渲染为 HTML 表格 */
  async _showSpreadsheet(filePath) {
    this._binaryViewType = 'spreadsheet';
    const encodedPath = encodeURIComponent(filePath);
    const url = `/api/file/raw?path=${encodedPath}`;

    // 表格渲染的最大总行数（超过此值说明文件太大，不应全量读取）
    const MAX_TOTAL_ROWS = 1000;
    // 实际显示行数
    const DISPLAY_ROWS = 100;

    try {
      const resp = await fetch(url);
      if (!resp.ok) {
        this._showHttpError(resp, filePath);
        return;
      }
      const arrayBuffer = await resp.arrayBuffer();

      // ── CSV 编码检测：处理 GBK/GB2312 中文乱码 ──
      // Windows 上导出的 CSV 常用 GBK 编码，而 SheetJS 默认按 UTF-8 解析，
      // 导致中文乱码。此处做自动检测：UTF-8 BOM → UTF-8；合法 UTF-8 → 直接使用；
      // 包含非法 UTF-8 序列 → 按 GBK 解码再转 UTF-8。
      let sheetData;
      if (this._isCsv(filePath)) {
        sheetData = this._decodeCSVBytes(arrayBuffer);
      } else {
        sheetData = new Uint8Array(arrayBuffer);
      }

      // SheetJS 解析
      const workbook = XLSX.read(sheetData, { type: 'array' });

      // ── 渲染表格的通用函数 ──
      const renderSheetTable = (sheet, sheetIdx) => {
        const jsonData = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '' });
        const totalRows = jsonData.length;
        const isOverflow = totalRows > MAX_TOTAL_ROWS;
        const displayData = isOverflow ? jsonData.slice(0, DISPLAY_ROWS) : jsonData;

        let tableHtml = '';
        if (displayData.length === 0) {
          tableHtml = '<div class="spreadsheet-empty">此 sheet 为空</div>';
        } else {
          tableHtml = '<table>';
          displayData.forEach((row, rowIdx) => {
            tableHtml += '<tr>';
            row.forEach((cell) => {
              const tag = rowIdx === 0 ? 'th' : 'td';
              const val = cell != null ? String(cell) : '';
              const cellClass = rowIdx === 0 ? '' : (!isNaN(val) && val !== '' ? 'num-cell' : 'text-cell');
              tableHtml += `<${tag}${cellClass ? ` class="${cellClass}"` : ''}>${this._escapeHtml(val)}</${tag}>`;
            });
            tableHtml += '</tr>';
          });
          tableHtml += '</table>';
        }

        // 行数超限提示
        if (isOverflow) {
          const remainingRows = totalRows - DISPLAY_ROWS;
          tableHtml += `<div class="spreadsheet-overflow-notice">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="8" cy="8" r="6"/>
              <line x1="8" y1="5" x2="8" y2="8"/>
              <line x1="8" y1="10.5" x2="8.01" y2="10.5"/>
            </svg>
            仅显示前 ${DISPLAY_ROWS} 行，共 ${totalRows} 行（剩余 ${remainingRows} 行未显示）
          </div>`;
        }

        return { html: tableHtml, totalRows, isOverflow };
      };

      // ── 构建整体 HTML ──
      const fileName = filePath.split('/').pop() || '';
      const sheetName = workbook.SheetNames[0];
      const sheet = workbook.Sheets[sheetName];
      const firstRender = renderSheetTable(sheet, 0);

      let html = `<div class="file-spreadsheet-preview">

        <!-- 顶部信息栏 -->
        <div class="spreadsheet-info">
          <span class="file-name">${this._escapeHtml(fileName)}</span>
          <span class="sheet-count">
            ${workbook.SheetNames.length} 个 sheet · ${sheetName}（激活）
          </span>
          ${firstRender.isOverflow
            ? `<span class="spreadsheet-size-warn" title="文件过大，仅显示前 ${DISPLAY_ROWS} 行">
                 ${this._escapeHtml(this._formatFileSize(arrayBuffer.byteLength))}
               </span>`
            : `<span class="spreadsheet-size">${this._escapeHtml(this._formatFileSize(arrayBuffer.byteLength))}</span>`}
        </div>`;

      // Sheet 标签栏
      if (workbook.SheetNames.length > 1) {
        html += `<div class="spreadsheet-sheet-tabs">
          ${workbook.SheetNames.map((name, i) => `
            <div class="sheet-tab ${i === 0 ? 'active' : ''}" data-sheet-index="${i}">
              ${this._escapeHtml(name)}
            </div>`).join('')}
        </div>`;
      }

      // 表格容器
      html += `<div class="spreadsheet-table-wrap">${firstRender.html}</div></div>`;
      this._container.innerHTML = html;

      // ── 绑定 sheet 切换事件 ──
      const tabs = this._container.querySelectorAll('.sheet-tab');
      const wrap = this._container.querySelector('.spreadsheet-table-wrap');
      const infoSpan = this._container.querySelector('.spreadsheet-info .sheet-count');
      tabs.forEach(tab => {
        tab.addEventListener('click', () => {
          const idx = parseInt(tab.dataset.sheetIndex, 10);
          tabs.forEach(t => t.classList.remove('active'));
          tab.classList.add('active');

          const name = workbook.SheetNames[idx];
          const s = workbook.Sheets[name];
          const rendered = renderSheetTable(s, idx);
          wrap.innerHTML = rendered.html;

          if (infoSpan) {
            infoSpan.textContent = `${workbook.SheetNames.length} 个 sheet · ${name}（激活）`;
          }
        });
      });

    } catch (err) {
      console.error('FilePreview: spreadsheet parse failed', filePath, err);
      this._container.innerHTML = `<div class="file-preview-placeholder">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <p>表格解析失败: ${this._escapeHtml(err.message)}</p>
      </div>`;
      this._onError(err);
    }
  }

  // ==================== DOCX 文件预览 ====================

  /** 判断是否为 DOCX 文件 */
  _isDocx(filePath) {
    return filePath && filePath.toLowerCase().endsWith('.docx');
  }

  /** 通过 mammoth.js 将 DOCX 渲染为 HTML */
  async _showDocx(filePath) {
    this._binaryViewType = 'docx';
    const encodedPath = encodeURIComponent(filePath);
    const url = `/api/file/raw?path=${encodedPath}`;

    try {
      const resp = await fetch(url);
      if (!resp.ok) {
        this._showHttpError(resp, filePath);
        return;
      }
      const arrayBuffer = await resp.arrayBuffer();

      // mammoth.js 转换（带样式映射，处理 Word 常见样式）
      const styleMap = [
        "p[style-name='Title'] => h1:fresh",
        "p[style-name='Subtitle'] => h2:fresh",
        "p[style-name='Heading 1'] => h1:fresh",
        "p[style-name='Heading 2'] => h2:fresh",
        "p[style-name='Heading 3'] => h3:fresh",
        "p[style-name='Heading 4'] => h4:fresh",
        "p[style-name='Heading 5'] => h5:fresh",
        "p[style-name='Quote'] => blockquote:fresh",
      ];
      const result = await mammoth.convertToHtml({
        arrayBuffer: arrayBuffer,
        styleMap: styleMap,
      });

      // 样式化后的文档容器
      this._container.innerHTML = `
        <div class="file-docx-preview">

          <!-- 顶部信息栏 -->
          <div class="docx-info">
            <span class="file-name">${this._escapeHtml(filePath.split('/').pop() || '')}</span>
            ${result.messages && result.messages.length > 0
              ? `<span class="docx-warning light"
                     title="${this._escapeHtml(result.messages.map(m => m.message).join('\n'))}">
                   ⚠ ${result.messages.length} 条样式警告
                 </span>`
              : ''}
          </div>

          <!-- 文档内容区 -->
          <div class="docx-content">
            ${result.value}
          </div>
        </div>`;

      // 如果转换有警告，将它们渲染到控制台
      if (result.messages && result.messages.length > 0) {
        console.info('mammoth.js 转换警告:', result.messages);
      }

    } catch (err) {
      console.error('FilePreview: docx parse failed', filePath, err);
      this._container.innerHTML = `<div class="file-preview-placeholder">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <p>文档解析失败: ${this._escapeHtml(err.message)}</p>
      </div>`;
      this._onError(err);
    }
  }

  // ==================== 工具方法 ====================

  /**
   * 根据 HTTP 响应状态码显示友好错误提示
   * @param {Response} resp - fetch 的响应对象
   * @param {string} filePath - 文件路径（用于读取错误文本）
   */
  async _showHttpError(resp, filePath) {
    this._destroyEditor();
    this._currentPath = null;
    this._binaryViewType = null;
    delete this._container.dataset.currentPath;

    // 尝试读取服务端返回的错误信息
    let serverMsg = '';
    try {
      serverMsg = await resp.text();
    } catch (_) {}

    const status = resp.status;
    let title = '预览失败';
    let detail = '';

    if (status === 413) {
      title = '文件过大';
      detail = serverMsg || '文件大小超过预览上限（50MB），请在本地打开';
    } else if (status === 404) {
      title = '文件未找到';
      detail = serverMsg || '文件可能已被移动或删除';
    } else if (status === 400) {
      title = '请求错误';
      detail = serverMsg || '无效的文件路径';
    } else if (status >= 500) {
      title = '服务器错误';
      detail = serverMsg || '服务器处理文件时出错，请稍后重试';
    } else {
      detail = serverMsg || `请求失败（HTTP ${status}）`;
    }

    // 仅在桌面端且可定位文件时显示"在文件管理器中查看"按钮
    const canShowInFolder = typeof window.HippoDesktop !== 'undefined'
      && window.HippoDesktop
      && typeof window.HippoDesktop.showItemInFolder === 'function'
      && filePath;

    this._container.innerHTML = `<div class="file-preview-placeholder">
      <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10"/>
        <line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <p><strong>${this._escapeHtml(title)}</strong></p>
      <p style="font-size:13px; opacity:0.8;">${this._escapeHtml(detail)}</p>
      ${canShowInFolder
        ? `<button class="file-preview-open-folder-btn"
             onclick="HippoDesktop.showItemInFolder('${this._escapeHtml(filePath)}').catch(()=>{})">
             <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
               <path d="M2 3.5h5l2 2h5a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z"/>
             </svg>
             在文件管理器中查看
           </button>`
        : ''}
    </div>`;
    this._updateSaveBtn();
    this._onError(new Error(`${title}: ${detail}`));
  }

  /** 判断是否为 CSV 文件 */
  _isCsv(filePath) {
    return filePath && filePath.toLowerCase().endsWith('.csv');
  }

  /**
   * 对 CSV 字节数组做编码检测和转换，返回 UTF-8 编码的 Uint8Array。
   *
   * 检测策略：
   *   1. 检查 UTF-8 BOM → 去除 BOM，直接按 UTF-8 使用
   *   2. 尝试 UTF-8 解码（fatal 模式）→ 成功则为合法 UTF-8
   *   3. 失败 → 按 GBK 解码，再重新编码为 UTF-8 字节
   */
  _decodeCSVBytes(arrayBuffer) {
    const bytes = new Uint8Array(arrayBuffer);
    if (bytes.length === 0) return bytes;

    // 1. 检查 UTF-8 BOM（EF BB BF）
    if (bytes.length >= 3 && bytes[0] === 0xEF && bytes[1] === 0xBB && bytes[2] === 0xBF) {
      return bytes.slice(3);
    }

    // 2. 尝试 UTF-8 解码（fatal 模式：遇到非法序列抛异常）
    try {
      const decoder = new TextDecoder('utf-8', { fatal: true });
      decoder.decode(bytes);
      // 合法 UTF-8，直接返回原字节
      return bytes;
    } catch (_) {
      // 3. UTF-8 解码失败 → 按 GBK 解码，再转回 UTF-8 字节
      try {
        const gbkDecoder = new TextDecoder('gbk');
        const text = gbkDecoder.decode(bytes);
        const utf8Encoder = new TextEncoder();
        return utf8Encoder.encode(text);
      } catch (e) {
        console.warn('FilePreview: CSV encoding fallback failed, using raw bytes', e);
        return bytes;
      }
    }
  }

  /** 格式化字节数 */
  _formatFileSize(bytes) {
    if (!bytes || bytes === 0) return '';
    if (bytes < 1024) return bytes + 'B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + 'MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(1) + 'GB';
  }

  _showError(message) {
    this._destroyEditor();
    this._container.innerHTML = `<div class="file-preview-placeholder">
      <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10"/>
        <line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <p>${this._escapeHtml(message)}</p>
    </div>`;
    this._updateSaveBtn();
  }

  _escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
}
