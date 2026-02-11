({
  isatty: function isatty() { return false; },
  ReadStream: class ReadStream {
    constructor() { this.isTTY = false; }
  },
  WriteStream: class WriteStream {
    constructor() { this.isTTY = false; }
  }
})
