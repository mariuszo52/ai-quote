package com.aiquote.backend.knowledgebase;

/** Where a PriceListItem came from — shown to the owner in "Moje materiały" so they can
 * tell what they typed themselves apart from what AI picked up. */
public enum PriceListItemSource {
    MANUAL,
    UPLOADED,
    CHAT
}
