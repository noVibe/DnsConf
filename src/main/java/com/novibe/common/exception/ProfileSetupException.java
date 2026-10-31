package com.novibe.common.exception;

import com.novibe.common.util.ProfileFailuresCollector;
import lombok.experimental.StandardException;

/**
 * Something went wrong with a single profile, but the rest of the profiles can still be set up.
 * Such failures are collected in {@link ProfileFailuresCollector} and reported at the end of the run.
 */
@StandardException
public class ProfileSetupException extends RuntimeException {
}
