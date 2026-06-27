/**
 * SettingsPanel — 全屏设置面板
 *
 * 左侧导航竖条 + 右侧内容区
 * 聚合：模型配置、规则管理、技能管理
 */
import { apiGet, apiPost } from '../utils.js';
import { CustomDropdown } from '../utils/dropdown.js';
import { showToast } from '../utils/toast.js';
import { getFileIconInfo } from '../utils/file-icons.js';
import { ConfirmDialog } from '../utils/modal.js';


/** Provider 可选列表（与 main.js 一致） */
const PROVIDER_ITEMS = [
  { label: 'DashScope', value: 'dashscope' },
  { label: 'OpenAI', value: 'openai' },
  { label: 'DeepSeek', value: 'deepseek' },
  { label: '智谱 GLM', value: 'zhipu' },
  { label: 'Kimi (月之暗面)', value: 'moonshot' },
  { label: 'MiniMax', value: 'minimax' },
  { label: '阶跃星辰', value: 'stepfun' },
  { label: '零一万物', value: 'lingyi' },
  { label: '豆包 (字节)', value: 'doubao' },
  { label: '硅基流动', value: 'siliconflow' },
  { label: '讯飞星火', value: 'xunfei' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: 'Ollama', value: 'ollama' },
  { label: 'Local', value: 'local' },
];

