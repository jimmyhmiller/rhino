({
  Readable: class Readable {
    constructor() {}
    pipe() { return this; }
    on() { return this; }
    read() { return null; }
  },
  Writable: class Writable {
    constructor() {}
    write() {}
    end() {}
    on() { return this; }
  },
  Duplex: class Duplex {
    constructor() {}
    pipe() { return this; }
    on() { return this; }
    read() { return null; }
    write() {}
    end() {}
  },
  Transform: class Transform {
    constructor() {}
    pipe() { return this; }
    on() { return this; }
    _transform() {}
  },
  PassThrough: class PassThrough {
    constructor() {}
    pipe() { return this; }
    on() { return this; }
  },
  Stream: class Stream {
    constructor() {}
    pipe() { return this; }
    on() { return this; }
  }
})
