from __future__ import annotations

import argparse
import json
from pathlib import Path

from graphify_adapter import run_graphify, generate_wiki


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Knowledge Worker")
    parser.add_argument("--job-file", required=True, help="Path to job contract JSON")
    parser.add_argument("--output-dir", required=True, help="Directory to write artifacts")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    job_path = Path(args.job_file)
    output_dir = Path(args.output_dir)

    job = json.loads(job_path.read_text(encoding="utf-8"))
    source_dir = Path(job.get("snapshot_dir", "."))

    # 调用 graphify
    graph = run_graphify(source_dir, output_dir)
    wiki_file = generate_wiki(graph, output_dir)

    result = {
        "job_id": job["job_id"],
        "status": "completed",
        "retriable": False,
        "artifacts": [
            {
                "asset_type": "graph",
                "path": str(output_dir / "graph.json"),
                "format": "json",
            },
            {
                "asset_type": "wiki",
                "path": str(wiki_file),
                "format": "markdown",
            },
        ],
        "issues": [],
        "coverage": "partial" if not graph.get("nodes") else "full",
        "scope": job.get("processing_scope", "latest_snapshot"),
    }

    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
