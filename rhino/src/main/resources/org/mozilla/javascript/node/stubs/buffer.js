({
  Buffer: class Buffer {
    constructor() {}
    static from(v) { return new Buffer(); }
    static alloc(n) { return new Buffer(); }
    static isBuffer(v) { return false; }
    toString() { return ''; }
  },
  default: {
    Buffer: class Buffer {
      constructor() {}
      static from(v) { return new Buffer(); }
      static alloc(n) { return new Buffer(); }
      static isBuffer(v) { return false; }
      toString() { return ''; }
    }
  }
})
