package com.meta_claw.knowledge.core.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.meta_claw.knowledge.core.application.archive.ArtifactArchiveService;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.meta_claw.knowledge.core.application.intake.RwaSourceResolver;
import com.meta_claw.knowledge.core.application.view.AgentViewBuilder;
import com.meta_claw.knowledge.core.application.worker.GraphifyWorkerInvoker;
import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExpertProjectEndToEndTest {

    @Autowired
    private KnowledgeFlowFacade flowFacade;

    @Autowired
    private AgentViewBuilder viewBuilder;

    @Test
    @DisplayName("expert_project 真实来源注册 → graphify Worker → artifact 归档 → view 查询")
    void expertProjectFullPipeline(@TempDir Path tempArchiveDir) throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.home"), "IdeaProjects", "meta_claw");
        Path rwaRoot = projectRoot.resolve(".rwa");

        // 1. 从 .rwa 解析 expert_project
        RwaSourceResolver resolver = new RwaSourceResolver(rwaRoot);
        var requests = resolver.resolve("expert_project");
        assertThat(requests).hasSize(1);
        var request = requests.get(0).toBuilder()
                .roleName("finance_advisor")
                .build();

        // 2. 注册来源
        SourceRegistrationResult reg = flowFacade.registerSource(
                RegisterSourceFlowContext.builder().request(request).build()
        );
        assertThat(reg.getSourceRecord().getSourceId()).isNotBlank();
        assertThat(reg.getSnapshotRecord().getSnapshotId()).isNotBlank();
        String sourceId = reg.getSourceRecord().getSourceId();
        String snapshotId = reg.getSnapshotRecord().getSnapshotId();
        String spaceId = reg.getSourceRecord().getSpaceId();

        // 3. 提交 worker job
        WorkerJob job = flowFacade.submitWorkerJob(
                SubmitWorkerJobFlowContext.builder()
                        .request(com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest.builder()
                                .roleName(request.getRoleName())
                                .jobId("job_expert_project_001")
                                .jobType("extract_graph_and_wiki")
                                .sourceId(sourceId)
                                .snapshotId(snapshotId)
                                .expectedArtifacts(List.of("graph", "wiki"))
                                .build())
                        .build()
        );
        assertThat(job.getJobId()).isEqualTo("job_expert_project_001");

        // 4. 真实调用 Python Worker
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.findAndRegisterModules();

        Path workerScript = projectRoot.resolve("knowledge/workers/python/worker_entry.py");

        GraphifyWorkerInvoker invoker = new GraphifyWorkerInvoker(
                objectMapper, "python3", workerScript
        );

        Path snapshotDir = Path.of(request.getLocation());
        Path workerOutputDir = tempArchiveDir.resolve("worker_output");

        WorkerResultEnvelope envelope = invoker.invoke(job, snapshotDir, workerOutputDir);

        assertThat(envelope.getStatus()).isEqualTo("completed");
        assertThat(envelope.getArtifacts()).isNotEmpty();

        // 5. Artifact 归档
        ArtifactArchiveService archiveService = new ArtifactArchiveService(tempArchiveDir);
        for (KnowledgeAsset artifact : envelope.getArtifacts()) {
            String type = artifact.getAssetType();
            Path tempFile = workerOutputDir.resolve(type.equals("wiki") ? "wiki.md" : "graph.json");
            if (java.nio.file.Files.exists(tempFile)) {
                Path archived = archiveService.archive(
                        spaceId, sourceId, snapshotId, job.getJobId(), type, tempFile
                );
                assertThat(archived).exists();
            }
        }

        // 6. 构建 enriched envelope 并摄入结果
        WorkerResultEnvelope enriched = envelope.toBuilder()
                .spaceId(spaceId)
                .jobId(job.getJobId())
                .artifacts(List.of(
                        KnowledgeAsset.builder()
                                .spaceId(spaceId)
                                .assetId("asset_graph_" + job.getJobId())
                                .assetType("graph")
                                .sourceId(sourceId)
                                .snapshotId(snapshotId)
                                .status("ready")
                                .coverage(envelope.getCoverage())
                                .scope(envelope.getScope())
                                .build(),
                        KnowledgeAsset.builder()
                                .spaceId(spaceId)
                                .assetId("asset_wiki_" + job.getJobId())
                                .assetType("wiki")
                                .sourceId(sourceId)
                                .snapshotId(snapshotId)
                                .status("ready")
                                .coverage(envelope.getCoverage())
                                .scope(envelope.getScope())
                                .build()
                ))
                .build();

        WorkerResult workerResult = enriched.toDomain();
        flowFacade.ingestWorkerResult(
                IngestWorkerResultFlowContext.builder()
                        .envelope(enriched)
                        .workerResult(workerResult)
                        .result(workerResult)
                        .build()
        );

        // 7. 查询 view 验证
        String view = viewBuilder.buildMarkdownView(request.getRoleName());
        assertThat(view).contains("Knowledge View for " + request.getRoleName());
        assertThat(view).contains("graph");
        assertThat(view).contains("wiki");
    }
}
