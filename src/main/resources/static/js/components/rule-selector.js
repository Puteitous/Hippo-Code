import { apiGet } from '../utils.js';

/**
 * 规则选择器组件。
 * 从 /api/rules/list 加载规则列表，展示为悬浮框，支持多选 manual 规则。
 * 新建规则请前往 Activity Bar 规则管理面板。
 */
export class RuleSelector {
  constructor({ onRulesChange }) {
    this._rules = [];       // 所有可用规则
    this._selected = new Set(); // 已选的规则 ID
    this._onRulesChange = onRulesChange;
    this._loading = false;
    this._loaded = false;
    this._panel = null;
    this._btn = null;

    this._createButton();
  }

  // ==================== 公开方法 ====================

  getSelectedRuleIds() {
    return Array.from(this._selected);
  }

  clearSelection() {
    this._selected.clear();
    this._updateButtonLabel();
  }

  destroy() {
    this._btn?.remove();
    this._panel?.remove();
  }

  // ==================== UI 创建 ====================

  _createButton() {
    this._btn = document.createElement('button');
    this._btn.className = 'rule-selector-btn';
    this._btn.title = '引用规则';
    this._btn.innerHTML = this._getButtonHTML(0);
    this._btn.addEventListener('click', () => this._togglePanel());
  }

  _getButtonHTML(count) {
    if (count > 0) {
      return `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
        <span class="rule-selector-badge">${count}</span>`;
    }
    return `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>`;
  }

  _updateButtonLabel() {
    if (!this._btn) return;
    this._btn.innerHTML = this._getButtonHTML(this._selected.size);
  }

  getButtonElement() {
    return this._btn;
  }

  // ==================== 面板 ====================

  async _togglePanel() {
    if (this._panel && this._panel.parentNode) {
      this._closePanel();
      return;
    }

    if (!this._loaded && !this._loading) {
      this._loading = true;
      await this._loadRules();
      this._loading = false;
      this._loaded = true;
    }

    this._openPanel();
  }

  _openPanel() {
    this._panel = document.createElement('div');
    this._panel.className = 'rule-selector-panel';

    const header = document.createElement('div');
    header.className = 'rule-selector-header';
    header.textContent = '引用规则';
    this._panel.appendChild(header);

    if (this._rules.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'rule-selector-empty';
      empty.innerHTML = '暂无可用规则<br><span style="font-size:11px;opacity:0.6;">前往左侧活动栏创建规则</span>';
      this._panel.appendChild(empty);
    } else {
      // 分组：always / manual
      const alwaysRules = this._rules.filter(r => r.mode === 'always');
      const manualRules = this._rules.filter(r => r.mode !== 'always');

      if (alwaysRules.length > 0) {
        this._appendGroup(this._panel, '始终生效', alwaysRules, true);
      }
      if (manualRules.length > 0) {
        this._appendGroup(this._panel, '手动引用', manualRules, false);
      }
    }

    document.body.appendChild(this._panel);
    this._positionPanel();

    // 点击外部关闭
    this._outsideClickHandler = (e) => {
      if (!this._panel.contains(e.target) && e.target !== this._btn) {
        this._closePanel();
      }
    };
    setTimeout(() => document.addEventListener('click', this._outsideClickHandler), 0);
  }

  _appendGroup(panel, label, rules, disabled) {
    const group = document.createElement('div');
    group.className = 'rule-selector-group';

    const groupLabel = document.createElement('div');
    groupLabel.className = 'rule-selector-group-label';
    groupLabel.textContent = label;
    group.appendChild(groupLabel);

    for (const rule of rules) {
      const item = document.createElement('div');
      item.className = 'rule-selector-item';
      if (this._selected.has(rule.id)) {
        item.classList.add('selected');
      }

      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.checked = this._selected.has(rule.id);
      checkbox.disabled = disabled;
      checkbox.addEventListener('change', () => {
        if (checkbox.checked) {
          this._selected.add(rule.id);
          item.classList.add('selected');
        } else {
          this._selected.delete(rule.id);
          item.classList.remove('selected');
        }
        this._updateButtonLabel();
        this._onRulesChange?.(this.getSelectedRuleIds());
      });

      const info = document.createElement('div');
      info.className = 'rule-selector-item-info';

      const name = document.createElement('div');
      name.className = 'rule-selector-item-name';
      name.textContent = rule.name;
      info.appendChild(name);

      if (rule.description && rule.description !== rule.name) {
        const desc = document.createElement('div');
        desc.className = 'rule-selector-item-desc';
        desc.textContent = rule.description;
        info.appendChild(desc);
      }

      item.appendChild(checkbox);
      item.appendChild(info);
      group.appendChild(item);
    }

    panel.appendChild(group);
  }

  _closePanel() {
    if (this._outsideClickHandler) {
      document.removeEventListener('click', this._outsideClickHandler);
      this._outsideClickHandler = null;
    }
    this._panel?.remove();
    this._panel = null;
  }

  _positionPanel() {
    if (!this._panel || !this._btn) return;
    const btnRect = this._btn.getBoundingClientRect();
    const panelWidth = 320;
    let left = btnRect.left;
    if (left + panelWidth > window.innerWidth - 16) {
      left = window.innerWidth - panelWidth - 16;
    }
    this._panel.style.position = 'fixed';
    this._panel.style.left = left + 'px';
    this._panel.style.bottom = (window.innerHeight - btnRect.top + 8) + 'px';
    this._panel.style.width = panelWidth + 'px';
  }

  // ==================== 数据加载 ====================

  async _loadRules() {
    try {
      const data = await apiGet('/api/rules/list');
      this._rules = [];

      if (data.projectRules) {
        for (const r of data.projectRules) {
          this._rules.push({ ...r, source: 'project' });
        }
      }
      if (data.userRules) {
        for (const r of data.userRules) {
          this._rules.push({ ...r, source: 'user' });
        }
      }
    } catch (e) {
      console.warn('加载规则列表失败:', e);
    }
  }
}
