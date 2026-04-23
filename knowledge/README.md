# Knowledge

`knowledge/` is the root for the private knowledge base implementation.

Sprint 1 establishes:

- shared contracts in `knowledge/contracts`
- shared examples in `knowledge/examples`
- Java state-core skeleton in `knowledge/service/java/core`
- worker skeletons in `knowledge/workers`

This round does not include real ingest, graph building, wiki generation, or persistence.

`knowledge/` contains the reusable framework only.

- shared runtime knowledge is centralized under `/meta_claw/knowledge_shared`
- private agent spaces are external runtime paths provided to the core by agent/runtime configuration
