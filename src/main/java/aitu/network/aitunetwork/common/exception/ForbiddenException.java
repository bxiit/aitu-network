package aitu.network.aitunetwork.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends SecureTalkException {
    private static final HttpStatus HTTP_STATUS = HttpStatus.FORBIDDEN;

    public ForbiddenException(String message) {
        super(message, HTTP_STATUS);
    }
}
