-- ==============================================================================
-- DevInsight - Database Initialization Script
-- ==============================================================================

SELECT 'CREATE DATABASE devinsight'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'devinsight')\gexec

SELECT 'CREATE DATABASE log_layer_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'log_layer_db')\gexec

SELECT 'CREATE DATABASE llm_service_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'llm_service_db')\gexec

