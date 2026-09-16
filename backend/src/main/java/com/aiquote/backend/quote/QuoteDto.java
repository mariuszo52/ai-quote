package com.aiquote.backend.quote;

import java.util.List;

public record QuoteDto(double minPrice, double maxPrice, String currency, String reasoning, List<String> uncertainFactors) {
}
