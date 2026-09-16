package com.aiquote.backend.billing;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Wraps checked StripeException / signature-verification failures into an unchecked,
 * ApiException-compatible error so callers don't need Stripe-specific try/catch. */
public class BillingException extends ApiException {

    public BillingException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message);
        initCause(cause);
    }

    public BillingException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
