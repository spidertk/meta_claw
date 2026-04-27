from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


def run_graphify(source_dir: Path, output_dir: Path) -> dict[str, Any]:
    """
    调用 graphify 分析 source_dir，产出 graph.json 到 output_dir。
    若 graphify 未安装，则产出一个最小占位 graph。
    """
    graphify_out = source_dir / "graphify-out"
    graph_file = graphify_out / "graph.json"

    # 尝试调用 graphify CLI: graphify update <source_dir>
    try:
        result = subprocess.run(
            ["graphify", "update", str(source_dir)],
            capture_output=True,
            text=True,
            timeout=300,
        )
        if result.returncode == 0 and graph_file.exists():
            graph_data = json.loads(graph_file.read_text(encoding="utf-8"))
            # 同时复制到 output_dir 以便归档
            output_dir.mkdir(parents=True, exist_ok=True)
            (output_dir / "graph.json").write_text(
                json.dumps(graph_data, indent=2), encoding="utf-8"
            )
            return graph_data
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass

    # Fallback: 产出最小占位 graph
    placeholder = {
        "meta": {"tool": "graphify", "version": "0.1.0-fallback"},
        "nodes": [],
        "edges": [],
        "communities": [],
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "graph.json").write_text(
        json.dumps(placeholder, indent=2), encoding="utf-8"
    )
    return placeholder


def generate_wiki(graph: dict[str, Any], output_dir: Path) -> Path:
    """基于 graph 产出最小 wiki.md。"""
    wiki_file = output_dir / "wiki.md"
    lines = ["# Auto-generated Wiki\n", "## Summary\n"]

    nodes = graph.get("nodes", [])
    if nodes:
        lines.append(f"- Discovered {len(nodes)} nodes\n")
    else:
        lines.append("- No structured nodes found (placeholder mode)\n")

    wiki_file.write_text("".join(lines), encoding="utf-8")
    return wiki_file
