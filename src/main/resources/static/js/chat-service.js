import { safeParseJSON } from './utils.js';

export class ChatService {
  constructor(baseUrl = '', options = {}) {
    this.baseUrl = baseUrl;
    this.maxRetries = options.maxRetries || 2;
    this.retryDelay = options.retryDelay || 1000;
  }

  async sendMessage(session, message, onChunk, signal, systemPrompt, editMessageId) {
    let lastError = null;
    let receivedContent = false;

    for (let attempt = 0; attempt <= this.maxRetries; attempt++) {
      if (attempt > 0) {
        console.log(`空响应重试：第 ${attempt}/${this.maxRetries} 次`);
        if (onChunk) {
          onChunk({
            type: 'retry',
            attempt: attempt,
            maxRetries: this.maxRetries,
            message: `正在重试 (${attempt}/${this.maxRetries})...`
          });
        }
        
        await new Promise(resolve => setTimeout(resolve, this.retryDelay * attempt));
      }

      try {
        const result = await this.executeRequest(session, message, onChunk, signal, systemPrompt, editMessageId);
        if (result.hasContent) {
          return;
        }
        
        lastError = new Error('LLM 未返回有效内容');
      } catch (error) {
        if (error.name === 'AbortError') {
          throw error;
        }
        lastError = error;
      }
    }

    throw lastError || new Error('请求失败');
  }

  async executeRequest(session, message, onChunk, signal, systemPrompt, editMessageId) {
    const timeout = 5 * 60 * 1000;
    const timeoutId = setTimeout(() => {
      throw new Error('请求超时');
    }, timeout);

    let hasContent = false;

    try {
      const response = await fetch(`${this.baseUrl}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: signal || null,
        body: JSON.stringify({
          session: session,
          message: message,
          ...(systemPrompt ? { systemPrompt: systemPrompt } : {}),
          ...(editMessageId ? { editMessageId: editMessageId } : {})
        })
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        const lines = chunk.split('\n');

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.substring(6);
            if (data === '[DONE]') continue;

            const parsed = safeParseJSON(data);
            if (parsed && onChunk) {
              if (parsed.content) {
                hasContent = true;
              }
              onChunk(parsed);
            }
          }
        }
      }
    } finally {
      clearTimeout(timeoutId);
    }

    return { hasContent };
  }

  async getSessions() {
    const response = await fetch(`${this.baseUrl}/api/sessions`);
    if (!response.ok) {
      throw new Error(`获取会话列表失败: ${response.status}`);
    }
    return response.json();
  }

  async getSessionMessages(sessionId) {
    const response = await fetch(`${this.baseUrl}/api/sessions/${sessionId}/messages`);
    if (response.status === 404) return [];
    if (!response.ok) {
      throw new Error(`获取消息失败: ${response.status}`);
    }
    return response.json();
  }

  async deleteSession(sessionId) {
    const response = await fetch(`${this.baseUrl}/api/sessions/${sessionId}`, {
      method: 'DELETE'
    });
    if (!response.ok) {
      throw new Error(`删除会话失败: ${response.status}`);
    }
  }

  async renameSession(sessionId, name) {
    const response = await fetch(`${this.baseUrl}/api/sessions/${sessionId}/rename`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name })
    });
    if (!response.ok) {
      throw new Error(`重命名会话失败: ${response.status}`);
    }
  }

  stopGeneration(abortController) {
    if (abortController) {
      abortController.abort();
    }
  }
}
