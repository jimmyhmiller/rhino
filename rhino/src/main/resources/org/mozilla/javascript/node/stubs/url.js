({
  URL: typeof URL !== 'undefined' ? URL : class URL { constructor(u) { this.href = u; } },
  URLSearchParams: typeof URLSearchParams !== 'undefined' ? URLSearchParams : class URLSearchParams { constructor() {} },
  parse: function parse(u) {
    return { href: u, hostname: '', pathname: '', protocol: '' };
  },
  format: function format(u) {
    return typeof u === 'string' ? u : (u.href || '');
  }
})
