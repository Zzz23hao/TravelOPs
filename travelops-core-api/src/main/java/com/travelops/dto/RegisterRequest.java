package com.travelops.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only uses the first 72 bytes; a longer password would silently lose its tail.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String name) {
}
