package com.meta_claw.knowledge.core.application.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GraphifyWorkerInvoker {

    private final ObjectMapper objectMapper;
    private final String pythonExecutable;
    private final Path workerScriptPath;

    public GraphifyWorkerInvoker(ObjectMapper objectMapper, String pythonExecutable, Path workerScriptPath) {
        this.objectMapper = objectMapper;
        this.pythonExecutable = pythonExecutable;
        this.workerScriptPath = workerScriptPath;
    }

    public WorkerResultEnvelope invoke(WorkerJob job, Path snapshotDir, Path outputDir) throws Exception {
        Path jobFile = Files.createTempFile("job_", ".json");
        objectMapper.writeValue(jobFile.toFile(), job);

        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable,
                workerScriptPath.toString(),
                "--job-file", jobFile.toString(),
                "--output-dir", outputDir.toString()
        );
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);

        int maxRetries = 3;
        long backoffMs = 1000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Process process = pb.start();
                boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new RuntimeException("Worker timed out after 120s");
                }
                if (process.exitValue() != 0) {
                    String stderr = new String(process.getErrorStream().readAllBytes());
                    throw new RuntimeException("Worker exited with code " + process.exitValue() + ": " + stderr);
                }

                String stdout = new String(process.getInputStream().readAllBytes());
                return objectMapper.readValue(stdout, WorkerResultEnvelope.class);
            } catch (Exception e) {
                lastException = e;
                log.warn("Worker attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                }
            }
        }

        return WorkerResultEnvelope.builder()
                .jobId(job.getJobId())
                .spaceId(job.getSpaceId())
                .status("failed")
                .retriable(true)
                .issues(List.of(lastException != null ? lastException.getMessage() : "unknown"))
                .build();
    }
}
