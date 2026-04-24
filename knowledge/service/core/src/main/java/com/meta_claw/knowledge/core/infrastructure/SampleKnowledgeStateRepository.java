package com.meta_claw.knowledge.core.infrastructure;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.KnowledgeControlState;
import com.meta_claw.knowledge.core.domain.KnowledgeStateRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SampleKnowledgeStateRepository implements KnowledgeStateRepository {
    private final Map<String, KnowledgeAsset> assets = new ConcurrentHashMap<>();
    private final Map<String, KnowledgeControlState> controlStates = new ConcurrentHashMap<>();

    @Override
    public void saveAsset(KnowledgeAsset asset) {
        assets.put(asset.getAssetId(), asset);
    }

    @Override
    public void saveControlState(KnowledgeControlState controlState) {
        controlStates.put(controlState.getAssetId(), controlState);
    }

    @Override
    public Optional<KnowledgeAsset> findAssetById(String assetId) {
        return Optional.ofNullable(assets.get(assetId));
    }

    @Override
    public Optional<KnowledgeControlState> findControlStateByAssetId(String assetId) {
        return Optional.ofNullable(controlStates.get(assetId));
    }

    @Override
    public List<KnowledgeAsset> findAssetsBySourceId(String sourceId) {
        List<KnowledgeAsset> result = new ArrayList<>();
        for (KnowledgeAsset asset : assets.values()) {
            if (asset.getSourceId().equals(sourceId)) {
                result.add(asset);
            }
        }
        log.debug("Resolved {} assets for source {}", result.size(), sourceId);
        return result;
    }
}
