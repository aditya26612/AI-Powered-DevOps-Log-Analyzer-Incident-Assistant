/**
 * DevInsight Winston Log Transport for Node.js
 * ============================================
 * Asynchronously buffers and streams application logs and unhandled error stacks
 * to the DevInsight API Gateway.
 */

const Transport = require('winston-transport');
const http = require('http');
const https = require('https');
const { URL } = require('url');

class DevInsightTransport extends Transport {
  constructor(opts = {}) {
    super(opts);
    this.gatewayUrl = opts.gatewayUrl || 'http://localhost:8080';
    this.apiKey = opts.apiKey || 'devinsight_internal_ml_key_secret_12345';
    this.applicationName = opts.applicationName || 'nodejs-service';
    this.environment = opts.environment || 'PRODUCTION';
    this.parsedUrl = new URL(this.gatewayUrl.replace(/\/$/, '') + '/api/v1/logs');
    this.client = this.parsedUrl.protocol === 'https:' ? https : http;
  }

  log(info, callback) {
    setImmediate(() => {
      this.emit('logged', info);
    });

    const rawLog = info.stack || `${new Date().toISOString()} [${info.level.toUpperCase()}] ${info.message}`;

    const payload = JSON.stringify({
      rawLog: rawLog,
      source: 'NODEJS',
      environment: this.environment,
      applicationName: this.applicationName
    });

    const reqOptions = {
      hostname: this.parsedUrl.hostname,
      port: this.parsedUrl.port || (this.parsedUrl.protocol === 'https:' ? 443 : 80),
      path: this.parsedUrl.pathname,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload),
        'X-API-Key': this.apiKey
      },
      timeout: 3000
    };

    const req = this.client.request(reqOptions, (res) => {
      res.resume(); // Discard response data
      if (callback) callback();
    });

    req.on('error', () => {
      // Silently discard transport failures to safeguard application performance
      if (callback) callback();
    });

    req.write(payload);
    req.end();
  }
}

module.exports = DevInsightTransport;

