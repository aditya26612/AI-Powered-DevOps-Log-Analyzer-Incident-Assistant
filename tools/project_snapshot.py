import sys
from pathlib import Path

class ProjectAnalyzer:
    IGNORE_DIRS = {
        "__pycache__", ".git", ".idea", ".vscode",
        ".pytest_cache", ".mypy_cache", ".ruff_cache", ".tox",
        "build", "node_modules", "htmlcov", ".dist"
    }

    IGNORE_FILES = {
        ".DS_Store", ".coverage"
    }

    def __init__(self, root_path: Path):
        self.root = root_path
        self.output_file = self.root / "project_snapshot.md"

        self.python_files = 0
        self.test_files = 0
        self.directories = 0

    def is_ignored_part(self, part: str) -> bool:
        """Checks if a single directory or file name should be ignored."""
        if part in self.IGNORE_DIRS or part in self.IGNORE_FILES:
            return True
        # Catch any variation of virtual environments (.venv, .venv-1, venv, env)
        if "venv" in part.lower() or part.lower() == "env":
            return True
        return False

    def should_ignore(self, path: Path) -> bool:
        return self.is_ignored_part(path.name)

    def generate_tree(self, path: Path, prefix: str = "") -> list:
        lines = []
        try:
            items = sorted(
                [p for p in path.iterdir() if not self.should_ignore(p)],
                key=lambda p: (p.is_file(), p.name.lower()),
            )
        except PermissionError:
            return lines

        for i, item in enumerate(items):
            is_last = (i == len(items) - 1)
            connector = "└── " if is_last else "├── "
            lines.append(prefix + connector + item.name)

            if item.is_dir():
                self.directories += 1
                extension = "    " if is_last else "│   "
                lines.extend(self.generate_tree(item, prefix + extension))

        return lines

    def analyze_files(self):
        for f in self.root.rglob("*.py"):
            # Skip if any part of the path is in our ignore list
            if any(self.is_ignored_part(part) for part in f.parts):
                continue
                
            self.python_files += 1
            if f.name.startswith("test_"):
                self.test_files += 1

    def run(self):
        self.analyze_files()
        tree_lines = self.generate_tree(self.root)

        md = [
            "# Project Snapshot\n",
            f"Project : {self.root.name}\n",
            "## Statistics\n",
            f"- Python files : {self.python_files}",
            f"- Directories  : {self.directories}",
            f"- Test files   : {self.test_files}\n",
            "## Structure\n",
            self.root.name
        ]
        
        md.extend(tree_lines)

        self.output_file.write_text("\n".join(md), encoding="utf-8")
        print(f"Snapshot successfully written to:\n{self.output_file}")


if __name__ == "__main__":
    if sys.platform == "win32":
        sys.stdout.reconfigure(encoding="utf-8")

    project_root = Path.cwd()
    print(f"Analyzing project at: {project_root}...")
    analyzer = ProjectAnalyzer(project_root)
    analyzer.run()