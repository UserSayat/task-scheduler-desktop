package org.example.taskschedulerdesktop.exeptions;

import java.util.List;

public class NotFoundException extends RuntimeException {
    private Integer errorCode;
    private List<String> details;

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Integer errorCode, List<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public NotFoundException(String message, Throwable cause, Integer errorCode, List<String> details) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public List<String> getDetails() {
        return details;
    }
}
