"""
Example FastAPI application instrumented with DevInsight
"""
import logging
import os
import sys
from fastapi import FastAPI, HTTPException
from devinsight_logger import DevInsightHandler

# Setup Logger
logger = logging.getLogger("checkout-service")
logger.setLevel(logging.INFO)

# Console Output
console_handler = logging.StreamHandler(sys.stdout)
formatter = logging.Formatter("%(asctime)s %(levelname)s [%(threadName)s] %(name)s : %(message)s")
console_handler.setFormatter(formatter)
logger.addHandler(console_handler)

# DevInsight Asynchronous Log Streaming Handler
devinsight_handler = DevInsightHandler(
    gateway_url="http://localhost:8080",
    api_key=os.environ["DEVINSIGHT_API_KEY"],
    application_name="checkout-service",
    environment="DEVELOPMENT"
)
devinsight_handler.setFormatter(formatter)
logger.addHandler(devinsight_handler)

app = FastAPI(title="Checkout Payment Service")

@app.get("/")
def read_root():
    logger.info("Health check ping received.")
    return {"status": "ok", "service": "checkout-service"}

@app.post("/simulate-db-failure")
def simulate_db_failure():
    logger.info("Initiating high-concurrency payment transaction...")
    try:
        # Simulate connection pool timeout
        raise TimeoutError("Database connection pool 'asyncpg-pool' exhausted after 5000ms. Active: 100/100 connections.")
    except Exception as ex:
        logger.exception("Critical error executing database transaction!")
        raise HTTPException(status_code=500, detail="Transaction failed due to database connection exhaustion")

if __name__ == "__main__":
    import uvicorn
    print("Starting example FastAPI app on http://localhost:8085...")
    uvicorn.run(app, host="0.0.0.0", port=8085)

