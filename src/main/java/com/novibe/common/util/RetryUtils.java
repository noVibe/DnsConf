package com.novibe.common.util;

import java.time.Duration;

public class RetryUtils {

    public static boolean isTemporaryError(int responseCode) {
        return switch (responseCode) {
            case 429, 524 -> true;
            case int code when code >= 500 -> true;
            default -> false;
        };
    }

    public static void waitBeforeRetry(Duration delay, int responseCode, int attempt, int maxAttempts) {
        Log.common("\nCode %s received. Attempt %s of %s, waiting %s seconds before retry"
                .formatted(responseCode, attempt, maxAttempts, delay.toSeconds()));
        for (long secondsLeft = delay.toSeconds(); secondsLeft > 0; secondsLeft--) {
            try {
                Thread.sleep(Duration.ofSeconds(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            Log.progress("Waiting for reset: " + secondsLeft + " seconds");
        }
    }

}
