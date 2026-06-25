import { apiGet, apiPost } from '../utils.js';
import { createDetailModal, createCreateModal } from '../utils/file-crud-utils.js';

/**
 * 规则管理面板组件 — 管理项目级和用户级规则文件。
 *
 * 注册为 Activity Bar 的 'rules' 面板。
 * 功能：列表展示（按 mode 分组）、新建、编辑详情、删除。
 */
export class RulesPanel {
  constructor() {
    this._projectRules = [];
    this._userRules = [];
    this._loading = false;
    this._container = null;
  }

  /**
   * 创建面板 DOM，挂载到 Activity Bar 面板 body 中。
   */
  render() {
    this._container = document.createElement('div');
    this._container.className = 'rules-panel';

    // 头部
    const header = document.createElement('div');
    header.className = 'rules-panel-header';

    const title = document.createElement('span');
    title.className = 'rules-panel-title';
    title.textContent = '规则管理';
    header.appendChild(title);

    const actions = document.createElement('div');
    actions.className = 'rules-panel-actions';

    const refreshBtn = document.createElement('button');
    refreshBtn.className = 'rules-panel-btn rules-panel-btn-icon';
    refreshBtn.title = '刷新';
    refreshBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <polyline points="23 4 23 10 17 10"/>
      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
    </svg>`;
    refreshBtn.addEventListener('click', () => this._loadRules());
    actions.appendChild(refreshBtn);

    const createBtn = document.createElement('button');
    createBtn.className = 'rules-panel-btn rules-panel-btn-primary';
    createBtn.innerHTML = '+ 新建';
    createBtn.addEventListener('click', () => this._showCreateModal());
    actions.appendChild(createBtn);

    header.appendChild(actions);
    this._container.appendChild(header);

    // 加载状态
    this._loadingEl = document.createElement('div');
    this._loadingEl.className = 'rules-panel-loading';
    this._loadingEl.textContent = '加载中...';
    this._loadingEl.style.display = 'none';
    this._container.appendChild(this._loadingEl);

    // 错误提示
    this._errorEl = document.createElement('div');
    this._errorEl.className = 'rules-panel-error';
    this._errorEl.style.display = 'none';
    this._container.appendChild(this._errorEl);

    // 空状态
    this._emptyEl = document.createElement('div');
    this._emptyEl.className = 'rules-panel-empty';
    this._emptyEl.innerHTML = '暂无规则文件<br><span style="font-size:11px;opacity:0.6;">点击「+ 新建」创建第一条规则</span>';
    this._container.appendChild(this._emptyEl);

    // 规则列表容器
    const listContainer = document.createElement('div');
    listContainer.className = 'rules-panel-list';

    // Always 组
    this._alwaysGroup = this._createGroup('始终生效', 'always');
    listContainer.appendChild(this._alwaysGroup);

    // Manual 组
    this._manualGroup = this._createGroup('手动引用', 'manual');
    listContainer.appendChild(this._manualGroup);

    this._container.appendChild(listContainer);

    return this._container;
  }

  _createGroup(label, type) {
    const group = document.createElement('div');
    group.className = 'rules-panel-group';

    const groupHeader = document.createElement('div');
    groupHeader.className = 'rules-panel-group-header';

    const labelEl = document.createElement('span');
    labelEl.className = 'rules-panel-group-label';
    labelEl.textContent = label;

    const countEl = document.createElement('span');
    countEl.className = 'rules-panel-group-count';
    countEl.id = `ruleCount${type.charAt(0).toUpperCase() + type.slice(1)}`;
    countEl.textContent = '0';

    groupHeader.appendChild(labelEl);
    groupHeader.appendChild(countEl);
    group.appendChild(groupHeader);

    const list = document.createElement('div');
    list.className = 'rules-panel-items';
    list.id = `ruleList${type.charAt(0).toUpperCase() + type.slice(1)}`;
    group.appendChild(list);

    return group;
  }

  /**
   * 加载规则列表（公开，供 Activity Bar 回调调用）。
   */
  async _loadRules() {
    if (this._loading) return;
    this._loading = true;

    this._loadingEl.style.display = 'block';
    this._errorEl.style.display = 'none';
    this._emptyEl.style.display = 'none';

    try {
      const data = await apiGet('/api/rules/list');

      // 按 mode 分组
      this._projectRules = data.projectRules || [];
      this._userRules = data.userRules || [];

      // 合并后按 mode 分组
      const always = [];
      const manual = [];

      for (const r of this._projectRules) {
        (r.mode === 'always' ? always : manual).push({ ...r, source: 'project' });
      }
      for (const r of this._userRules) {
        (r.mode === 'always' ? always : manual).push({ ...r, source: 'user' });
      }

      this._renderList(always, manual);
    } catch (e) {
      console.warn('加载规则列表失败:', e);
      this._errorEl.textContent = '加载失败，请重试';
      this._errorEl.style.display = 'block';
    } finally {
      this._loading = false;
      this._loadingEl.style.display = 'none';
    }
  }

  _renderList(always, manual) {
    const alwaysList = this._container.querySelector('#ruleListAlways');
    const manualList = this._container.querySelector('#ruleListManual');

    this._renderGroupItems(alwaysList, always);
    this._renderGroupItems(manualList, manual);

    // 更新计数
    const alwaysCount = this._container.querySelector('#ruleCountAlways');
    const manualCount = this._container.querySelector('#ruleCountManual');
    if (alwaysCount) alwaysCount.textContent = always.length;
    if (manualCount) manualCount.textContent = manual.length;

    // 空状态
    const total = always.length + manual.length;
    this._emptyEl.style.display = total === 0 ? 'block' : 'none';
  }

  _renderGroupItems(listEl, rules) {
    if (!listEl) return;
    listEl.innerHTML = '';

    if (rules.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'rules-panel-empty-item';
      empty.textContent = '暂无';
      listEl.appendChild(empty);
      return;
    }

    for (const rule of rules) {
      const item = document.createElement('div');
      item.className = 'rules-panel-item';
      item.addEventListener('click', () => this._showDetailModal(rule));

      const icon = document.createElement('span');
      icon.className = 'rules-panel-item-icon';
      icon.textContent = rule.mode === 'always' ? '⚡' : '📋';
      icon.title = rule.mode === 'always' ? '始终生效' : '手动引用';
      item.appendChild(icon);

      const info = document.createElement('div');
      info.className = 'rules-panel-item-info';

      const name = document.createElement('div');
      name.className = 'rules-panel-item-name';
      name.textContent = rule.name;
      info.appendChild(name);

      if (rule.description && rule.description !== rule.name) {
        const desc = document.createElement('div');
        desc.className = 'rules-panel-item-desc';
        desc.textContent = rule.description;
        info.appendChild(desc);
      }

      // 来源标识
      const sourceBadge = document.createElement('span');
      sourceBadge.className = 'rules-panel-item-badge';
      sourceBadge.textContent = rule.source === 'project' ? '项目' : '全局';
      info.appendChild(sourceBadge);

      item.appendChild(info);

      // 删除按钮
      const delBtn = document.createElement('button');
      delBtn.className = 'rules-panel-item-del';
      delBtn.title = '删除';
      delBtn.innerHTML = `<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="3 6 5 6 21 6"/>
        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
      </svg>`;
      delBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this._deleteRule(rule);
      });
      item.appendChild(delBtn);

      listEl.appendChild(item);
    }
  }

  _showDetailModal(rule) {
    createDetailModal({
      title: '编辑规则',
      item: rule,
      source: rule.source,
      fetchUrl: '/api/rules/get',
      saveUrl: '/api/rules/update',
      fields: [
        {
          id: 'mode',
          label: '模式',
          type: 'toggle',
          value: rule.mode || 'always',
          options: [
            { value: 'always', label: '始终生效' },
            { value: 'manual', label: '手动引用' }
          ]
        }
      ],
      onSaved: () => this._loadRules()
    });
  }

  _showCreateModal() {
    createCreateModal({
      title: '新建规则',
      createUrl: '/api/rules/create',
      nameHint: 'my-rule（字母、数字、连字符、下划线、点）',
      extraFields: [
        {
          id: 'mode',
          label: '模式',
          options: [
            { value: 'always', label: '始终生效' },
            { value: 'manual', label: '手动引用' }
          ],
          defaultValue: 'always'
        }
      ],
      onCreated: () => this._loadRules()
    });
  }

  async _deleteRule(rule) {
    if (!confirm(`确定删除规则「${rule.name}」？`)) return;

    try {
      const result = await apiPost('/api/rules/delete', { filePath: rule.filePath });

      if (result.success) {
        this._loadRules();
      } else {
        alert('删除失败: ' + (result.message || '未知错误'));
      }
    } catch (e) {
      console.warn('删除规则失败:', e);
      alert('删除失败，请重试');
    }
  }
}
