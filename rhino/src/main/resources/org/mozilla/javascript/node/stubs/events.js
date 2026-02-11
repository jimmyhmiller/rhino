({
  EventEmitter: class EventEmitter {
    constructor() { this._events = {}; }
    on() { return this; }
    emit() { return false; }
    removeListener() { return this; }
    once() { return this; }
  },
  default: class EventEmitter {
    constructor() { this._events = {}; }
    on() { return this; }
    emit() { return false; }
    removeListener() { return this; }
    once() { return this; }
  }
})
