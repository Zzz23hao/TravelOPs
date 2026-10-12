package com.travelops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(@NotBlank @Size(max = 2000) String content) {
}
