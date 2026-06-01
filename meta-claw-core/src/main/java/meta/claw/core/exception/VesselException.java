package meta.claw.core.exception;

public class VesselException extends MetaClawException {

    public VesselException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public VesselException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
