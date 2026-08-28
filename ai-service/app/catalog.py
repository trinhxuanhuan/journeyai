from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from unidecode import unidecode


CATALOG_PATH = Path(__file__).resolve().parent / "data" / "vietnam_destinations.json"


def normalize_text(value: str) -> str:
    return " ".join(unidecode(value).lower().replace("–", " ").replace("-", " ").split())


@lru_cache(maxsize=1)
def load_catalog() -> dict[str, Any]:
    with CATALOG_PATH.open(encoding="utf-8") as source:
        return json.load(source)


def resolve_destination(destination: str) -> dict[str, Any] | None:
    normalized = normalize_text(destination)
    catalog = load_catalog()
    best_match: tuple[int, dict[str, Any]] | None = None

    for profile in catalog["destinations"]:
        candidates = [profile["name"], *profile.get("aliases", [])]
        for candidate in candidates:
            normalized_candidate = normalize_text(candidate)
            if normalized == normalized_candidate:
                return profile
            if normalized_candidate in normalized or normalized in normalized_candidate:
                score = min(len(normalized), len(normalized_candidate))
                if best_match is None or score > best_match[0]:
                    best_match = (score, profile)

    return best_match[1] if best_match else None


def catalog_metadata() -> dict[str, Any]:
    catalog = load_catalog()
    return {
        "catalogVersion": catalog["catalogVersion"],
        "referenceNotice": catalog["referenceNotice"],
        "supportedDestinations": [
            {"id": item["id"], "name": item["name"], "aliases": item.get("aliases", [])}
            for item in catalog["destinations"]
        ],
    }
