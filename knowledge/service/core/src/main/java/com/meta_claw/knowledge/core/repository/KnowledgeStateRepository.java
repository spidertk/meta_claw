package com.meta_claw.knowledge.core.repository;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.KnowledgeControlState;

import java.util.List;
import java.util.Optional;

public interface KnowledgeStateRepository {
    void saveAsset(KnowledgeAsset asset);

    void saveControlState(KnowledgeControlState controlState);

    Optional<KnowledgeAsset> findAssetById(String assetId);

    Optional<KnowledgeControlState> findControlStateByAssetId(String assetId);

    List<KnowledgeAsset> findAssetsBySourceId(String sourceId);
}
