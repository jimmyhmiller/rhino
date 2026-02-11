({
  join: function join() {
    return Array.prototype.slice.call(arguments).join('/');
  },
  resolve: function resolve() {
    return Array.prototype.slice.call(arguments).join('/');
  },
  dirname: function dirname(p) {
    var i = p.lastIndexOf('/');
    return i >= 0 ? p.substring(0, i) : '.';
  },
  basename: function basename(p) {
    var i = p.lastIndexOf('/');
    return i >= 0 ? p.substring(i + 1) : p;
  },
  extname: function extname(p) {
    var i = p.lastIndexOf('.');
    return i >= 0 ? p.substring(i) : '';
  },
  sep: '/',
  posix: {},
  win32: {}
})
