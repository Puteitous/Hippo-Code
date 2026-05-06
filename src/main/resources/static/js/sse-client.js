export class SSEClient {
  constructor(url, options = {}) {
    this.url = url;
    this.maxReconnectAttempts = options.maxReconnectAttempts || 10;
    this.baseDelay = options.baseDelay || 1000;
    this.maxDelay = options.maxDelay || 30000;
    this.es = null;
    this.reconnectAttempts = 0;
    this.reconnectTimer = null;
    this.eventHandlers = new Map();
    this.onOpenHandler = options.onOpen || null;
    this.onErrorHandler = options.onError || null;
    this.isManualClose = false;
  }

  connect() {
    this.isManualClose = false;
    
    if (this.es) {
      this.es.close();
      this.es = null;
    }

    this.es = new EventSource(this.url);

    this.es.onopen = () => {
      this.reconnectAttempts = 0;
      console.log('SSE 连接成功');
      if (this.onOpenHandler) this.onOpenHandler();
    };

    this.es.onerror = (err) => {
      if (this.isManualClose) return;
      
      console.error('SSE 连接断开:', err);
      
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
      }

      this.reconnectAttempts++;

      if (this.reconnectAttempts <= this.maxReconnectAttempts) {
        const delay = Math.min(
          this.baseDelay * Math.pow(2, this.reconnectAttempts - 1),
          this.maxDelay
        );
        console.log(`将在 ${delay}ms 后重连 (第 ${this.reconnectAttempts}/${this.maxReconnectAttempts} 次)`);
        
        if (this.onErrorHandler) this.onErrorHandler(this.reconnectAttempts);
        
        this.reconnectTimer = setTimeout(() => {
          this.connect();
        }, delay);
      } else {
        console.error('SSE 连接失败次数过多，停止重连');
        if (this.onErrorHandler) this.onErrorHandler(this.reconnectAttempts);
      }
    };

    for (const [eventName, handler] of this.eventHandlers) {
      this.es.addEventListener(eventName, handler);
    }
  }

  on(eventName, handler) {
    this.eventHandlers.set(eventName, handler);
    if (this.es) {
      this.es.addEventListener(eventName, handler);
    }
  }

  onOpen(handler) {
    this.onOpenHandler = handler;
  }

  onError(handler) {
    this.onErrorHandler = handler;
  }

  close() {
    this.isManualClose = true;
    
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    
    if (this.es) {
      this.es.close();
      this.es = null;
    }
  }

  isConnected() {
    return this.es && this.es.readyState === EventSource.OPEN;
  }

  getReconnectAttempts() {
    return this.reconnectAttempts;
  }

  resetReconnectAttempts() {
    this.reconnectAttempts = 0;
  }
}
