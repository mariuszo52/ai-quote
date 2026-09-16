package com.aiquote.backend.quote;

import java.util.List;

public record QuoteMessageResponse(String reply, QuoteDto quote, List<String> options) {
}
