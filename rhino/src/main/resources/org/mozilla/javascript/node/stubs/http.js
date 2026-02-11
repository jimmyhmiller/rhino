({
  Agent: class Agent {
    constructor(opts) { this.options = opts || {}; }
    destroy() {}
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
