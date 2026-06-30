package meta.claw.tool.knowledge.model;

public enum KnowledgeStatus {
    ACTIVE("active"),
    SUPERSEDED("superseded"),
    DEPRECATED("deprecated"),
    DRAFT("draft");

    private final String value;

    KnowledgeStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static KnowledgeStatus fromValue(String value) {
        for (KnowledgeStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return ACTIVE;
    }
}