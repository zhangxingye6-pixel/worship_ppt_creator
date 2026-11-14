package claygminx.worshipppt.exception;

/**
 * 信条服务异常
 */
public class ConfessionServiceException extends RuntimeException {
    public ConfessionServiceException(String message) {
        super(message);
    }

    public ConfessionServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
