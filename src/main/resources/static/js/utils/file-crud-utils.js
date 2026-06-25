import { apiGet, apiPost } from '../utils.js';

/**
 * 创建通用的文件编辑详情弹窗（Raw 编辑器 + 元数据表单）。
 * 规则和技能共用同一套弹窗结构，通过 fields 配置差异化字段。
 *
 * @param {object} options
 * @param {string} options.title - 弹窗标题
 * @param {object} options.item - 当前编辑的条目
 * @param {string} options.source - 'project' | 'user'
 * @param {string} options.fetchUrl - 读取文件内容的 API URL
 * @param {string} options.saveUrl - 保存的 API URL
 * @param {Array<object>} options.fields - 额外的元数据字段配置
 *   [{ id, label, type: 'text'|'toggle', value, options?: [{value, label}], onChange? }]
 * @param {function} options.onSaved - 保存成功后的回调
 */
export function createDetailModal({ title, item, source, fetchUrl, saveUrl, fields, onSaved }) {
  const overlay = document.createElement('div');
  overlay.className = 'file-detail-overlay';

  const modal = document.createElement('div');
  modal.className = 'file-detail-modal';

  // ── 字段容器（存值的 map） ──
  const fieldValues = { name: item.name || item.fileName?.replace(/\.md$/, '') || '', description: item.description || '', scope: source };

  // ── 动态构建元数据字段 ──
  for (const f of fields) {
    if (f.type === 'toggle') {
      fieldValues[f.id] = f.value || (f.options?.[0]?.value) || '';
    }
  }

  // ── 名称输入 ──
  const nameGroup = createFieldGroup('名称', 'detail-file-name', 'text', fieldValues.name);
  modal.appendChild(nameGroup);
  const nameInput = nameGroup.querySelector('input');

  // ── 描述输入 ──
  const descGroup = createFieldGroup('描述', 'detail-file-desc', 'text', fieldValues.description, '简短说明，前端展示用');
  modal.appendChild(descGroup);
  const descInput = descGroup.querySelector('input');

  // ── 作用域切换 ──
  const scopeGroup = createToggleGroup('作用域', 'detail-file-scope', [
    { value: 'project', label: '项目' },
    { value: 'user', label: '全局' }
  ], source);
  modal.appendChild(scopeGroup);

  // ── 额外字段（如规则的 mode） ──
  const extraFieldEls = [];
  for (const f of fields) {
    if (f.type === 'toggle') {
      const group = createToggleGroup(f.label, f.id, f.options, fieldValues[f.id]);
      modal.appendChild(group);
      extraFieldEls.push(group);
    }
  }

  // ── 分隔线 ──
  const divider = document.createElement('div');
  divider.className = 'file-detail-divider';
  modal.appendChild(divider);

  // ── 文件路径提示 ──
  const fileHint = document.createElement('div');
  fileHint.className = 'file-detail-file-hint';
  fileHint.textContent = `📄 ${item.fileName || item.name + '.md'}`;
  modal.appendChild(fileHint);

  // ── 加载状态 ──
  const loadingEl = document.createElement('div');
  loadingEl.className = 'file-detail-loading';
  loadingEl.textContent = '加载中...';
  modal.appendChild(loadingEl);

  // ── Raw 编辑器 ──
  const editorWrap = document.createElement('div');
  editorWrap.className = 'file-detail-editor-wrap';
  editorWrap.style.display = 'none';

  const textarea = document.createElement('textarea');
  textarea.className = 'file-detail-textarea';
  textarea.id = 'detail-file-content';
  textarea.spellcheck = false;
  editorWrap.appendChild(textarea);
  modal.appendChild(editorWrap);

  // ── 状态提示 ──
  const statusEl = document.createElement('div');
  statusEl.className = 'file-detail-status';
  statusEl.id = 'detailFileStatus';
  statusEl.style.display = 'none';
  modal.appendChild(statusEl);

  // ── 按钮区 ──
  const actions = document.createElement('div');
  actions.className = 'file-detail-actions';

  const saveBtn = document.createElement('button');
  saveBtn.className = 'file-detail-btn file-detail-btn-primary';
  saveBtn.textContent = '保存';
  saveBtn.style.display = 'none';

  const closeBtn = document.createElement('button');
  closeBtn.className = 'file-detail-btn file-detail-btn-ghost';
  closeBtn.textContent = '取消';
  closeBtn.addEventListener('click', () => overlay.remove());

  saveBtn.addEventListener('click', () => handleSave());

  actions.appendChild(closeBtn);
  actions.appendChild(saveBtn);
  modal.appendChild(actions);

  overlay.appendChild(modal);
  document.body.appendChild(overlay);

  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) overlay.remove();
  });

  // ── 加载文件内容 ──
  loadContent();

  async function loadContent() {
    try {
      const data = await apiGet(`${fetchUrl}?filePath=${encodeURIComponent(item.filePath)}`);
      loadingEl.remove();
      editorWrap.style.display = 'block';
      textarea.value = data.content;
      saveBtn.style.display = 'inline-flex';
    } catch (e) {
      console.warn('加载文件内容失败:', e);
      loadingEl.textContent = '加载失败，请重试';
      loadingEl.classList.add('file-detail-loading-error');
    }
  }

  async function handleSave() {
    const name = nameInput.value.trim();
    const description = descInput.value.trim();
    const scope = scopeGroup.querySelector('.file-detail-toggle-btn.active')?.dataset.value || source;

    // 收集额外字段值
    const extra = {};
    for (const el of extraFieldEls) {
      const id = el.id || el.querySelector('.file-detail-toggle-btn')?.dataset.value;
      const activeBtn = el.querySelector('.file-detail-toggle-btn.active');
      if (activeBtn) {
        extra[el.id] = activeBtn.dataset.value;
      }
    }

    if (!name) {
      showStatus('⚠️ 名称不能为空', 'error');
      return;
    }

    saveBtn.disabled = true;
    saveBtn.textContent = '保存中…';

    try {
      const payload = {
        filePath: item.filePath,
        name,
        description,
        scope,
        content: textarea.value,
        ...extra
      };

      const result = await apiPost(saveUrl, payload);

      if (result.success) {
        showStatus('✓ 已保存', 'success');
        saveBtn.textContent = '✓ 已保存';
        onSaved?.(result);
      } else {
        showStatus('⚠️ ' + (result.message || '保存失败'), 'error');
        saveBtn.disabled = false;
        saveBtn.textContent = '保存';
      }
    } catch (e) {
      console.warn('保存失败:', e);
      showStatus('⚠️ 网络错误，请重试', 'error');
      saveBtn.disabled = false;
      saveBtn.textContent = '保存';
    }
  }

  function showStatus(msg, type) {
    statusEl.textContent = msg;
    statusEl.className = 'file-detail-status file-detail-status-' + type;
    statusEl.style.display = 'block';
  }
}

