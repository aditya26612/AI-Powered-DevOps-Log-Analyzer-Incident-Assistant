import logging
import sys


LOG_FORMAT = (
    "%(asctime)s | "
    "%(levelname)-8s | "
    "%(name)s | "
    "%(message)s"
)


def configure_logging(log_level: str = "INFO") -> None:
    """
    Configure application-wide logging.
    """

    logging.basicConfig(
        level=getattr(logging, log_level.upper(), logging.INFO),
        format=LOG_FORMAT,
        handlers=[
            logging.StreamHandler(sys.stdout)
        ],
        force=True,
    )


def get_logger(name: str) -> logging.Logger:
    """
    Return a logger instance.
    """
    return logging.getLogger(name)