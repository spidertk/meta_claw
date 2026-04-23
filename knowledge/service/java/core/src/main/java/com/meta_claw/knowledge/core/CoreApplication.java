package com.meta_claw.knowledge.core;

import com.meta_claw.knowledge.core.api.CoreController;
import com.meta_claw.knowledge.core.application.IngestWorkerResultUseCase;
import com.meta_claw.knowledge.core.application.RegisterSourceUseCase;
import com.meta_claw.knowledge.core.application.ResolveKnowledgeSpaceUseCase;
import com.meta_claw.knowledge.core.application.SubmitWorkerJobUseCase;
import com.meta_claw.knowledge.core.infrastructure.SampleKnowledgeRegistryRepository;
import com.meta_claw.knowledge.core.infrastructure.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.infrastructure.SampleSourceRegistryRepository;

public class CoreApplication {
    public static void main(String[] args) {
        SampleKnowledgeRegistryRepository knowledgeRegistryRepository = new SampleKnowledgeRegistryRepository();
        SampleSourceRegistryRepository sourceRegistryRepository = new SampleSourceRegistryRepository();
        SampleKnowledgeStateRepository knowledgeStateRepository = new SampleKnowledgeStateRepository();

        CoreController controller = new CoreController(
                new ResolveKnowledgeSpaceUseCase(knowledgeRegistryRepository),
                new RegisterSourceUseCase(sourceRegistryRepository),
                new SubmitWorkerJobUseCase(),
                new IngestWorkerResultUseCase(knowledgeStateRepository)
        );

        System.out.println("Knowledge Java core skeleton is ready for role "
                + controller.resolveKnowledgeSpace("finance_advisor").roleName());
    }
}
