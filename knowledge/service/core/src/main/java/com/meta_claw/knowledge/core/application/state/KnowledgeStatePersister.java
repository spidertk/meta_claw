package com.meta_claw.knowledge.core.application.state;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.KnowledgeControlState;
import com.meta_claw.knowledge.core.repository.KnowledgeStateRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

@Slf4j
public class KnowledgeStatePersister {

    private final KnowledgeStateRepository repository;

    public KnowledgeStatePersister(KnowledgeStateRepository repository) {
        this.repository = repository;
    }

    public void persistAll(String spaceId, String jobId, List<KnowledgeAsset> assets) {
        for (KnowledgeAsset asset : assets) {
            KnowledgeAsset enriched = asset.toBuilder()
                    .spaceId(spaceId)
                    .status("ready")
                    .createdAt(Instant.now())
                    .build();
            repository.saveAsset(enriched);

            KnowledgeControlState control = KnowledgeControlState.builder()
                    .spaceId(spaceId)
                    .assetId(asset.getAssetId())
                    .reviewStatus("candidate")
                    .issues(List.of())
                    .updatedAt(Instant.now())
                    .build();
            repository.saveControlState(control);

            log.info("Persisted asset={}, control_state=candidate, job={}", asset.getAssetId(), jobId);
        }
    }
}
