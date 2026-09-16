package com.aiquote.backend.feedback;

/** Both fields optional — submitting feedback must never be required to approve or
 * otherwise use a quote (Etap 15 #2). */
public record SubmitFeedbackRequest(FeedbackReason reason, String note) {
}
