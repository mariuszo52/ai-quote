package com.aiquote.backend.offer;

import com.aiquote.backend.auth.AppUser;
import com.aiquote.backend.auth.AppUserRepository;
import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.feedback.QuoteFeedbackService;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadNotFoundException;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.quote.InvalidQuoteStatusException;
import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteLineItem;
import com.aiquote.backend.quote.QuoteNotFoundException;
import com.aiquote.backend.quote.QuoteRepository;
import com.aiquote.backend.quote.QuoteResponse;
import com.aiquote.backend.quote.QuoteService;
import com.aiquote.backend.quote.QuoteStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Quote-approval -> final-offer pipeline (Etap 12). Depends on QuoteService/
 * QuoteRepository (offer -> quote is the allowed direction, same as quote -> lead) so
 * that approveQuoteAndGenerateOffer can wrap both the status flip and the offer/PDF
 * creation in one transaction: if PDF rendering or MinIO storage fails, the whole
 * approval rolls back instead of leaving an APPROVED quote with no offer. Also
 * generates the QuoteFeedback AI-vs-final comparison row (Etap 15) in the same
 * transaction, for the same reason — feedback depends on quote just like offer does,
 * so this is the natural single place to compose both "approval side effects."
 */
@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository offerRepository;
    private final QuoteRepository quoteRepository;
    private final LeadRepository leadRepository;
    private final QuoteService quoteService;
    private final CompanyService companyService;
    private final AppUserRepository appUserRepository;
    private final StorageService storageService;
    private final OfferPdfGenerator offerPdfGenerator;
    private final QuoteFeedbackService quoteFeedbackService;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuoteResponse approveQuoteAndGenerateOffer(Long companyId, Long quoteId) {
        QuoteResponse response = quoteService.approve(companyId, quoteId);
        generateForApprovedQuote(companyId, quoteId);
        quoteFeedbackService.generateForApprovedQuote(companyId, quoteId);
        return response;
    }

    @Transactional
    public Offer generateForApprovedQuote(Long companyId, Long quoteId) {
        Quote quote = quoteRepository.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));
        if (quote.getStatus() != QuoteStatus.APPROVED) {
            throw new InvalidQuoteStatusException("Ofertę końcową można wygenerować tylko dla zaakceptowanej wyceny.");
        }
        Lead lead = leadRepository.findById(quote.getLeadId())
                .orElseThrow(() -> new LeadNotFoundException(quote.getLeadId()));
        Company company = companyService.getById(companyId);
        String contactEmail = resolveContactEmail(company);

        List<OfferLineItem> items = readQuoteItems(quote.getApprovedItemsJson()).stream()
                .map(item -> new OfferLineItem(item.name(), item.description(), item.quantity(), item.unit(), item.unitPrice(), item.totalPrice()))
                .toList();

        Offer offer = new Offer(
                companyId,
                quote.getId(),
                UUID.randomUUID().toString(),
                quote.getCurrency(),
                writeJson(items),
                quote.getApprovedTotal(),
                lead.getClientName(),
                lead.getClientPhone(),
                lead.getClientEmail(),
                lead.getAiSummary(),
                quote.getEstimatedTimeline(),
                quote.getOfferValidUntil(),
                "pending");
        offer = offerRepository.save(offer);

        byte[] pdf = offerPdfGenerator.generate(offer, company, contactEmail);
        String storageKey = storageService.store(
                companyId, "oferta-" + offer.getId() + ".pdf", "application/pdf", new ByteArrayInputStream(pdf), pdf.length);
        offer.attachPdf(storageKey);
        return offer;
    }

    public OfferResponse getByQuoteId(Long companyId, Long quoteId) {
        return toResponse(requireOwned(companyId, quoteId));
    }

    public byte[] getPdf(Long companyId, Long quoteId) {
        Offer offer = requireOwned(companyId, quoteId);
        return readAll(offer.getPdfStorageKey());
    }

    public PublicOfferResponse getPublicOffer(String publicToken) {
        Offer offer = offerRepository.findByPublicToken(publicToken)
                .orElseThrow(() -> new OfferNotFoundException(publicToken));
        return toPublicResponse(offer);
    }

    public byte[] getPublicPdf(String publicToken) {
        Offer offer = offerRepository.findByPublicToken(publicToken)
                .orElseThrow(() -> new OfferNotFoundException(publicToken));
        return readAll(offer.getPdfStorageKey());
    }

    private byte[] readAll(String storageKey) {
        try {
            return storageService.retrieve(storageKey).readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored offer PDF", e);
        }
    }

    /** Prefers the company's own branding contact email (Etap 17) over the owner's raw
     * login email — the latter is just a sensible fallback for companies that haven't
     * set one. */
    private String resolveContactEmail(Company company) {
        if (company.getContactEmail() != null && !company.getContactEmail().isBlank()) {
            return company.getContactEmail();
        }
        return appUserRepository.findFirstByCompanyIdOrderByIdAsc(company.getId())
                .map(AppUser::getEmail)
                .orElse(null);
    }

    private Offer requireOwned(Long companyId, Long quoteId) {
        return offerRepository.findByQuoteIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new OfferNotFoundException(quoteId));
    }

    private OfferResponse toResponse(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getQuoteId(),
                offer.getPublicToken(),
                offer.getStatus().name(),
                offer.getCurrency(),
                readItems(offer.getItemsJson()),
                offer.getTotal(),
                offer.getClientName(),
                offer.getClientPhone(),
                offer.getClientEmail(),
                offer.getJobDescription(),
                offer.getEstimatedTimeline(),
                offer.getValidUntil(),
                offer.getSentAt(),
                offer.getLastSendError(),
                offer.getCreatedAt());
    }

    private PublicOfferResponse toPublicResponse(Offer offer) {
        Company company = companyService.getById(offer.getCompanyId());
        String contactEmail = resolveContactEmail(company);
        return new PublicOfferResponse(
                offer.getId(),
                company.getDisplayName(),
                contactEmail,
                offer.getCurrency(),
                readItems(offer.getItemsJson()),
                offer.getTotal(),
                offer.getClientName(),
                offer.getJobDescription(),
                offer.getEstimatedTimeline(),
                offer.getValidUntil(),
                offer.getCreatedAt());
    }

    private List<QuoteLineItem> readQuoteItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuoteLineItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse approved quote items", e);
        }
    }

    private List<OfferLineItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<OfferLineItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse offer items", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize offer items", e);
        }
    }
}
