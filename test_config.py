# from app.core.config import settings
# from app.core.logging import configure_logging, get_logger

# configure_logging(settings.LOG_LEVEL)

# logger = get_logger(__name__)

# logger.info("Application started")

# logger.warning("Memory usage high")

# logger.error("Model not found")

from pathlib import Path

IGNORE_DIRS = {
    ".git",
    ".idea",
    ".vscode",
    ".venv",
    "__pycache__",
    ".pytest_cache",
    ".mypy_cache",
    "node_modules",
    "build",
    "dist",
}

IGNORE_FILES = {
    ".DS_Store",
}


def print_tree(path: Path, prefix=""):
    items = sorted(
        [
            p for p in path.iterdir()
            if p.name not in IGNORE_DIRS
            and p.name not in IGNORE_FILES
        ],
        key=lambda x: (x.is_file(), x.name.lower())
    )

    for index, item in enumerate(items):
        connector = "└── " if index == len(items) - 1 else "├── "
        print(prefix + connector + item.name)

        if item.is_dir():
            extension = "    " if index == len(items) - 1 else "│   "
            print_tree(item, prefix + extension)


if __name__ == "__main__":
    print(Path(".").resolve().name)
    print_tree(Path("."))