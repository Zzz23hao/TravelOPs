package com.travelops.dto;

import com.travelops.entity.Comment;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long postId,
        Long authorId,
        String authorName,
        String content,
        Instant createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                // Reading the id of a lazy proxy does not trigger a query; Hibernate already holds it.
                comment.getPost().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getContent(),
                comment.getCreatedAt());
    }
}
