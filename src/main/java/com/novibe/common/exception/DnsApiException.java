package com.novibe.common.exception;

import lombok.experimental.StandardException;

/**
 * A dns provider api refused to apply a part of the configuration.
 */
@StandardException
public class DnsApiException extends ProfileSetupException {
}
