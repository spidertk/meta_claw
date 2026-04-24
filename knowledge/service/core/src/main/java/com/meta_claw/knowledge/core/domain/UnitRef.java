package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UnitRef {
    private String unitId;
    private String snapshotId;
    private String parentUnitId;
    private String unitType;
    private String path;
    private String displayName;
    private List<String> neighbors;
}