/**
 * 创建通用的新建文件弹窗。
 *
 * @param {object} options
 * @param {string} options.title - 弹窗标题
 * @param {string} options.createUrl - 创建 API URL
 * @param {string} options.nameHint - 名称输入框的提示文本
 * @param {Array<object>} options.extraFields - 额外 toggle 字段
 *   [{ id, label, options: [{value, label}], defaultValue }]
 * @param {function} options.onCreated - 创建成功后的回调
 */
export function createCreateModal({ title, createUrl, nameHint, extraFields, onCreated }) {
  const overlay = document.createElement('div');
  overlay.className = 'file-create-overlay';

  const modal = document.createElement('div');
  modal.className = 'file-create-modal';

  // 标题
  const titleEl = document.createElement('div');
  titleEl.className = 'file-create-title';
  titleEl.textContent = title;
  modal.appendChild(titleEl);

  // 表单
  const form = document.createElement('div');
  form.className = 'file-create-form';

  // 名称
  form.appendChild(createFieldGroup('名称', 'file-create-name', 'text', '', nameHint || 'my-file'));
  // 描述
  form.appendChild(createFieldGroup('描述（可选）', 'file-create-desc', 'text', '', '简短说明，前端展示用'));

  // 额外字段
  const extraToggleEls = [];
  if (extraFields) {
    for (const ef of extraFields) {
      const group = createToggleGroup(ef.label, ef.id, ef.options, ef.defaultValue);
      form.appendChild(group);
      extraToggleEls.push(group);
    }
  }

  // 作用域
  const scopeGroup = createToggleGroup('作用域', 'file-create-scope', [
    { value: 'project', label: '项目' },
    { value: 'user', label: '全局' }
  ], 'project');
  form.appendChild(scopeGroup);

  // 内容
  const contentWrap = document.createElement('div');
  contentWrap.className = 'file-create-field';
  const contentLabel = document.createElement('label');
  contentLabel.className = 'file-create-label';
  contentLabel.textContent = '内容（可选）';
  contentWrap.appendChild(contentLabel);
  const textarea = document.createElement('textarea');
  textarea.className = 'file-create-textarea';
  textarea.id = 'file-create-content';
  textarea.placeholder = '正文内容，不填则生成模板';
  textarea.rows = 6;
  contentWrap.appendChild(textarea);
  form.appendChild(contentWrap);

  modal.appendChild(form);

  // 按钮区
  const actions = document.createElement('div');
  actions.className = 'file-create-actions';

  const cancelBtn = document.createElement('button');
  cancelBtn.className = 'file-create-btn file-create-btn-cancel';
  cancelBtn.textContent = '取消';
  cancelBtn.addEventListener('click', () => overlay.remove());

  const saveBtn = document.createElement('button');
  saveBtn.className = 'file-create-btn file-create-btn-save';
  saveBtn.textContent = '创建';
  saveBtn.addEventListener('click', () => handleCreate());

  actions.appendChild(cancelBtn);
  actions.appendChild(saveBtn);
  modal.appendChild(actions);

  overlay.appendChild(modal);
  document.body.appendChild(overlay);

  const nameInput = modal.querySelector('#file-create-name');
  if (nameInput) nameInput.focus();

  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) overlay.remove();
  });

  async function handleCreate() {
    const name = nameInput.value.trim();
    const description = modal.querySelector('#file-create-desc')?.value?.trim() || '';
    const scope = scopeGroup.querySelector('.file-detail-toggle-btn.active')?.dataset.value || 'project';
    const content = textarea.value;

    // 收集额外字段
    const extra = {};
    if (extraFields) {
      for (const el of extraToggleEls) {
        const activeBtn = el.querySelector('.file-detail-toggle-btn.active');
        if (activeBtn) {
          extra[el.id] = activeBtn.dataset.value;
        }
      }
    }

    if (!name) {
      showError(modal, '名称不能为空');
      return;
    }

    saveBtn.disabled = true;
    saveBtn.textContent = '创建中...';

    try {
      const result = await apiPost(createUrl, { name, description, scope, content, ...extra });

      if (result.success) {
        overlay.remove();
        onCreated?.(result);
      } else {
        showError(modal, result.message || '创建失败');
        saveBtn.disabled = false;
        saveBtn.textContent = '创建';
      }
    } catch (e) {
      console.warn('创建失败:', e);
      showError(modal, '网络错误，请重试');
      saveBtn.disabled = false;
      saveBtn.textContent = '创建';
    }
  }
}

