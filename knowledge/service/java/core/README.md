# Java State Core

This module contains the Sprint 1 skeleton for the Java state core.

Current scope:

- role-to-space resolution from `.knowledge_registry.json`
- domain records aligned with `knowledge/contracts`
- repository interfaces
- sample repository implementations
- minimal use case and API skeletons
- internal worker transport models kept separate from external API requests

This round does not include database persistence or production orchestration.

Path boundary:

- centralized shared knowledge root: `/meta_claw/knowledge_shared`
- private role spaces: external paths supplied by agent/runtime configuration
- this module does not own or generate private space directories
