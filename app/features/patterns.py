"""
===============================================================================
DevInsight AI - Regex Pattern Registry
===============================================================================

This module contains all compiled regular expressions used throughout the
Feature Engineering pipeline.

All patterns are compiled once during application startup for maximum
performance.

No extractor should define its own regular expressions.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

import re
from typing import Final

# =============================================================================
# Exception Patterns
# =============================================================================

EXCEPTION_PATTERN: Final = re.compile(
    r"\b\w*(Exception|Error)\b|panic|fatal|fault|throwable",
    re.IGNORECASE,
)

STACKTRACE_PATTERN: Final = re.compile(
    r"\bat\s+[\w.$]+\([\w.$]+:\d+\)",
    re.IGNORECASE,
)

JAVA_EXCEPTION_PATTERN: Final = re.compile(
    r"\bjava\.[\w.]+Exception\b",
    re.IGNORECASE,
)

OUT_OF_MEMORY_PATTERN: Final = re.compile(
    r"\b(outofmemory|oom|java heap space|gc overhead limit exceeded)\b",
    re.IGNORECASE,
)

# =============================================================================
# Timestamp Patterns
# =============================================================================

ISO_TIMESTAMP_PATTERN: Final = re.compile(
    r"^\d{4}-\d{2}-\d{2}"
    r"[T ]"
    r"\d{2}:\d{2}:\d{2}"
    r"(?:\.\d+)?"
    r"(?:Z|[+-]\d{2}:\d{2})?"
)

SLASH_TIMESTAMP_PATTERN: Final = re.compile(
    r"^\d{4}/\d{2}/\d{2}"
    r"\s"
    r"\d{2}:\d{2}:\d{2}"
)

SYSLOG_TIMESTAMP_PATTERN: Final = re.compile(
    r"^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)"
    r"\s+\d{1,2}\s+\d{2}:\d{2}:\d{2}",
    re.IGNORECASE,
)

FULL_MONTH_TIMESTAMP_PATTERN: Final = re.compile(
    r"^(January|February|March|April|May|June|July|August|"
    r"September|October|November|December)"
    r"\s+\d{1,2}\s+\d{2}:\d{2}:\d{2}",
    re.IGNORECASE,
)

TIMESTAMP_PATTERNS: Final = (
    ISO_TIMESTAMP_PATTERN,
    SLASH_TIMESTAMP_PATTERN,
    SYSLOG_TIMESTAMP_PATTERN,
    FULL_MONTH_TIMESTAMP_PATTERN,
)

# =============================================================================
# Database Patterns
# =============================================================================

DATABASE_PATTERN: Final = re.compile(
    r"(sql\w*|jdbc|database|postgres|mysql|oracle|mongodb|redis|hikari|connection pool)",
    re.IGNORECASE,
)

SQL_EXCEPTION_PATTERN: Final = re.compile(
    r"\b(sqlsyntax|sqlstate|sqlexception|sqltimeoutexception)\b",
    re.IGNORECASE,
)


# =============================================================================
# Timeout Patterns
# =============================================================================

TIMEOUT_PATTERN: Final = re.compile(
    r"\b("
    r"timeout|"
    r"timed out|"
    r"sockettimeout|"
    r"read timeout|"
    r"connect timeout|"
    r"request timeout"
    r")\b",
    re.IGNORECASE,
)

# =============================================================================
# Network Patterns
# =============================================================================

NETWORK_PATTERN: Final = re.compile(
    r"\b(connection refused|reset by peer|network unreachable|broken pipe|host unreachable)\b",
    re.IGNORECASE,
)

IP_ADDRESS_PATTERN: Final = re.compile(
    r"\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}"
    r"(?:25[0-5]|2[0-4]\d|1?\d?\d)\b"
)

PORT_PATTERN: Final = re.compile(
    r":\d{2,5}\b"
)

URL_PATTERN: Final = re.compile(
    r"https?://[^\s\"'>]+",
    re.IGNORECASE,
)

# =============================================================================
# HTTP Patterns
# =============================================================================

HTTP_STATUS_PATTERN: Final = re.compile(
    r"\b(?:"
    r"1\d\d|"
    r"2\d\d|"
    r"3\d\d|"
    r"4\d\d|"
    r"5\d\d"
    r")\b"
)

HTTP_METHOD_PATTERN: Final = re.compile(
    r"\b(GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD)\b",
    re.IGNORECASE,
)

# =============================================================================
# Authentication / Security
# =============================================================================

JWT_PATTERN: Final = re.compile(
    r"\b(jwt|token|oauth|bearer|signature|authentication|authorization)\b",
    re.IGNORECASE,
)

ACCESS_DENIED_PATTERN: Final = re.compile(
    r"\b(accessdenied|forbidden|unauthorized|authentication failed)\b",
    re.IGNORECASE,
)

BRUTE_FORCE_PATTERN: Final = re.compile(
    r"\b(brute[- ]?force|failed login|account locked|rate limit)\b",
    re.IGNORECASE,
)

# =============================================================================
# Spring Boot Patterns
# =============================================================================

SPRING_PATTERN: Final = re.compile(
    r"\b(spring|springboot|dispatcherServlet|bean|tomcat|actuator|hibernate)\b",
    re.IGNORECASE,
)

# =============================================================================
# Docker Patterns
# =============================================================================

DOCKER_PATTERN: Final = re.compile(
    r"\b(docker|container|image|volume|overlay|cgroup|containerd)\b",
    re.IGNORECASE,
)

# =============================================================================
# Kubernetes Patterns
# =============================================================================

KUBERNETES_PATTERN: Final = re.compile(
    r"\b(kubernetes|k8s|pod|deployment|daemonset|replicaset|kubectl|namespace|kubelet)\b",
    re.IGNORECASE,
)

# =============================================================================
# JVM Patterns
# =============================================================================

JVM_PATTERN: Final = re.compile(
    r"\b(jvm|gc|garbage collector|heap|thread|jre|jdk)\b",
    re.IGNORECASE,
)

# =============================================================================
# UUID Pattern
# =============================================================================

UUID_PATTERN: Final = re.compile(
    r"\b[0-9a-f]{8}-"
    r"[0-9a-f]{4}-"
    r"[0-9a-f]{4}-"
    r"[0-9a-f]{4}-"
    r"[0-9a-f]{12}\b",
    re.IGNORECASE,
)

# =============================================================================
# Email Pattern
# =============================================================================

EMAIL_PATTERN: Final = re.compile(
    r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"
)

# =============================================================================
# File Path Pattern
# =============================================================================

FILE_PATH_PATTERN: Final = re.compile(
    r"(\/[\w./-]+)|([A-Za-z]:\\[\w\\.-]+)"
)

# =============================================================================
# Numeric Pattern
# =============================================================================

NUMBER_PATTERN: Final = re.compile(
    r"\b\d+(\.\d+)?\b"
)

# =============================================================================
# Whitespace Pattern
# =============================================================================

MULTIPLE_SPACE_PATTERN: Final = re.compile(
    r"\s+"
)

# =============================================================================
# Pattern Registry
# =============================================================================

PATTERN_REGISTRY: Final = {
    "exception": EXCEPTION_PATTERN,
    "stacktrace": STACKTRACE_PATTERN,
    "java_exception": JAVA_EXCEPTION_PATTERN,
    "out_of_memory": OUT_OF_MEMORY_PATTERN,
    "database": DATABASE_PATTERN,
    "sql_exception": SQL_EXCEPTION_PATTERN,
    "timeout": TIMEOUT_PATTERN,
    "network": NETWORK_PATTERN,
    "ip": IP_ADDRESS_PATTERN,
    "port": PORT_PATTERN,
    "url": URL_PATTERN,
    "http_status": HTTP_STATUS_PATTERN,
    "http_method": HTTP_METHOD_PATTERN,
    "jwt": JWT_PATTERN,
    "access_denied": ACCESS_DENIED_PATTERN,
    "brute_force": BRUTE_FORCE_PATTERN,
    "spring": SPRING_PATTERN,
    "docker": DOCKER_PATTERN,
    "kubernetes": KUBERNETES_PATTERN,
    "jvm": JVM_PATTERN,
    "uuid": UUID_PATTERN,
    "email": EMAIL_PATTERN,
    "file_path": FILE_PATH_PATTERN,
    "number": NUMBER_PATTERN,
    "multiple_spaces": MULTIPLE_SPACE_PATTERN,
}


# =============================================================================
# Pattern Categories
# =============================================================================

PATTERN_CATEGORIES: Final = {
    "exception": "error",
    "stacktrace": "error",
    "java_exception": "error",
    "out_of_memory": "runtime",
    "database": "database",
    "sql_exception": "database",
    "timeout": "network",
    "network": "network",
    "ip": "network",
    "url": "network",
    "http_status": "http",
    "http_method": "http",
    "jwt": "security",
    "access_denied": "security",
    "brute_force": "security",
    "spring": "framework",
    "docker": "container",
    "kubernetes": "container",
    "jvm": "runtime",
    "uuid": "identifier",
    "email": "identifier",
    "file_path": "filesystem",
    "number": "numeric",
    "multiple_spaces": "formatting",
}

# =============================================================================
# Regex Feature Mapping
# =============================================================================

REGEX_FEATURE_MAPPING: Final = {
    "contains_exception": EXCEPTION_PATTERN,
    "contains_stacktrace": STACKTRACE_PATTERN,
    "contains_timeout": TIMEOUT_PATTERN,
    "contains_database_error": DATABASE_PATTERN,
    "contains_network_error": NETWORK_PATTERN,
    "contains_memory_error": OUT_OF_MEMORY_PATTERN,
    "contains_http_status": HTTP_STATUS_PATTERN,
    "contains_ip_address": IP_ADDRESS_PATTERN,
    "contains_uuid": UUID_PATTERN,
    "contains_port_number": PORT_PATTERN,
    "contains_kubernetes_keyword": KUBERNETES_PATTERN,
    "contains_docker_keyword": DOCKER_PATTERN,
    "contains_spring_keyword": SPRING_PATTERN,
    "contains_jvm_keyword": JVM_PATTERN,
    "contains_authentication_keyword": JWT_PATTERN,
}