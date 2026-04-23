from __future__ import annotations

import argparse
import json
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_EXAMPLES_DIR = ROOT / "examples"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def resolve_examples_dir(cli_value: str | None) -> Path:
    if cli_value:
        return Path(cli_value).expanduser().resolve()

    env_value = os.environ.get("KNOWLEDGE_EXAMPLES_DIR")
    if env_value:
        return Path(env_value).expanduser().resolve()

    return DEFAULT_EXAMPLES_DIR


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sprint 1 Python worker stub")
    parser.add_argument("--examples-dir", help="Directory containing shared example fixtures")
    parser.add_argument("--job-file", default="job-contract.example.json", help="Job fixture filename")
    parser.add_argument("--result-file", help="Result fixture filename override")
    parser.add_argument("--fail", action="store_true", help="Use the failure result fixture")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    examples_dir = resolve_examples_dir(args.examples_dir)
    job_path = examples_dir / args.job_file
    result_name = args.result_file or ("artifact-failure.example.json" if args.fail else "artifact-success.example.json")
    result_path = examples_dir / result_name

    job = load_json(job_path)
    result = load_json(result_path)

    payload = {
        "job_id": job["job_id"],
        "job_path": str(job_path),
        "result_path": str(result_path),
        "result": result,
    }
    print(json.dumps(payload, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
