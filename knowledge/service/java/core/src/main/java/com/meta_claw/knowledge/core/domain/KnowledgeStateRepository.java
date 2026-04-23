package com.meta_claw.knowledge.core.domain;

import java.util.List;
import java.util.Optional;

public interface KnowledgeStateRepository {
    void saveAsset(KnowledgeAsset asset);

    void saveControlState(KnowledgeControlState controlState);

    Optional<KnowledgeAsset> findAssetById(String assetId);

    Optional<KnowledgeControlState> findControlStateByAssetId(String assetId);

    List<KnowledgeAsset> findAssetsBySourceId(String sourceId);
}
