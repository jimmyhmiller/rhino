({
  inherits: function inherits(ctor, superCtor) {
    if (superCtor) {
      ctor.prototype = Object.create(superCtor.prototype);
      ctor.prototype.constructor = ctor;
    }
  },
  deprecate: function deprecate(fn, msg) { return fn; },
  promisify: function promisify(fn) { return fn; },
  inspect: function inspect(obj) { return String(obj); },
  TextDecoder: class TextDecoder { decode() { return ''; } },
  TextEncoder: class TextEncoder { encode() { return new Uint8Array(); } },
  types: {
    isUint8Array: function(v) { return v instanceof Uint8Array; }
  }
})