// ==================== 内部工具 ====================

function showError(modal, message) {
  const oldErr = modal.querySelector('.file-create-error');
  oldErr?.remove();
  const err = document.createElement('div');
  err.className = 'file-create-error';
  err.textContent = message;
  const actions = modal.querySelector('.file-create-actions');
  if (actions) modal.insertBefore(err, actions);
}

function createFieldGroup(label, id, type, value, placeholder) {
  const wrap = document.createElement('div');
  wrap.className = 'file-create-field';

  const lbl = document.createElement('label');
  lbl.className = 'file-create-label';
  lbl.textContent = label;
  lbl.htmlFor = id;
  wrap.appendChild(lbl);

  const input = document.createElement('input');
  input.className = 'file-create-input';
  input.id = id;
  input.type = type;
  input.value = value || '';
  if (placeholder) input.placeholder = placeholder;
  wrap.appendChild(input);

  return wrap;
}

function createToggleGroup(label, id, options, defaultValue) {
  const wrap = document.createElement('div');
  wrap.className = 'file-create-field';
  wrap.id = id;

  const lbl = document.createElement('label');
  lbl.className = 'file-create-label';
  lbl.textContent = label;
  wrap.appendChild(lbl);

  const toggle = document.createElement('div');
  toggle.className = 'file-create-toggle';

  for (const opt of options) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'file-detail-toggle-btn' + (opt.value === defaultValue ? ' active' : '');
    btn.dataset.value = opt.value;
    btn.textContent = opt.label;
    btn.addEventListener('click', () => {
      toggle.querySelectorAll('.file-detail-toggle-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    });
    toggle.appendChild(btn);
  }

  wrap.appendChild(toggle);
  return wrap;
}
