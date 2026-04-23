package com.meta_claw.knowledge.core.application;

import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistryRepository;

public class RegisterSourceUseCase {
    private final SourceRegistryRepository sourceRegistryRepository;

    public RegisterSourceUseCase(SourceRegistryRepository sourceRegistryRepository) {
        this.sourceRegistryRepository = sourceRegistryRepository;
    }

    public SourceRecord execute(SourceRecord sourceRecord) {
        sourceRegistryRepository.save(sourceRecord);
        return sourceRecord;
    }
}
