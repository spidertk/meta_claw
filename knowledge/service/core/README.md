# Java State Core

This module contains the Sprint 1 skeleton for the Java state core.

Current scope:

- role-to-space resolution through the repository/facade main path
- domain records aligned with `knowledge/contracts`
- repository interfaces
- demo and JSONL file repository implementations
- LiteFlow facade + chain/node application orchestration
- internal worker transport models kept separate from external API requests

This round does not include database persistence or production orchestration.

Path boundary:

- centralized shared knowledge root: `/meta_claw/knowledge_shared`
- private role spaces: external paths supplied by agent/runtime configuration
- this module does not own or generate private space directories
