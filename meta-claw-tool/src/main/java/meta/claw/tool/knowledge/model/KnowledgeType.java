package meta.claw.tool.knowledge.model;

public enum KnowledgeType {
    FACT("fact"),
    OPINION("opinion"),
    UNKNOWN("unknown");

    private final String value;

    KnowledgeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static KnowledgeType fromValue(String value) {
        for (KnowledgeType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}