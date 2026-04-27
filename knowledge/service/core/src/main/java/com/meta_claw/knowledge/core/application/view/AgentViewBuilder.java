package com.meta_claw.knowledge.core.application.view;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.repository.KnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.repository.KnowledgeStateRepository;

import java.util.List;

public class AgentViewBuilder {

    private final KnowledgeSpaceBindingRepository spaceBindingRepository;
    private final KnowledgeStateRepository stateRepository;

    public AgentViewBuilder(KnowledgeSpaceBindingRepository spaceBindingRepository,
                            KnowledgeStateRepository stateRepository) {
        this.spaceBindingRepository = spaceBindingRepository;
        this.stateRepository = stateRepository;
    }

    public String buildMarkdownView(String roleName) {
        AgentRoleBinding binding = spaceBindingRepository.findByRoleName(roleName).orElse(null);
        if (binding == null) {
            return "# Knowledge View for " + roleName + "\n\nNo binding found.\n";
        }

        List<String> spaceIds = new java.util.ArrayList<>(binding.getInherits());
        spaceIds.add(binding.getSpaceId());

        List<KnowledgeAsset> assets = stateRepository.findAll().stream()
                .filter(a -> spaceIds.contains(a.getSpaceId()))
                .filter(a -> "ready".equals(a.getStatus()))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("# Knowledge View for ").append(roleName).append("\n\n");

        for (String spaceId : spaceIds) {
            sb.append("## Space: ").append(spaceId).append("\n\n");
            List<KnowledgeAsset> spaceAssets = assets.stream()
                    .filter(a -> spaceId.equals(a.getSpaceId()))
                    .toList();

            if (spaceAssets.isEmpty()) {
                sb.append("_No assets yet._\n\n");
                continue;
            }

            for (KnowledgeAsset asset : spaceAssets) {
                sb.append("- **").append(asset.getAssetType()).append("** : ")
                  .append(asset.getAssetId())
                  .append(" (coverage: ").append(asset.getCoverage()).append(")\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
