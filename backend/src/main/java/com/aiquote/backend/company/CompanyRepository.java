package com.aiquote.backend.company;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Used by the Stripe webhook handler to find which company a subscription event belongs to. */
    Optional<Company> findByStripeCustomerId(String stripeCustomerId);
}
