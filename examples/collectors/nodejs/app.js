"use strict";

const { createLogger, format, transports } = require("winston");
const DevInsightTransport = require("devinsight-log-collector/winston");

async function main() {
  const count = Number(process.env.DEMO_EVENTS || 6);
  const delay = Number(process.env.DEMO_DELAY_MS || 0);
  if (!Number.isInteger(count) || count < 1 || count > 5000 || !Number.isFinite(delay) || delay < 0 || delay > 1000) {
    throw new Error("Choose 1 to 5000 events and a delay between 0 and 1000 ms");
  }
  const failures = process.env.DEMO_ERRORS !== "false";
  const collector = new DevInsightTransport();
  const logger = createLogger({
    level: "info", format: format.combine(format.errors({ stack: true }), format.simple()),
    transports: [new transports.Console(), collector],
  });
  try {
    for (let index = 0; index < count; index++) {
      if (failures && index === count - 1) {
        logger.error(new Error("Synthetic database connection timed out"));
      } else if (failures && index === count - 2) {
        logger.warn("Synthetic dependency slowdown");
      } else {
        logger.info("Payment accepted in independent Node.js example");
      }
      if (delay) await new Promise(resolve => setTimeout(resolve, delay));
    }
  } finally {
    await collector.close();
    logger.close();
  }
  console.log("DEVINSIGHT_STATS " + JSON.stringify(collector.stats()));
}

main().catch(() => {
  console.error("DevInsight sample failed. Check protected configuration and test limits.");
  process.exitCode = 1;
});
