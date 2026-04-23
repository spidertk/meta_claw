from __future__ import annotations

import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
EXAMPLES_DIR = ROOT / "examples"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    job = load_json(EXAMPLES_DIR / "job-contract.example.json")
    result_name = "artifact-failure.example.json" if "--fail" in sys.argv else "artifact-success.example.json"
    result_path = EXAMPLES_DIR / result_name
    result = load_json(result_path)

    payload = {
        "job_id": job["job_id"],
        "result_path": str(result_path),
        "result": result,
    }
    print(json.dumps(payload, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
