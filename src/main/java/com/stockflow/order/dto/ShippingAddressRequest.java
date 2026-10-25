package com.stockflow.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where the parcel goes. Germany only for now, so there is no country field: the server sets it (a client
 * sending one is ignored). Limits are DHL's own field limits, so DHL never rejects at ship time what we
 * accepted at checkout. Every value is trimmed before validation and before the idempotency hash, so
 * " Berlin " and "Berlin" are the same address. Trimming removes every Unicode space (incl. NBSP), not only
 * what {@code String.strip()} and {@code @NotBlank} treat as blank, so a field of just "\u00A0" is blank.
 *
 * @param postcode exactly five ASCII digits ({@code \d} in Java is ASCII-only, so "١٢٣٤٥" is rejected)
 */
public record ShippingAddressRequest(

        @NotBlank
        @Size(max = NAME_MAX)
        @Pattern(regexp = NO_CONTROL_CHARACTERS, message = NO_CONTROL_CHARACTERS_MESSAGE)
        String name,

        @NotBlank
        @Size(max = STREET_MAX)
        @Pattern(regexp = NO_CONTROL_CHARACTERS, message = NO_CONTROL_CHARACTERS_MESSAGE)
        String street,

        @NotBlank
        @Size(max = HOUSE_NUMBER_MAX)
        @Pattern(regexp = NO_CONTROL_CHARACTERS, message = NO_CONTROL_CHARACTERS_MESSAGE)
        String houseNumber,

        @NotNull
        @Pattern(regexp = GERMAN_POSTCODE, message = "must be a German postcode (5 digits)")
        String postcode,

        @NotBlank
        @Size(max = CITY_MAX)
        @Pattern(regexp = NO_CONTROL_CHARACTERS, message = NO_CONTROL_CHARACTERS_MESSAGE)
        String city) {

    public static final int NAME_MAX = 50;
    public static final int STREET_MAX = 50;
    public static final int HOUSE_NUMBER_MAX = 10;
    public static final int CITY_MAX = 40;
    public static final String GERMAN_POSTCODE = "^\\d{5}$";
    /**
     * Line breaks and other control characters would end up on a printed label. Unicode categories, not
     * {@code \p{Cntrl}} (ASCII only): NEL (U+0085) and the line / paragraph separators are line breaks too,
     * and format characters (Cf: BOM, zero-width, bidi overrides such as U+202E) are invisible or reorder
     * the printed text.
     */
    public static final String NO_CONTROL_CHARACTERS = "^[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*$";
    // Fully qualified: Pattern is the Bean Validation annotation in this file.
    private static final java.util.regex.Pattern EDGE_SPACES = java.util.regex.Pattern.compile("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$");
    private static final String NO_CONTROL_CHARACTERS_MESSAGE = "must not contain line breaks or control characters";

    public ShippingAddressRequest {
        name = trim(name);
        street = trim(street);
        houseNumber = trim(houseNumber);
        postcode = trim(postcode);
        city = trim(city);
    }

    private static String trim(String value) {
        return value == null ? null : EDGE_SPACES.matcher(value).replaceAll("");
    }
}
