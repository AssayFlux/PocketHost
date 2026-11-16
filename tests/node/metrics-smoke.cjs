'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');

const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'pockethost-node-metrics-'));
const metricsFile = path.join(directory, 'metrics.json');
process.env.POCKETHOST_METRICS_FILE = metricsFile;
require('../../runtime-worker/src/main/assets/pockethost-node-metrics.cjs');

const server = http.createServer((_request, response) => response.end('ok'));
server.listen(0, '0.0.0.0', () => {
  assert.equal(server.address().address, '127.0.0.1', 'Node server must be forced to loopback');
  const port = server.address().port;
  http.get({ hostname: '127.0.0.1', port, path: '/' }, response => {
    response.resume();
    response.on('end', () => {
      setTimeout(() => {
        try {
          const metrics = JSON.parse(fs.readFileSync(metricsFile, 'utf8'));
          assert.equal(metrics.requests, 1);
          assert.equal(metrics.bytesSent, 2);
          assert.equal(metrics.active, 0);
          assert.ok(metrics.rssBytes > 0);
          server.close(() => {
            fs.rmSync(directory, { recursive: true, force: true });
            process.stdout.write('Node metrics preload smoke passed.\n');
          });
        } catch (error) {
          server.close(() => {
            fs.rmSync(directory, { recursive: true, force: true });
            process.stderr.write(String(error) + '\n');
            process.exitCode = 1;
          });
        }
      }, 2_200);
    });
  }).on('error', error => {
    server.close();
    fs.rmSync(directory, { recursive: true, force: true });
    throw error;
  });
});
