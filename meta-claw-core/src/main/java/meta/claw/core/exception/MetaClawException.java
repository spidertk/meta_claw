package meta.claw.core.exception;

import lombok.Getter;

@Getter
public class MetaClawException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public MetaClawException(ErrorCode errorCode, Object... args) {
        super(errorCode.format(args));
        this.errorCode = errorCode;
        this.args = args;
    }

    public MetaClawException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.format(args), cause);
        this.errorCode = errorCode;
        this.args = args;
    }
}
