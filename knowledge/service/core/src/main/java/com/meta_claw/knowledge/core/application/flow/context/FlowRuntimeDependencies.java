package com.meta_claw.knowledge.core.application.flow.context;

import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.repository.KnowledgeStateRepository;
import com.meta_claw.knowledge.core.repository.KnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.repository.SnapshotStoreRepository;
import com.meta_claw.knowledge.core.repository.SourceRegistryRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowRuntimeDependencies {

    private KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository;
    private SourceRegistryRepository sourceRegistryRepository;
    private SnapshotStoreRepository snapshotStoreRepository;
    private KnowledgeStateRepository knowledgeStateRepository;
    private SourceIntakeConfig sourceIntakeConfig;
}
