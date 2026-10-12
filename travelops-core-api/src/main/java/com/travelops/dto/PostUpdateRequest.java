package com.travelops.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH body: a null field means "leave unchanged". A field that is present must not be blank,
 * which @NotBlank cannot express because it also rejects null.
 */
public record PostUpdateRequest(
        @Size(max = 200) @Pattern(regexp = NOT_BLANK, message = MUST_NOT_BE_BLANK) String title,
        @Size(max = 20000) @Pattern(regexp = NOT_BLANK, message = MUST_NOT_BE_BLANK) String content,
        @Size(max = 100) @Pattern(regexp = NOT_BLANK, message = MUST_NOT_BE_BLANK) String destinationCity,
        @Size(max = 100) @Pattern(regexp = NOT_BLANK, message = MUST_NOT_BE_BLANK) String destinationCountry) {

    static final String NOT_BLANK = "(?s).*\\S.*";
    static final String MUST_NOT_BE_BLANK = "must not be blank";
}
