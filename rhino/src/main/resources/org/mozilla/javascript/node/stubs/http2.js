({
  connect: function connect() {
    return {
      request: function() {
        return { on: function() { return this; }, end: function() {} };
      },
      close: function() {},
      on: function() { return this; },
      destroy: function() {}
    };
  },
  constants: {
    HTTP2_HEADER_PATH: ':path',
    HTTP2_HEADER_METHOD: ':method',
    HTTP2_HEADER_STATUS: ':status',
    HTTP2_HEADER_CONTENT_TYPE: 'content-type'
  },
  default: {
    connect: function connect() {
      return {
        request: function() {
          return { on: function() { return this; }, end: function() {} };
        },
        close: function() {},
        on: function() { return this; },
        destroy: function() {}
      };
    }
  }
})
