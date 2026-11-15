# DevInsight Node.js Integration SDK

Drop-in Winston logging transport for Node.js applications (Express, NestJS, Fastify, Koa).

## Quick Start (30 Seconds)

### 1. Copy `devinsight-transport.js` into your project

### 2. Add it to your Winston logger configuration:

```javascript
const winston = require('winston');
const DevInsightTransport = require('./devinsight-transport');

const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.errors({ stack: true }),
    winston.format.json()
  ),
  transports: [
    new winston.transports.Console(),
    new DevInsightTransport({
      gatewayUrl: 'http://localhost:8080',
      apiKey: 'your_service_api_key_here',
      applicationName: 'order-api',
      environment: 'PRODUCTION'
    })
  ]
});

// Logs and unhandled errors stream directly to DevInsight
logger.error(new Error('FATAL: Database connection timeout!'));
```

