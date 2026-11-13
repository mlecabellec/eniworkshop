package fr.lecabellec.eniworkshop;

import java.util.Objects;

/**
 * Domain runtime exception for ENI Workshop operations.
 *
 * <p>Requirement: REQ-00001, Constraint: CS-0030.8.2.
 */
public final class EniWorkshopException extends RuntimeException {

    /** Serialization identifier. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an exception with a descriptive message.
     *
     * @param message non-null error detail message
     */
    public EniWorkshopException(final String message) {
        super(Objects.requireNonNull(message, "Exception message must not be null"));
    }

    /**
     * Constructs an exception with a descriptive message and root cause.
     *
     * @param message non-null error detail message
     * @param cause   non-null underlying cause
     */
    public EniWorkshopException(final String message, final Throwable cause) {
        super(
                Objects.requireNonNull(message, "Exception message must not be null"),
                Objects.requireNonNull(cause, "Exception cause must not be null"));
    }
}
