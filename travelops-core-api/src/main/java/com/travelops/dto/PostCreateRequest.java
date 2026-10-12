package com.travelops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20000) String content,
        @NotBlank @Size(max = 100) String destinationCity,
        @NotBlank @Size(max = 100) String destinationCountry) {
}
