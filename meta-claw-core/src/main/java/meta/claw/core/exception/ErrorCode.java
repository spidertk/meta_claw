package meta.claw.core.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // Vessel domain (Vxxx)
    VESSEL_META_NOT_FOUND("V001", "Vessel config file not found: {0}"),
    VESSEL_PROFILE_NOT_FOUND("V002", "Vessel profile not found: {0}"),
    VESSEL_GLOBAL_CONFIG_EMPTY("V003", "Global config not found or providers empty: {0}"),
    VESSEL_PROVIDER_NOT_FOUND("V004", "Provider '{0}' not found in global config. Available: {1}"),
    VESSEL_API_KEY_MISSING("V005", "API key not set for provider '{0}'"),
    VESSEL_MODEL_MISSING("V006", "Model not set for provider '{0}'"),
    VESSEL_META_PARSE_ERROR("V007", "Failed to parse vessel config: {0}"),
    VESSEL_PROFILE_PARSE_ERROR("V008", "Failed to parse vessel profile: {0}");

    private final String code;
    private final String template;

    ErrorCode(String code, String template) {
        this.code = code;
        this.template = template;
    }

    public String format(Object... args) {
        String msg = template;
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }
}
