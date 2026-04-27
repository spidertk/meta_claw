package com.meta_claw.knowledge.core.adapter.inbound.rest;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.meta_claw.knowledge.core.application.view.AgentViewBuilder;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class KnowledgeRestController {

    private final KnowledgeFlowFacade flowFacade;
    private final AgentViewBuilder agentViewBuilder;

    public KnowledgeRestController(KnowledgeFlowFacade flowFacade, AgentViewBuilder agentViewBuilder) {
        this.flowFacade = flowFacade;
        this.agentViewBuilder = agentViewBuilder;
    }

    @PostMapping("/sources")
    public ResponseEntity<SourceRegistrationResult> registerSource(@RequestBody SourceRegistrationRequest request) {
        SourceRegistrationResult result = flowFacade.registerSource(
                RegisterSourceFlowContext.builder().request(request).build()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jobs")
    public ResponseEntity<WorkerJob> submitJob(@RequestBody SubmitWorkerJobRequest request) {
        WorkerJob job = flowFacade.submitWorkerJob(
                SubmitWorkerJobFlowContext.builder().request(request).build()
        );
        return ResponseEntity.ok(job);
    }

    @PostMapping("/results")
    public ResponseEntity<WorkerResult> ingestResult(@RequestBody WorkerResultEnvelope envelope) {
        WorkerResult result = flowFacade.ingestWorkerResult(
                com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext.builder()
                        .envelope(envelope)
                        .build()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/views")
    public ResponseEntity<String> getView(@RequestParam String role) {
        String markdown = agentViewBuilder.buildMarkdownView(role);
        return ResponseEntity.ok(markdown);
    }
}
