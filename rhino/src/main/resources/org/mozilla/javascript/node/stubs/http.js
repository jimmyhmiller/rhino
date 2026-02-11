({
  Agent: class Agent {
    constructor(opts) { this.options = opts || {}; }
    destroy() {}
  },
  IncomingMessage: class IncomingMessage {
    constructor() {
      this.headers = {};
      this.method = 'GET';
      this.url = '';
      this.statusCode = null;
    }
  },
  OutgoingMessage: class OutgoingMessage {
    constructor() {
      this._headers = {};
      this._headerSent = false;
      this._hasConnectPatch = false;
      this.charset = '';
    }
    setHeader(name, value) { this._headers[name.toLowerCase()] = value; }
    getHeader(name) { return this._headers[name.toLowerCase()]; }
    removeHeader(name) { delete this._headers[name.toLowerCase()]; }
    _renderHeaders() { return this._headers; }
    end() {}
    on() { return this; }
    emit() {}
  },
  ServerResponse: class ServerResponse {
    constructor() {
      this._headers = {};
      this._headerSent = false;
      this.statusCode = 200;
      this.charset = '';
    }
    setHeader(name, value) { this._headers[name.toLowerCase()] = value; }
    getHeader(name) { return this._headers[name.toLowerCase()]; }
    removeHeader(name) { delete this._headers[name.toLowerCase()]; }
    _renderHeaders() { return this._headers; }
    writeHead() { return this; }
    write() { return true; }
    end() {}
    on() { return this; }
    emit() {}
  },
  ClientRequest: class ClientRequest {
    constructor() {}
    end() {}
    on() { return this; }
    write() { return true; }
    abort() {}
  },
  request: function request() {
    return { on: function() { return this; }, end: function() {} };
  },
  get: function get() {
    return { on: function() { return this; }, end: function() {} };
  },
  Server: class Server {
    constructor() {}
    listen() { return this; }
    close() {}
    on() { return this; }
  },
  createServer: function createServer() {
    return new this.Server();
  },
  globalAgent: new (class Agent { constructor() {} destroy() {} })()
})
