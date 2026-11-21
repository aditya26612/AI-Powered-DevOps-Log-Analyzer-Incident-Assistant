# DevInsight Node.js collector

The local devinsight-log-collector package targets Node.js 20 or newer. Its core uses built-in Node modules. Winston users can import the package's winston export and install winston-transport as shown in the runnable example.

Provide DEVINSIGHT_API_KEY, DEVINSIGHT_APPLICATION_NAME and DEVINSIGHT_ENVIRONMENT through protected process configuration. The optional DEVINSIGHT_ENDPOINT is the full Gateway batch URL.

```javascript
const { DevInsightCollector } = require("devinsight-log-collector");
async function main() {
  const collector = new DevInsightCollector();
  collector.capture("INFO", "Payment accepted");
  await collector.close();
  console.log(collector.stats());
}

main().catch(() => { process.exitCode = 1; });
```

For Winston use the transport alongside your normal console and file transports, and await its close method during shutdown.

See the [Winston collector example](../../examples/collectors/nodejs).
