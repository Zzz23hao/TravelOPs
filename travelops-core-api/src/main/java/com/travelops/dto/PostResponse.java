package com.travelops.dto;

import com.travelops.entity.Post;

import java.time.Instant;

public record PostResponse(
        Long id,
        String title,
        String content,
        String destinationCity,
        String destinationCountry,
        Long authorId,
        String authorName,
        Instant createdAt,
        Instant updatedAt) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getDestinationCity(),
                post.getDestinationCountry(),
                post.getAuthor().getId(),
                post.getAuthor().getName(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
