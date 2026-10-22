'use strict';
// DSH's web listener uses a private Unix socket. The native authenticated proxy
// owns the only TCP listener. Child Node commands do not inherit this override.
if (process.env.NIOBIUM_SOCKET && process.argv.some(v => v.endsWith('/dsh/lib/bin.js')) && process.argv.includes('web')) {
  const net = require('node:net');
  const fs = require('node:fs');
  const original = net.Server.prototype.listen;
  const port = Number(process.env.DSH_PORT || 3080);
  net.Server.prototype.listen = function (...args) {
    const value = typeof args[0] === 'object' && args[0] !== null ? args[0].port : args[0];
    if (Number(value) === port) {
      const path = process.env.NIOBIUM_SOCKET;
      try { fs.unlinkSync(path); } catch (error) { if (error.code !== 'ENOENT') throw error; }
      const address = this.address;
      this.address = function () {
        const bound = address.call(this);
        return typeof bound === 'string' ? {address: '127.0.0.1', family: 'IPv4', port} : bound;
      };
      const callback = args.findLast(item => typeof item === 'function');
      return callback ? original.call(this, {path}, callback) : original.call(this, {path});
    }
    return original.apply(this, args);
  };
}
