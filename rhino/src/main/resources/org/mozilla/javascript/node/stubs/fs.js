({
  readFileSync: function readFileSync() {
    throw new Error('fs.readFileSync is not supported in Rhino');
  },
  writeFileSync: function writeFileSync() {
    throw new Error('fs.writeFileSync is not supported in Rhino');
  },
  existsSync: function existsSync() { return false; },
  promises: {}
})
