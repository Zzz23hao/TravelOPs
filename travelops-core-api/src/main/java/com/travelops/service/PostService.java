package com.travelops.service;

import com.travelops.dto.PageResponse;
import com.travelops.dto.PostCreateRequest;
import com.travelops.dto.PostResponse;
import com.travelops.dto.PostUpdateRequest;
import com.travelops.entity.Post;
import com.travelops.exception.ApiException;
import com.travelops.repository.PostRepository;
import com.travelops.repository.PostSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class PostService {

    // Whitelist: sorting on an arbitrary client-supplied property would be a 500 or an unindexed scan.
    private static final Map<String, Sort> SORTS = Map.of(
            "createdAt_desc", Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")),
            "createdAt_asc", Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Transactional
    public PostResponse create(PostCreateRequest request) {
        Post post = new Post(
                request.title().strip(),
                request.content(),
                request.destinationCity().strip(),
                request.destinationCountry().strip());
        return PostResponse.from(postRepository.save(post));
    }

    @Transactional(readOnly = true)
    public PostResponse get(Long id) {
        return PostResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> list(String keyword, String destination, int page, int size, String sort) {
        Sort order = SORTS.get(sort);
        if (order == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                    "sort must be one of " + SORTS.keySet().stream().sorted().toList());
        }

        Specification<Post> spec = Specification.where(null);
        if (destination != null && !destination.isBlank()) {
            spec = spec.and(PostSpecifications.destinationEquals(destination.strip()));
        }
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(PostSpecifications.keywordMatches(keyword.strip()));
        }

        return PageResponse.from(
                postRepository.findAll(spec, PageRequest.of(page, size, order)).map(PostResponse::from));
    }

    @Transactional
    public PostResponse update(Long id, PostUpdateRequest request) {
        Post post = findOrThrow(id);
        if (request.title() != null) {
            post.setTitle(request.title().strip());
        }
        if (request.content() != null) {
            post.setContent(request.content());
        }
        if (request.destinationCity() != null) {
            post.setDestinationCity(request.destinationCity().strip());
        }
        if (request.destinationCountry() != null) {
            post.setDestinationCountry(request.destinationCountry().strip());
        }
        // Flush so updated_at is populated in the response rather than after commit.
        return PostResponse.from(postRepository.saveAndFlush(post));
    }

    @Transactional
    public void delete(Long id) {
        postRepository.delete(findOrThrow(id));
    }

    private Post findOrThrow(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Post " + id + " not found."));
    }
}
