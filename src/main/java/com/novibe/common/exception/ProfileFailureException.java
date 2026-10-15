package com.novibe.common.exception;

import lombok.experimental.StandardException;

/**
 * At least one profile was not set up. Thrown at the end of the run so that the exit code is not zero.
 */
@StandardException
public class ProfileFailureException extends RuntimeException {

    private ProfileFailureException(String message) {
        super(message, null, false, false);
    }

    public static ProfileFailureException noStackTrace(String message) {
        return new ProfileFailureException(message);
    }

}
