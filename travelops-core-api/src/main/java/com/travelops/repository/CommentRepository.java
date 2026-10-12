package com.travelops.repository;

import com.travelops.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Same N+1 guard as PostRepository: author_name is rendered for every comment on the page.
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByPostId(Long postId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "author")
    Optional<Comment> findById(Long id);
}
