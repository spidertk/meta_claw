"""
Knowledge Worker 入口

职责: 解析 job contract，调用可编排抽取流水线，返回 artifact 结果。
支持三种分析模式:
- ast_only: 纯 AST 提取（代码仓库）
- llm_enhanced: AST + LLM 语义增强（混合来源）
- cli_delegate: 委托 Code CLI（需 CLI 在线）
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

# 导入所有抽取器和合成器，触发注册
import extractor_pipeline
import extractors.graphify_ast_extractor
import extractors.llm_semantic_extractor
import extractors.cli_bridge_extractor
import synthesizers.wiki_synthesizer


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Knowledge Worker (Orchestrated Pipeline)")
    parser.add_argument("--job-file", required=True, help="Path to job contract JSON")
    parser.add_argument("--output-dir", required=True, help="Directory to write artifacts")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    job_path = Path(args.job_file)
    output_dir = Path(args.output_dir)

    job = json.loads(job_path.read_text(encoding="utf-8"))
    job["output_dir"] = str(output_dir)

    # 运行可编排抽取流水线
    result = extractor_pipeline.run_pipeline(job)

    # 构建 artifact 报告
    artifacts = []
    graph_file = result.get("graph_file")
    wiki_dir = result.get("wiki_dir")

    if graph_file and Path(graph_file).exists():
        artifacts.append({
            "asset_type": "graph",
            "path": graph_file,
            "format": "json",
        })

    if wiki_dir and Path(wiki_dir).exists():
        artifacts.append({
            "asset_type": "wiki",
            "path": wiki_dir,
            "format": "markdown",
        })

    # 计算 coverage
    graph = result.get("graph", {})
    meta = graph.get("meta", {})
    has_semantic = meta.get("semantic_node_count", 0) > 0
    coverage = "full" if has_semantic else "partial"

    report = {
        "job_id": job.get("job_id", "unknown"),
        "status": "completed" if artifacts else "failed",
        "retriable": False,
        "artifacts": artifacts,
        "issues": [],
        "coverage": coverage,
        "scope": job.get("processing_scope", "latest_snapshot"),
        "analysis_mode": job.get("analysis_mode", "ast_only"),
        "meta": meta,
    }

    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
