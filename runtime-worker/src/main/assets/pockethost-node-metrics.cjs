'use strict';

const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const net = require('node:net');

// All Node TCP listeners stay on loopback. Public/LAN ingress must pass through
// the Rust front door, which enforces the user's LAN exposure setting.
const originalListen = net.Server.prototype.listen;
net.Server.prototype.listen = function (...args) {
  if (args[0] && typeof args[0] === 'object' && !Array.isArray(args[0])) {
    args[0] = { ...args[0], host: '127.0.0.1' };
  } else if (typeof args[0] === 'number') {
    if (typeof args[1] === 'function' || args.length < 2) args.splice(1, 0, '127.0.0.1');
    else args[1] = '127.0.0.1';
  }
  return originalListen.apply(this, args);
};

const target = process.env.POCKETHOST_METRICS_FILE;
const counters = { requests: 0, bytesSent: 0, errors: 0, active: 0 };
const originalEmit = http.Server.prototype.emit;
const originalWrite = http.ServerResponse.prototype.write;
const originalEnd = http.ServerResponse.prototype.end;

http.Server.prototype.emit = function (event, ...args) {
  if (event === 'request') {
    const response = args[1];
    counters.requests += 1;
    counters.active += 1;
    response.once('finish', () => { counters.active = Math.max(0, counters.active - 1); });
    response.once('close', () => {
      if (!response.writableFinished) {
        counters.errors += 1;
        counters.active = Math.max(0, counters.active - 1);
      }
    });
  }
  return originalEmit.call(this, event, ...args);
};

function countBytes(chunk, encoding) {
  if (chunk === undefined || chunk === null) return 0;
  if (Buffer.isBuffer(chunk)) return chunk.length;
  if (ArrayBuffer.isView(chunk)) return chunk.byteLength;
  return Buffer.byteLength(String(chunk), typeof encoding === 'string' ? encoding : 'utf8');
}

http.ServerResponse.prototype.write = function (chunk, encoding, callback) {
  counters.bytesSent += countBytes(chunk, encoding);
  return originalWrite.call(this, chunk, encoding, callback);
};

http.ServerResponse.prototype.end = function (chunk, encoding, callback) {
  if (chunk !== undefined && chunk !== null) counters.bytesSent += countBytes(chunk, encoding);
  return originalEnd.call(this, chunk, encoding, callback);
};

function writeMetrics() {
  if (!target) return;
  try {
    const memory = process.memoryUsage();
    const data = JSON.stringify({
      requests: counters.requests,
      bytesSent: counters.bytesSent,
      errors: counters.errors,
      active: counters.active,
      rssBytes: memory.rss,
      heapUsedBytes: memory.heapUsed,
      heapTotalBytes: memory.heapTotal,
      uptimeSeconds: process.uptime(),
      sampledAt: new Date().toISOString()
    });
    const temporary = target + '.tmp';
    fs.writeFileSync(temporary, data, { mode: 0o600 });
    fs.renameSync(temporary, target);
  } catch (_) {
    // Metrics must never crash a user application.
  }
}

const timer = setInterval(writeMetrics, 2000);
timer.unref();
writeMetrics();
