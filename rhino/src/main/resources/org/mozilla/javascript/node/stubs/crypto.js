({
  randomBytes: function randomBytes(n) {
    var arr = new Uint8Array(n);
    for (var i = 0; i < n; i++) arr[i] = Math.floor(Math.random() * 256);
    return arr;
  },
  createHash: function createHash() {
    return { update: function() { return this; }, digest: function() { return ''; } };
  },
  createHmac: function createHmac() {
    return { update: function() { return this; }, digest: function() { return ''; } };
  }
})