/** 导航项定义 */
const NAV_ITEMS = [
  { id: 'model', label: '模型配置', icon: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z' },
  { id: 'rules', label: '规则管理', icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z' },
  { id: 'skills', label: '技能管理', icon: 'M22 11.08V12a10 10 0 1 1-5.93-9.14' },
  { id: 'general', label: '通用设置', icon: 'M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z' },
];

export class SettingsPanel {
  constructor() {
    this._overlay = null;
    this._contentEl = null;
    this._navItems = [];
    this._activePage = 'model';
    this._mainContainer = document.querySelector('.main-container');
    this._chatPanel = document.querySelector('.chat-panel');

    // 模型配置状态
    this._providerDropdown = null;

    // 规则/技能状态
    this._rules = [];
    this._projectSkills = [];
    this._userSkills = [];

    // 编辑状态
    this._editingRule = null;
    this._editingSkill = null;

    this._init();
  }

  // ==================== 生命周期 ====================

  open() {
    if (!this._overlay) this._init();
    if (this._chatPanel) this._chatPanel.style.display = 'none';
    this._overlay.style.display = 'flex';

    // 根据预览面板状态决定宽度策略：
    //   preview 隐藏 → flex: 1 铺满剩余空间
    //   preview 显示 → 恢复保存的固定宽度（如果有）
    const preview = document.querySelector('.preview-panel');
    const isPreviewHidden = !preview || preview.classList.contains('hidden');
    if (isPreviewHidden) {
      this._overlay.style.width = '';
      this._overlay.style.flex = '';
    } else {
      const savedWidth = localStorage.getItem('hippo-settings-width');
      if (savedWidth) {
        this._overlay.style.width = savedWidth + 'px';
        this._overlay.style.flex = 'none';
      } else {
        this._overlay.style.width = '';
        this._overlay.style.flex = '';
      }
    }

    this._switchPage(this._activePage);
  }

  close() {
    if (this._overlay) {
      this._overlay.style.display = 'none';
    }
    if (this._chatPanel) {
      this._chatPanel.style.display = '';
    }
  }

  isOpen() {
    return this._overlay && this._overlay.style.display !== 'none';
  }

  toggle() {
    if (this.isOpen()) {
      this.close();
    } else {
      this.open();
    }
  }

  // ==================== 初始化 DOM ====================

  _init() {
    this._overlay = document.createElement('div');
    this._overlay.className = 'settings-overlay';
    this._overlay.style.display = 'none';

    // ── 关闭按钮 ──
    const closeBtn = document.createElement('button');
    closeBtn.className = 'settings-close-btn';
    closeBtn.title = '关闭设置 (Esc)';
    closeBtn.innerHTML = '<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="3" x2="13" y2="13"/><line x1="13" y1="3" x2="3" y2="13"/></svg>';
    closeBtn.addEventListener('click', () => this.close());
    this._overlay.appendChild(closeBtn);

    // ── 宽度拖拽条（左侧边缘） ──
    const resizer = document.createElement('div');
    resizer.className = 'settings-resizer';
    resizer.title = '拖拽调整宽度';
    this._overlay.appendChild(resizer);

    // 恢复上次保存的宽度
    const savedWidth = localStorage.getItem('hippo-settings-width');
    if (savedWidth) {
      this._overlay.style.width = savedWidth + 'px';
      this._overlay.style.flex = 'none';
    }

    // 拖拽逻辑
    resizer.addEventListener('mousedown', (e) => {
      e.preventDefault();
      resizer.classList.add('resizing');
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      const startX = e.clientX;
      const startWidth = this._overlay.offsetWidth;

      const onMove = (ev) => {
        // 向左拖 = 宽度增加（resizer 在面板左侧边缘）
        const diff = startX - ev.clientX;
        const w = Math.max(420, Math.min(960, startWidth + diff));
        this._overlay.style.width = w + 'px';
        this._overlay.style.flex = 'none';
      };

      const onUp = () => {
        resizer.classList.remove('resizing');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        const w = parseInt(this._overlay.style.width, 10);
        if (w && w > 0) localStorage.setItem('hippo-settings-width', String(w));
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
      };

      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });

    // ── 主体 ──
    const body = document.createElement('div');
    body.className = 'settings-body';

    // 左侧导航
    const nav = document.createElement('nav');
    nav.className = 'settings-nav';
    this._navEl = nav;

    for (const item of NAV_ITEMS) {
      const navItem = document.createElement('button');
      navItem.className = 'settings-nav-item';
      navItem.dataset.page = item.id;

      const icon = document.createElement('span');
      icon.className = 'settings-nav-icon';
      icon.innerHTML = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="${item.icon}"/></svg>`;
      navItem.appendChild(icon);

      const label = document.createElement('span');
      label.textContent = item.label;
      navItem.appendChild(label);

      navItem.addEventListener('click', () => this._switchPage(item.id));
      nav.appendChild(navItem);
      this._navItems.push(navItem);
    }

    body.appendChild(nav);

    // 右侧内容区
    const content = document.createElement('div');
    content.className = 'settings-content';
    this._contentEl = content;
    body.appendChild(content);

    this._overlay.appendChild(body);

    // ── 监听预览面板显示/隐藏，动态切换宽度策略 ──
    this._previewObserver = new MutationObserver(() => {
      if (!this.isOpen()) return;
      const preview = document.querySelector('.preview-panel');
      const isHidden = !preview || preview.classList.contains('hidden');
      if (isHidden) {
        this._overlay.style.width = '';
        this._overlay.style.flex = '';
      } else {
        const savedWidth = localStorage.getItem('hippo-settings-width');
        if (savedWidth) {
          this._overlay.style.width = savedWidth + 'px';
          this._overlay.style.flex = 'none';
        }
      }
    });
    const previewTarget = document.querySelector('.preview-panel');
    if (previewTarget) {
      this._previewObserver.observe(previewTarget, { attributes: true, attributeFilter: ['class'] });
    }

    // ── 键盘关闭 ──
    this._onKeyDown = (e) => {
      if (e.key === 'Escape' && this.isOpen()) {
        this.close();
      }
    };
    document.addEventListener('keydown', this._onKeyDown);

    if (this._mainContainer) {
      this._mainContainer.appendChild(this._overlay);
    }
  }

  destroy() {
    if (this._overlay) {
      this._overlay.remove();
      this._overlay = null;
    }
    if (this._chatPanel) {
      this._chatPanel.style.display = '';
    }
    if (this._onKeyDown) {
      document.removeEventListener('keydown', this._onKeyDown);
    }
    if (this._previewObserver) {
      this._previewObserver.disconnect();
      this._previewObserver = null;
    }
  }

  // ==================== 页面切换 ====================

  _switchPage(pageId) {
    this._activePage = pageId;

    // 更新导航高亮
    for (const item of this._navItems) {
      item.classList.toggle('active', item.dataset.page === pageId);
    }

    // 清除编辑状态
    this._editingRule = null;
    this._editingSkill = null;

    // 渲染对应页面
    switch (pageId) {
      case 'model':
        this._renderModelPage();
        break;
      case 'rules':
        this._renderRulesPage();
        break;
      case 'skills':
        this._renderSkillsPage();
        break;
      case 'general':
        this._renderGeneralPage();
        break;
    }
  }

  // ==================== 模型配置页面 ====================

  async _renderModelPage() {
    // 销毁旧 Provider 下拉，避免 DOM 重建后 trigger 失效
    if (this._providerDropdown) {
      this._providerDropdown.destroy();
      this._providerDropdown = null;
    }

    const page = document.createElement('div');
    page.className = 'settings-page';

    page.innerHTML = `
      <h2 class="settings-page-title">模型配置</h2>
      <p class="settings-page-desc">配置 AI 聊天模型 Provider、API Key 等参数</p>
      <hr class="settings-page-divider">

      <div class="settings-field">
        <label class="settings-field-label">已添加的模型</label>
        <div class="settings-model-list" id="settingsModelList">
          <div class="settings-model-empty">暂无已添加的模型</div>
        </div>
      </div>

      <hr class="settings-page-divider">

      <div class="settings-form" id="settingsModelForm">
        <div class="settings-field">
          <label class="settings-field-label">Provider</label>
          <div class="settings-provider-wrap">
            <button class="settings-input settings-provider-btn" id="settingsProvider">DashScope</button>
          </div>
        </div>
        <div class="settings-field">
          <label class="settings-field-label" for="settingsModel">Model</label>
          <input class="settings-input" id="settingsModel" type="text" placeholder="例如 qwen3.5-plus">
        </div>
        <div class="settings-field">
          <label class="settings-field-label" for="settingsApiKey">API Key</label>
          <div class="settings-input-wrap">
            <input class="settings-input" id="settingsApiKey" type="password" placeholder="输入 API Key">
            <button class="settings-input-btn" id="settingsApiKeyToggle" title="显示/隐藏">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
            </button>
          </div>
        </div>
        <div class="settings-field">
          <label class="settings-field-label" for="settingsBaseUrl">Base URL</label>
          <input class="settings-input" id="settingsBaseUrl" type="text" placeholder="https://dashscope.aliyuncs.com">
        </div>
        <div class="settings-field">
          <label class="settings-field-label" for="settingsMaxTokens">
            Max Tokens <span class="settings-field-hint">(单次输出上限, 0=不限制)</span>
          </label>
          <input class="settings-input" id="settingsMaxTokens" type="number" min="0" placeholder="0">
        </div>
        <div class="settings-field desktop-only">
          <label class="settings-field-label" for="settingsDefaultWorkspace">
            默认工作区路径 <span class="settings-field-hint">(留空使用内置默认)</span>
          </label>
          <div class="settings-input-wrap">
            <input class="settings-input" id="settingsDefaultWorkspace" type="text" placeholder="留空则使用内置默认路径">
            <button class="settings-input-btn" id="settingsDefaultWorkspaceBrowse" title="选择文件夹">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
      <div class="settings-save-bar">
        <button class="settings-save-btn" id="settingsModelSave">保存配置</button>
      </div>
    `;

    this._setContent(page);

    // 绑定事件
    this._bindModelEvents();
    await this._loadModelConfig();
  }

  _bindModelEvents() {
    // Provider 下拉
    const providerBtn = document.getElementById('settingsProvider');
    if (providerBtn && !this._providerDropdown) {
      this._providerDropdown = new CustomDropdown({
        trigger: providerBtn,
        items: PROVIDER_ITEMS,
        placement: 'bottom-left',
      });
    }

    // API Key 显示/隐藏
    const toggleBtn = document.getElementById('settingsApiKeyToggle');
    const apiKeyInput = document.getElementById('settingsApiKey');
    if (toggleBtn && apiKeyInput) {
      toggleBtn.addEventListener('click', () => {
        const isPassword = apiKeyInput.type === 'password';
        apiKeyInput.type = isPassword ? 'text' : 'password';
        toggleBtn.innerHTML = isPassword
          ? `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>`
          : `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>`;
      });
    }

    // 保存
    const saveBtn = document.getElementById('settingsModelSave');
    if (saveBtn) {
      saveBtn.addEventListener('click', () => this._saveModelConfig());
    }
  }

  async _loadModelConfig() {
    try {
      const data = await apiGet('/api/config/llm');

      // Provider
      if (this._providerDropdown) {
        this._providerDropdown.setSelectedValue(data.provider || 'dashscope');
      }

      const modelInput = document.getElementById('settingsModel');
      const baseUrlInput = document.getElementById('settingsBaseUrl');
      const maxTokensInput = document.getElementById('settingsMaxTokens');
      const apiKeyInput = document.getElementById('settingsApiKey');

      if (modelInput) modelInput.value = data.model || '';
      if (baseUrlInput) baseUrlInput.value = data.baseUrl || '';
      if (maxTokensInput) maxTokensInput.value = data.maxTokens || '';

      if (data.hasApiKey) {
        apiKeyInput.value = data.apiKeyMasked || '';
        apiKeyInput.dataset.masked = 'true';
      } else {
        apiKeyInput.value = '';
        delete apiKeyInput.dataset.masked;
      }

      this._renderModelHistoryList(data);
    } catch (e) {
      console.warn('加载模型配置失败:', e);
      showToast('加载模型配置失败', 'error');
    }
  }

  async _saveModelConfig() {
    const body = {
      provider: this._providerDropdown ? this._providerDropdown.getSelectedItem()?.value || 'dashscope' : 'dashscope',
      model: document.getElementById('settingsModel')?.value || '',
      baseUrl: document.getElementById('settingsBaseUrl')?.value || '',
      apiKey: document.getElementById('settingsApiKey')?.value || '',
      maxTokens: document.getElementById('settingsMaxTokens')?.value
        ? parseInt(document.getElementById('settingsMaxTokens').value, 10)
        : undefined,
    };

    const apiKeyInput = document.getElementById('settingsApiKey');
    if (apiKeyInput?.dataset.masked === 'true') {
      delete body.apiKey;
    }

    try {
      const resp = await fetch('/api/config/llm', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!resp.ok) throw new Error(await resp.text());

      showToast('配置已保存', { type: 'success', duration: 2000 });
      // 刷新列表
      await this._loadModelConfig();
    } catch (e) {
      showToast('保存失败: ' + e.message, { type: 'error', duration: 3000 });
    }
  }

  _renderModelHistoryList(data) {
    const list = document.getElementById('settingsModelList');
    if (!list) return;

    const models = data.modelHistory || [];

    if (models.length === 0) {
      list.innerHTML = '<div class="settings-model-empty">暂无已添加的模型</div>';
      return;
    }

    list.innerHTML = models.map((m, i) => `
      <div class="settings-model-item ${i === 0 ? 'active' : ''}">
        <div class="settings-model-item-info">
          <div class="settings-model-item-provider">${m.provider || ''}</div>
          <div class="settings-model-item-model">${m.model || m.name || ''}</div>
        </div>
        <div class="settings-model-item-actions">
          <button class="settings-model-item-delete" data-provider="${m.provider || ''}" data-model="${m.model || ''}" title="删除">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
          </button>
        </div>
      </div>
    `).join('');

    // 绑定事件
    list.querySelectorAll('.settings-model-item').forEach((card, i) => {
      const m = models[i];
      if (!m) return;

      // 整张卡片可点击 → 填入表单 + 高亮
      card.addEventListener('click', (e) => {
        // 点删除按钮不触发
        if (e.target.closest('.settings-model-item-delete')) return;
        // 移除其他卡片的高亮
        list.querySelectorAll('.settings-model-item.active').forEach(c => c.classList.remove('active'));
        card.classList.add('active');
        this._fillModelForm(m);
      });
    });

    list.querySelectorAll('.settings-model-item-delete').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const provider = btn.dataset.provider;
        const model = btn.dataset.model;
        if (!provider || !model) return;
        const confirmed = await ConfirmDialog.confirmDelete(`确定从历史记录中删除模型「${provider}:${model}」？`);
        if (!confirmed) return;

        try {
          const result = await apiPost('/api/config/llm/history', { provider, model }, 'DELETE');
          if (result.success) {
            showToast('已删除模型: ' + provider + ' · ' + model, { type: 'success', duration: 2000 });
            this._loadModelConfig();
          } else {
            showToast('删除失败: ' + (result.message || '未知错误'), { type: 'error', duration: 3000 });
          }
        } catch (e) {
          console.warn('删除模型失败:', e);
          showToast('删除失败: ' + e.message, { type: 'error', duration: 3000 });
        }
      });
    });
  }

  /** 将历史模型填入当前表单 */
  _fillModelForm(m) {
    if (this._providerDropdown) {
      this._providerDropdown.setSelectedValue(m.provider || 'dashscope');
    }
    const modelInput = document.getElementById('settingsModel');
    const baseUrlInput = document.getElementById('settingsBaseUrl');
    const maxTokensInput = document.getElementById('settingsMaxTokens');
    if (modelInput) modelInput.value = m.model || '';
    if (baseUrlInput) baseUrlInput.value = m.baseUrl || '';
    if (maxTokensInput) maxTokensInput.value = m.maxTokens || '';
    showToast('已填入模型配置，点击「保存配置」生效', { type: 'info', duration: 2000 });
  }

  // ==================== 规则管理页面 ====================

  async _renderRulesPage() {
    const page = document.createElement('div');
    page.className = 'settings-page';

    page.innerHTML = `
      <h2 class="settings-page-title">规则管理</h2>
      <p class="settings-page-desc">管理项目级和用户级规则文件，按「始终生效」和「手动引用」分组</p>
      <hr class="settings-page-divider">

      <div class="settings-item-list-header">
        <h3>规则列表</h3>
        <div class="settings-item-list-actions">
          <button class="settings-btn settings-btn-icon" id="settingsRulesRefresh" title="刷新">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </button>
          <button class="settings-btn settings-btn-primary" id="settingsRulesCreate">+ 新建</button>
        </div>
      </div>

      <div class="settings-loading" id="settingsRulesLoading" style="display:none;">加载中...</div>
      <div class="settings-items-error" id="settingsRulesError" style="display:none;"></div>
      <div id="settingsRulesList"></div>
    `;

    this._setContent(page);

    document.getElementById('settingsRulesRefresh')?.addEventListener('click', () => this._loadRules());
    document.getElementById('settingsRulesCreate')?.addEventListener('click', () => this._showCreateRuleModal());

    await this._loadRules();
  }

  async _loadRules() {
    const loadingEl = document.getElementById('settingsRulesLoading');
    const errorEl = document.getElementById('settingsRulesError');
    const listEl = document.getElementById('settingsRulesList');
    if (!listEl) return;

    if (loadingEl) loadingEl.style.display = 'block';
    if (errorEl) errorEl.style.display = 'none';

    try {
      const data = await apiGet('/api/rules/list');
      const projectRules = data.projectRules || [];
      const userRules = data.userRules || [];

      const always = [];
      const manual = [];

      for (const r of projectRules) {
        (r.mode === 'always' ? always : manual).push({ ...r, source: 'project' });
      }
      for (const r of userRules) {
        (r.mode === 'always' ? always : manual).push({ ...r, source: 'user' });
      }

      this._renderRulesList(listEl, always, manual);
    } catch (e) {
      console.warn('加载规则列表失败:', e);
      if (errorEl) {
        errorEl.textContent = '加载失败，请重试';
        errorEl.style.display = 'block';
      }
    } finally {
      if (loadingEl) loadingEl.style.display = 'none';
    }
  }

  _renderRulesList(listEl, always, manual) {
    if (always.length === 0 && manual.length === 0) {
      listEl.innerHTML = '<div class="settings-items-empty">暂无规则文件<br><span style="font-size:11px;opacity:0.6;">点击「+ 新建」创建第一条规则</span></div>';
      return;
    }

    listEl.innerHTML = '';

    if (always.length > 0) {
      listEl.appendChild(this._createRuleGroup('始终生效', always, '⚡'));
    }
    if (manual.length > 0) {
      listEl.appendChild(this._createRuleGroup('手动引用', manual, '📋'));
    }
  }

  _createRuleGroup(label, rules, icon) {
    const group = document.createElement('div');
    group.className = 'settings-item-group';

    const header = document.createElement('div');
    header.className = 'settings-item-group-header';

    const labelEl = document.createElement('span');
    labelEl.className = 'settings-item-group-label';
    labelEl.textContent = label;
    header.appendChild(labelEl);

    const count = document.createElement('span');
    count.className = 'settings-item-group-count';
    count.textContent = rules.length;
    header.appendChild(count);

    group.appendChild(header);

    const items = document.createElement('div');
    items.className = 'settings-items';

    for (const rule of rules) {
      const item = document.createElement('div');
      item.className = 'settings-item';
      item.addEventListener('click', () => this._showRuleDetail(rule));

      const iconEl = document.createElement('span');
      iconEl.className = 'settings-item-icon';
      iconEl.textContent = icon;
      item.appendChild(iconEl);

      const info = document.createElement('div');
      info.className = 'settings-item-info';

      const name = document.createElement('div');
      name.className = 'settings-item-name';
      name.textContent = rule.name;
      info.appendChild(name);

      if (rule.description && rule.description !== rule.name) {
        const meta = document.createElement('div');
        meta.className = 'settings-item-meta';
        meta.textContent = rule.description;
        info.appendChild(meta);
      }

      item.appendChild(info);

      const badge = document.createElement('span');
      badge.className = 'settings-item-badge';
      badge.textContent = rule.source === 'project' ? '项目' : '全局';
      item.appendChild(badge);

      const delBtn = document.createElement('button');
      delBtn.className = 'settings-item-del';
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

      items.appendChild(item);
    }

    group.appendChild(items);
    return group;
  }

  _showRuleDetail(rule) {
    this._editingRule = rule;
    this._renderRuleEditor(rule);
  }

  _renderRuleEditor(rule) {
    // 把当前规则列表区替换为编辑器
    const listEl = document.getElementById('settingsRulesList');
    if (!listEl) return;

    // 隐藏新建/刷新按钮
    const headerActions = document.querySelector('#settingsRulesCreate')?.closest('.settings-item-list-actions');
    if (headerActions) headerActions.style.display = 'none';

    listEl.innerHTML = `
      <div class="settings-editor">
        <div class="settings-editor-header">
          <span class="settings-editor-title">编辑规则：${rule.name}</span>
          <div class="settings-editor-actions">
            <button class="settings-editor-btn" id="settingsRuleEditorBack">← 返回列表</button>
            <button class="settings-editor-btn settings-editor-btn-primary" id="settingsRuleEditorSave">保存</button>
          </div>
        </div>
        <div class="settings-editor-fields">
          <div class="settings-field">
            <label class="settings-field-label" for="settingsRuleEditorName">规则名称</label>
            <input class="settings-input" id="settingsRuleEditorName" type="text" value="${rule.name}">
          </div>
          <div class="settings-field">
            <label class="settings-field-label" for="settingsRuleEditorDesc">描述</label>
            <input class="settings-input" id="settingsRuleEditorDesc" type="text" value="${rule.description || ''}">
          </div>
          <div class="settings-field">
            <label class="settings-field-label">模式</label>
            <div class="settings-toggle-group" id="settingsRuleEditorMode">
              <button class="settings-toggle-btn ${rule.mode === 'always' ? 'active' : ''}" data-value="always">始终生效</button>
              <button class="settings-toggle-btn ${rule.mode !== 'always' ? 'active' : ''}" data-value="manual">手动引用</button>
            </div>
          </div>
          <div class="settings-field">
            <label class="settings-field-label">作用域</label>
            <div class="settings-toggle-group" id="settingsRuleEditorScope">
              <button class="settings-toggle-btn ${rule.source === 'project' ? 'active' : ''}" data-value="project">项目</button>
              <button class="settings-toggle-btn ${rule.source !== 'project' ? 'active' : ''}" data-value="user">全局</button>
            </div>
          </div>
        </div>
        <textarea class="settings-editor-textarea" id="settingsRuleEditorContent" placeholder="加载中..." spellcheck="false"></textarea>
        <div class="settings-editor-status" id="settingsRuleEditorStatus" style="display:none;"></div>
      </div>
    `;

    // 加载文件内容
    this._loadRuleContent(rule);

    // 绑定事件
    document.getElementById('settingsRuleEditorBack')?.addEventListener('click', () => {
      this._editingRule = null;
      if (headerActions) headerActions.style.display = '';
      this._loadRules();
    });

    document.getElementById('settingsRuleEditorSave')?.addEventListener('click', () => {
      this._saveRuleEditor(rule);
    });

    // 绑定 toggle
    document.querySelectorAll('#settingsRuleEditorMode .settings-toggle-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#settingsRuleEditorMode .settings-toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
    document.querySelectorAll('#settingsRuleEditorScope .settings-toggle-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#settingsRuleEditorScope .settings-toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
  }

  async _loadRuleContent(rule) {
    const textarea = document.getElementById('settingsRuleEditorContent');
    if (!textarea) return;

    try {
      const data = await apiGet('/api/rules/get?filePath=' + encodeURIComponent(rule.filePath));
      textarea.value = data.content || '';
      textarea.placeholder = '';
    } catch (e) {
      console.warn('加载规则内容失败:', e);
      textarea.value = '';
      textarea.placeholder = '加载失败';
    }
  }

  async _saveRuleEditor(rule) {
    const nameInput = document.getElementById('settingsRuleEditorName');
    const descInput = document.getElementById('settingsRuleEditorDesc');
    const textarea = document.getElementById('settingsRuleEditorContent');
    const modeBtn = document.querySelector('#settingsRuleEditorMode .settings-toggle-btn.active');
    const scopeBtn = document.querySelector('#settingsRuleEditorScope .settings-toggle-btn.active');
    const statusEl = document.getElementById('settingsRuleEditorStatus');
    const saveBtn = document.getElementById('settingsRuleEditorSave');

    if (!nameInput || !textarea) return;

    const name = nameInput.value.trim();
    const description = descInput?.value.trim() || '';
    const mode = modeBtn?.dataset.value || rule.mode;
    const scope = scopeBtn?.dataset.value || rule.source;
    const content = textarea.value;

    if (!name) {
      if (statusEl) {
        statusEl.textContent = '⚠️ 规则名称不能为空';
        statusEl.className = 'settings-editor-status settings-editor-status-error';
        statusEl.style.display = 'block';
      }
      return;
    }

    if (saveBtn) {
      saveBtn.disabled = true;
      saveBtn.textContent = '保存中…';
    }

    try {
      const result = await apiPost('/api/rules/update', {
        filePath: rule.filePath,
        name,
        description,
        mode,
        scope,
        content,
      });

      if (result.success) {
        if (statusEl) {
          statusEl.textContent = '✓ 规则已保存';
          statusEl.className = 'settings-editor-status settings-editor-status-success';
          statusEl.style.display = 'block';
        }
        if (saveBtn) saveBtn.textContent = '✓ 已保存';
        rule.filePath = result.filePath || rule.filePath;
        // 刷新列表
        setTimeout(() => {
          const headerActions = document.querySelector('#settingsRulesCreate')?.closest('.settings-item-list-actions');
          if (headerActions) headerActions.style.display = '';
          this._editingRule = null;
          this._loadRules();
        }, 800);
      } else {
        if (statusEl) {
          statusEl.textContent = '⚠️ ' + (result.message || '保存失败');
          statusEl.className = 'settings-editor-status settings-editor-status-error';
          statusEl.style.display = 'block';
        }
        if (saveBtn) {
          saveBtn.disabled = false;
          saveBtn.textContent = '保存';
        }
      }
    } catch (e) {
      console.warn('保存规则失败:', e);
      if (statusEl) {
        statusEl.textContent = '⚠️ 网络错误，请重试';
        statusEl.className = 'settings-editor-status settings-editor-status-error';
        statusEl.style.display = 'block';
      }
      if (saveBtn) {
        saveBtn.disabled = false;
        saveBtn.textContent = '保存';
      }
    }
  }

  async _deleteRule(rule) {
    const confirmed = await ConfirmDialog.confirmDelete(`确定删除规则「${rule.name}」？`);
    if (!confirmed) return;

    try {
      const result = await apiPost('/api/rules/delete', { filePath: rule.filePath });
      if (result.success) {
        this._loadRules();
      } else {
        showToast('删除失败: ' + (result.message || '未知错误'), { type: 'error', duration: 3000 });
      }
    } catch (e) {
      console.warn('删除规则失败:', e);
      showToast('删除失败，请重试', { type: 'error', duration: 3000 });
    }
  }

  _showCreateRuleModal() {
    const listEl = document.getElementById('settingsRulesList');
    if (!listEl) return;

    const headerActions = document.querySelector('#settingsRulesCreate')?.closest('.settings-item-list-actions');
    if (headerActions) headerActions.style.display = 'none';

    listEl.innerHTML = `
      <div class="settings-editor">
        <div class="settings-editor-header">
          <span class="settings-editor-title">新建规则</span>
          <div class="settings-editor-actions">
            <button class="settings-editor-btn" id="settingsRuleCreateBack">← 返回列表</button>
            <button class="settings-editor-btn settings-editor-btn-primary" id="settingsRuleCreateSave">创建</button>
          </div>
        </div>
        <div class="settings-editor-fields">
          <div class="settings-field">
            <label class="settings-field-label" for="settingsRuleCreateName">规则名称</label>
            <input class="settings-input" id="settingsRuleCreateName" type="text" placeholder="my-rule（字母、数字、连字符、下划线、点）">
          </div>
          <div class="settings-field">
            <label class="settings-field-label" for="settingsRuleCreateDesc">描述</label>
            <input class="settings-input" id="settingsRuleCreateDesc" type="text" placeholder="简短说明">
          </div>
          <div class="settings-field">
            <label class="settings-field-label">模式</label>
            <div class="settings-toggle-group" id="settingsRuleCreateMode">
              <button class="settings-toggle-btn active" data-value="always">始终生效</button>
              <button class="settings-toggle-btn" data-value="manual">手动引用</button>
            </div>
          </div>
          <div class="settings-field">
            <label class="settings-field-label">作用域</label>
            <div class="settings-toggle-group" id="settingsRuleCreateScope">
              <button class="settings-toggle-btn active" data-value="project">项目</button>
              <button class="settings-toggle-btn" data-value="user">全局</button>
            </div>
          </div>
        </div>
        <textarea class="settings-editor-textarea" id="settingsRuleCreateContent" placeholder="规则正文内容，Markdown 格式" spellcheck="false"></textarea>
        <div class="settings-editor-status" id="settingsRuleCreateStatus" style="display:none;"></div>
      </div>
    `;

    document.getElementById('settingsRuleCreateBack')?.addEventListener('click', () => {
      if (headerActions) headerActions.style.display = '';
      this._loadRules();
    });

    document.getElementById('settingsRuleCreateSave')?.addEventListener('click', () => this._handleCreateRule());

    // 绑定 toggle
    document.querySelectorAll('#settingsRuleCreateMode .settings-toggle-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#settingsRuleCreateMode .settings-toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
    document.querySelectorAll('#settingsRuleCreateScope .settings-toggle-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#settingsRuleCreateScope .settings-toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
  }

  async _handleCreateRule() {
    const nameInput = document.getElementById('settingsRuleCreateName');
    const descInput = document.getElementById('settingsRuleCreateDesc');
    const textarea = document.getElementById('settingsRuleCreateContent');
    const modeBtn = document.querySelector('#settingsRuleCreateMode .settings-toggle-btn.active');
    const scopeBtn = document.querySelector('#settingsRuleCreateScope .settings-toggle-btn.active');
    const statusEl = document.getElementById('settingsRuleCreateStatus');
    const saveBtn = document.getElementById('settingsRuleCreateSave');

    const name = nameInput?.value?.trim();
    if (!name) {
      if (statusEl) {
        statusEl.textContent = '⚠️ 规则名称不能为空';
        statusEl.className = 'settings-editor-status settings-editor-status-error';
        statusEl.style.display = 'block';
      }
      return;
    }

    if (saveBtn) {
      saveBtn.disabled = true;
      saveBtn.textContent = '创建中…';
    }

    try {
      const result = await apiPost('/api/rules/create', {
        name,
        description: descInput?.value?.trim() || '',
        mode: modeBtn?.dataset?.value || 'always',
        scope: scopeBtn?.dataset?.value || 'project',
        content: textarea?.value || '',
      });

      if (result.success) {
        if (statusEl) {
          statusEl.textContent = '✓ 规则已创建';
          statusEl.className = 'settings-editor-status settings-editor-status-success';
          statusEl.style.display = 'block';
        }
        if (saveBtn) saveBtn.textContent = '✓ 已创建';
        setTimeout(() => {
          const headerActions = document.querySelector('#settingsRulesCreate')?.closest('.settings-item-list-actions');
          if (headerActions) headerActions.style.display = '';
          this._loadRules();
        }, 600);
      } else {
        if (statusEl) {
          statusEl.textContent = '⚠️ ' + (result.message || '创建失败');
          statusEl.className = 'settings-editor-status settings-editor-status-error';
          statusEl.style.display = 'block';
        }
        if (saveBtn) {
          saveBtn.disabled = false;
          saveBtn.textContent = '创建';
        }
      }
    } catch (e) {
      console.warn('创建规则失败:', e);
      if (statusEl) {
        statusEl.textContent = '⚠️ 网络错误，请重试';
        statusEl.className = 'settings-editor-status settings-editor-status-error';
        statusEl.style.display = 'block';
      }
      if (saveBtn) {
        saveBtn.disabled = false;
        saveBtn.textContent = '创建';
      }
    }
  }

  // ==================== 技能管理页面 ====================

  async _renderSkillsPage() {
    const page = document.createElement('div');
    page.className = 'settings-page';

    page.innerHTML = `
      <h2 class="settings-page-title">技能管理</h2>
      <p class="settings-page-desc">管理项目级和用户级技能文件，按「项目技能」和「全局技能」分组</p>
      <hr class="settings-page-divider">

      <div class="settings-item-list-header">
        <h3>技能列表</h3>
        <div class="settings-item-list-actions">
          <button class="settings-btn settings-btn-icon" id="settingsSkillsRefresh" title="刷新">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </button>
          <button class="settings-btn settings-btn-primary" id="settingsSkillsCreate">+ 新建</button>
        </div>
      </div>

      <div class="settings-loading" id="settingsSkillsLoading" style="display:none;">加载中...</div>
      <div class="settings-items-error" id="settingsSkillsError" style="display:none;"></div>
      <div id="settingsSkillsList"></div>
    `;

    this._setContent(page);

    document.getElementById('settingsSkillsRefresh')?.addEventListener('click', () => this._loadSkills());
    document.getElementById('settingsSkillsCreate')?.addEventListener('click', () => this._showCreateSkillModal());

    await this._loadSkills();
  }

  async _loadSkills() {
    const loadingEl = document.getElementById('settingsSkillsLoading');
    const errorEl = document.getElementById('settingsSkillsError');
    const listEl = document.getElementById('settingsSkillsList');
    if (!listEl) return;

    if (loadingEl) loadingEl.style.display = 'block';
    if (errorEl) errorEl.style.display = 'none';

    try {
      const data = await apiGet('/api/skills/list');
      this._projectSkills = data.projectSkills || [];
      this._userSkills = data.userSkills || [];
      this._renderSkillsList(listEl);
    } catch (e) {
      console.warn('加载技能列表失败:', e);
      if (errorEl) {
        errorEl.textContent = '加载失败，请重试';
        errorEl.style.display = 'block';
      }
    } finally {
      if (loadingEl) loadingEl.style.display = 'none';
    }
  }

  _renderSkillsList(listEl) {
    if (this._projectSkills.length === 0 && this._userSkills.length === 0) {
      listEl.innerHTML = '<div class="settings-items-empty">暂无技能文件<br><span style="font-size:11px;opacity:0.6;">点击「+ 新建」创建第一个技能</span></div>';
      return;
    }

    listEl.innerHTML = '';

    if (this._projectSkills.length > 0) {
      listEl.appendChild(this._createSkillGroup('项目技能', this._projectSkills, 'project'));
    }
    if (this._userSkills.length > 0) {
      listEl.appendChild(this._createSkillGroup('全局技能', this._userSkills, 'user'));
    }
  }

  _createSkillGroup(label, skills, source) {
    const group = document.createElement('div');
    group.className = 'settings-item-group';

    const header = document.createElement('div');
    header.className = 'settings-item-group-header';

    const labelEl = document.createElement('span');
    labelEl.className = 'settings-item-group-label';
    labelEl.textContent = label;
    header.appendChild(labelEl);

    const count = document.createElement('span');
    count.className = 'settings-item-group-count';
    count.textContent = skills.length;
    header.appendChild(count);

    group.appendChild(header);

    const items = document.createElement('div');
    items.className = 'settings-items';

    for (const skill of skills) {
      const item = document.createElement('div');
      item.className = 'settings-item';
      item.addEventListener('click', () => this._showSkillDetail(skill, source));

      const iconInfo = getFileIconInfo(skill.fileName);
      const iconEl = document.createElement('img');
      iconEl.className = 'settings-item-icon';
      iconEl.src = '/icons/' + iconInfo.iconFile;
      iconEl.style.width = '18px';
      iconEl.style.height = '18px';
      iconEl.alt = '';
      item.appendChild(iconEl);

      const info = document.createElement('div');
      info.className = 'settings-item-info';

      const name = document.createElement('div');
      name.className = 'settings-item-name';
      name.textContent = skill.name || skill.fileName.replace(/\.md$/, '');
      info.appendChild(name);

      if (skill.description) {
        const meta = document.createElement('div');
        meta.className = 'settings-item-meta';
        meta.textContent = skill.description;
        info.appendChild(meta);
      }

      item.appendChild(info);

      const delBtn = document.createElement('button');
      delBtn.className = 'settings-item-del';
      delBtn.title = '删除';
      delBtn.innerHTML = `<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="3 6 5 6 21 6"/>
        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
      </svg>`;
      delBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this._deleteSkill(skill);
      });
      item.appendChild(delBtn);

      items.appendChild(item);
    }

    group.appendChild(items);
    return group;
  }

  _showSkillDetail(skill, source) {
    this._editingSkill = skill;
    this._renderSkillEditor(skill, source);
  }

  _renderSkillEditor(skill, source) {
    const listEl = document.getElementById('settingsSkillsList');
    if (!listEl) return;

    const headerActions = document.querySelector('#settingsSkillsCreate')?.closest('.settings-item-list-actions');
    if (headerActions) headerActions.style.display = 'none';

    const iconInfo = getFileIconInfo(skill.fileName);

    listEl.innerHTML = `
      <div class="settings-editor">
        <div class="settings-editor-header">
          <span class="settings-editor-title">编辑技能：${skill.name || skill.fileName.replace(/\.md$/, '')}</span>
          <div class="settings-editor-actions">
            <button class="settings-editor-btn" id="settingsSkillEditorBack">← 返回列表</button>
            <button class="settings-editor-btn settings-editor-btn-primary" id="settingsSkillEditorSave">保存</button>
          </div>
        </div>
        <div class="settings-editor-fields">
          <div class="settings-field">
            <label class="settings-field-label" for="settingsSkillEditorName">技能名称</label>
            <input class="settings-input" id="settingsSkillEditorName" type="text" value="${skill.name || skill.fileName.replace(/\.md$/, '')}">
          </div>
          <div class="settings-field">
            <label class="settings-field-label" for="settingsSkillEditorDesc">描述</label>
            <input class="settings-input" id="settingsSkillEditorDesc" type="text" value="${skill.description || ''}" placeholder="简短说明，前端展示用">
          </div>
          <div class="settings-field">
            <label class="settings-field-label">作用域</label>
            <div class="settings-toggle-group" id="settingsSkillEditorScope">
              <button class="settings-toggle-btn ${source === 'project' ? 'active' : ''}" data-value="project">项目技能</button>
              <button class="settings-toggle-btn ${source !== 'project' ? 'active' : ''}" data-value="user">全局技能</button>
            </div>
          </div>
          <div class="settings-field">
            <div style="display:flex;align-items:center;gap:6px;font-size:var(--font-size-xs);color:var(--text-primary);font-family:var(--font-mono);user-select:none;">
              <img src="/icons/${iconInfo.iconFile}" style="width:16px;height:16px;" alt="">
              <span>${skill.fileName}</span>
            </div>
          </div>
        </div>
        <textarea class="settings-editor-textarea" id="settingsSkillEditorContent" placeholder="加载中..." spellcheck="false"></textarea>
        <div class="settings-editor-status" id="settingsSkillEditorStatus" style="display:none;"></div>
      </div>
    `;

    // 加载内容
    this._loadSkillContent(skill);

    // 绑定事件
    document.getElementById('settingsSkillEditorBack')?.addEventListener('click', () => {
      this._editingSkill = null;
      if (headerActions) headerActions.style.display = '';
      this._loadSkills();
    });

    document.getElementById('settingsSkillEditorSave')?.addEventListener('click', () => {
      this._saveSkillEditor(skill, source);
    });

    document.querySelectorAll('#settingsSkillEditorScope .settings-toggle-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#settingsSkillEditorScope .settings-toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
  }

  async _loadSkillContent(skill) {
    const textarea = document.getElementById('settingsSkillEditorContent');
    if (!textarea) return;

    try {
      const resp = await fetch(`/api/skills/get?filePath=${encodeURIComponent(skill.filePath)}`);
      if (!resp.ok) throw new Error(await resp.text());
      const data = await resp.json();
      textarea.value = data.content || '';
      textarea.placeholder = '';
    } catch (e) {
      console.warn('加载技能内容失败:', e);
      textarea.value = '';
      textarea.placeholder = '加载失败';
    }
  }

  async _saveSkillEditor(skill, source) {
    const nameInput = document.getElementById('settingsSkillEditorName');
    const descInput = document.getElementById('settingsSkillEditorDesc');
    const textarea = document.getElementById('settingsSkillEditorContent');
    const scopeBtn = document.querySelector('#settingsSkillEditorScope .settings-toggle-btn.active');
    const statusEl = document.getElementById('settingsSkillEditorStatus');
    const saveBtn = document.getElementById('settingsSkillEditorSave');

    if (!nameInput || !textarea) return;

    const name = nameInput.value.trim();
    const description = descInput?.value.trim() || '';
    const scope = scopeBtn?.dataset.value || source;
    const content = textarea.value;

    if (!name) {
      if (statusEl) {
        statusEl.textContent = '⚠️ 技能名称不能为空';
        statusEl.className = 'settings-editor-status settings-editor-status-error';
        statusEl.style.display = 'block';
      }
      return;
    }

    if (saveBtn) {
      saveBtn.disabled = true;
      saveBtn.textContent = '保存中…';
    }

    try {
      const result = await apiPost('/api/skills/update', {
        filePath: skill.filePath,
        name,
        description,
        scope,
        content,
      });

      if (result.success) {
        if (statusEl) {
          statusEl.textContent = '✓ 技能已保存';
          statusEl.className = 'settings-editor-status settings-editor-status-success';
          statusEl.style.display = 'block';
        }
        if (saveBtn) saveBtn.textContent = '✓ 已保存';
        skill.filePath = result.filePath || skill.filePath;
        setTimeout(() => {
          const headerActions = document.querySelector('#settingsSkillsCreate')?.closest('.settings-item-list-actions');
          if (headerActions) headerActions.style.display = '';
          this._editingSkill = null;
          this._loadSkills();
        }, 800);
      } else {
        if (statusEl) {
          statusEl.textContent = '⚠️ ' + (result.message || '保存失败');
          statusEl.className = 'settings-editor-status settings-editor-status-error';
          statusEl.style.display = 'block';
        }
        if (saveBtn) {
          saveBtn.disabled = false;
          saveBtn.textContent = '保存';
        }
      }
    } catch (e) {
      console.warn('保存技能失败:', e);
      if (statusEl) {
        statusEl.textContent = '⚠️ 网络错误，请重试';
        statusEl.className = 'settings-editor-status settings-editor-status-error';
        statusEl.style.display = 'block';
      }
      if (saveBtn) {
        saveBtn.disabled = false;
        saveBtn.textContent = '保存';
      }
    }
  }

  async _deleteSkill(skill) {
    const name = skill.name || skill.fileName.replace(/\.md$/, '');
    const confirmed = await ConfirmDialog.confirmDelete(`确定删除技能「${name}」？`);
    if (!confirmed) return;

    try {
      const result = await apiPost('/api/skills/delete', { filePath: skill.filePath });
      if (result.success) {
        this._loadSkills();
      } else {
        showToast('删除失败: ' + (result.message || '未知错误'), { type: 'error', duration: 3000 });
      }
    } catch (e) {
      console.warn('删除技能失败:', e);
      showToast('删除失败，请重试', { type: 'error', duration: 3000 });
    }
  }

  _showCreateSkillModal() {
    const listEl = document.getElementById('settingsSkillsList');
    if (!listEl) return;

    const headerActions = document.querySelector('#settingsSkillsCreate')?.closest('.settings-item-list-actions');
    if (headerActions) headerActions.style.display = 'none';

    listEl.innerHTML = `
      <div class="settings-editor">
        <div class="settings-editor-header">
          <span class="settings-editor-title">新建技能</span>
          <div class="settings-editor-actions">
            <button class="settings-editor-btn" id="settingsSkillCreateBack">← 返回列表</button>
            <button class="settings-editor-btn settings-editor-btn-primary" id="settingsSkillCreateSave">创建</button>
          </div>
        </div>
        <div class="settings-editor-fields">
          <div class="settings-field">
            <label class="settings-field-label" for="settingsSkillCreateName">技能名称</label>
            <input class="settings-input" id="settingsSkillCreateName" type="text" placeholder="my-skill（字母、数字、连字符，不含 .md）">
          </div>
          <div class="settings-field">
            <label class="settings-field-label" for="settingsSkillCreateDesc">描述</label>
            <input class="settings-input" id="settingsSkillCreateDesc" type="text" placeholder="简短说明，前端展示用">
          </div>
          <div class="settings-field">
            <label class="settings-field-label">作用域</label>
            <div class="settings-toggle-group" id="settingsSkillCreateScope">
              <button class="settings-toggle-btn active" data-value="project">项目技能</button>
              <button class="settings-toggle-btn" data-value="user">全局技能</button>
            </div>
          </div>
        </div>
        <textarea class="settings-editor-textarea" id="settingsSkillCreateContent" placeholder="技能正文内容，Markdown 格式" spellcheck="false"></textarea>
        <div class="settings-editor-status" id="settingsSkillCreateStatus" style="display:none;"></div>
      </div>
    `;

    document.getElementById('settingsSkillCreateBack')?.addEventListener('click', () => {
      if (headerActions) headerActions.style.display = '';
      this._loadSkills();
    });

    document.getElementById('settingsSkillCreateSave')?.addEventListener('click', () => this._handleCreateSkill());

    // 绑定 toggle
    document.querySelectorAll('#settingsSkillCreateScope .settings-toggle-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#settingsSkillCreateScope .settings-toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
  }

  async _handleCreateSkill() {
    const nameInput = document.getElementById('settingsSkillCreateName');
    const descInput = document.getElementById('settingsSkillCreateDesc');
    const textarea = document.getElementById('settingsSkillCreateContent');
    const scopeBtn = document.querySelector('#settingsSkillCreateScope .settings-toggle-btn.active');
    const statusEl = document.getElementById('settingsSkillCreateStatus');
    const saveBtn = document.getElementById('settingsSkillCreateSave');

    const name = nameInput?.value?.trim();
    if (!name) {
      if (statusEl) {
        statusEl.textContent = '⚠️ 技能名称不能为空';
        statusEl.className = 'settings-editor-status settings-editor-status-error';
        statusEl.style.display = 'block';
      }
      return;
    }

    if (saveBtn) {
      saveBtn.disabled = true;
      saveBtn.textContent = '创建中…';
    }

    try {
      const result = await apiPost('/api/skills/create', {
        name,
        description: descInput?.value?.trim() || '',
        scope: scopeBtn?.dataset?.value || 'project',
        content: textarea?.value || '',
      });

      if (result.success) {
        if (statusEl) {
          statusEl.textContent = '✓ 技能已创建';
          statusEl.className = 'settings-editor-status settings-editor-status-success';
          statusEl.style.display = 'block';
        }
        if (saveBtn) saveBtn.textContent = '✓ 已创建';
        setTimeout(() => {
          const headerActions = document.querySelector('#settingsSkillsCreate')?.closest('.settings-item-list-actions');
          if (headerActions) headerActions.style.display = '';
          this._loadSkills();
        }, 600);
      } else {
        if (statusEl) {
          statusEl.textContent = '⚠️ ' + (result.message || '创建失败');
          statusEl.className = 'settings-editor-status settings-editor-status-error';
          statusEl.style.display = 'block';
        }
        if (saveBtn) {
          saveBtn.disabled = false;
          saveBtn.textContent = '创建';
        }
      }
    } catch (e) {
      console.warn('创建技能失败:', e);
      if (statusEl) {
        statusEl.textContent = '⚠️ 网络错误，请重试';
        statusEl.className = 'settings-editor-status settings-editor-status-error';
        statusEl.style.display = 'block';
      }
      if (saveBtn) {
        saveBtn.disabled = false;
        saveBtn.textContent = '创建';
      }
    }
  }

  // ==================== 通用设置页面 ====================

  _renderGeneralPage() {
    const page = document.createElement('div');
    page.className = 'settings-page';

    page.innerHTML = `
      <h2 class="settings-page-title">通用设置</h2>
      <p class="settings-page-desc">界面、行为等通用偏好设置</p>
      <hr class="settings-page-divider">
      <div class="settings-form">
        <div class="settings-field-horizontal">
          <label class="settings-field-label">主题模式</label>
          <div class="settings-field-body">
            <div class="settings-toggle-group" id="settingsThemeToggle">
              <button class="settings-toggle-btn" data-value="light">浅色</button>
              <button class="settings-toggle-btn" data-value="dark">深色</button>
              <button class="settings-toggle-btn" data-value="system">跟随系统</button>
            </div>
          </div>
        </div>
        <div class="settings-field desktop-only">
          <label class="settings-field-label" for="settingsDefaultWorkspace">
            默认工作区路径 <span class="settings-field-hint">(留空使用内置默认)</span>
          </label>
          <div class="settings-input-wrap">
            <input class="settings-input" id="settingsDefaultWorkspace" type="text" placeholder="留空则使用内置默认路径">
            <button class="settings-input-btn" id="settingsDefaultWorkspaceBrowse" title="选择文件夹">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    `;

    this._setContent(page);

    // 读取当前主题
    const currentTheme = document.documentElement.getAttribute('data-theme') || 'system';
    document.querySelectorAll('#settingsThemeToggle .settings-toggle-btn').forEach(btn => {
      if (btn.dataset.value === currentTheme) btn.classList.add('active');
      btn.addEventListener('click', () => {
        document.querySelectorAll('#settingsThemeToggle .settings-toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const theme = btn.dataset.value;
        if (theme === 'system') {
          document.documentElement.removeAttribute('data-theme');
        } else {
          document.documentElement.setAttribute('data-theme', theme);
        }
        localStorage.setItem('hippo-theme', theme);
      });
    });

    // ── 工作区路径 ──
    const browseBtn = document.getElementById('settingsDefaultWorkspaceBrowse');
    if (browseBtn && window.HippoDesktop?.selectDirectory) {
      browseBtn.addEventListener('click', async () => {
        try {
          const result = await window.HippoDesktop.selectDirectory();
          if (result?.path) {
            document.getElementById('settingsDefaultWorkspace').value = result.path;
          }
        } catch (e) {
          // ignore
        }
      });
    }

    // 加载默认工作区路径
    const workspaceInput = document.getElementById('settingsDefaultWorkspace');
    if (workspaceInput && window.HippoDesktop?.getDefaultWorkspace) {
      window.HippoDesktop.getDefaultWorkspace().then(result => {
        workspaceInput.value = result?.path || '';
      }).catch(() => {});
    }

    // 失焦时自动保存工作区路径
    if (workspaceInput && window.HippoDesktop?.setDefaultWorkspace) {
      workspaceInput.addEventListener('change', () => {
        window.HippoDesktop.setDefaultWorkspace(workspaceInput.value.trim()).catch(() => {});
      });
    }
  }

  // ==================== 工具方法 ====================

  _setContent(contentEl) {
    this._contentEl.innerHTML = '';
    this._contentEl.appendChild(contentEl);
  }

  /**
   * 刷新技能列表（供外部调用，安装技能后自动刷新）
   */
  reloadSkills() {
    const listEl = document.getElementById('settingsSkillsList');
    if (!listEl) return;
    if (this._activePage !== 'skills') return;
    this._editingSkill = null;
    this._loadSkills();
  }
}
