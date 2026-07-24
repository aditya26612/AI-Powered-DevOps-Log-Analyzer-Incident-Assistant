"""
===============================================================================
Artifact Validator
===============================================================================
"""

from pathlib import Path


class ArtifactValidator:

    @staticmethod
    def exists(path: Path) -> bool:

        return path.exists()

    @staticmethod
    def validate(path: Path):

        if not path.exists():
            raise FileNotFoundError(path)