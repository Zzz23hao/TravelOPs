package com.travelops;

import com.travelops.dto.PostCreateRequest;
import com.travelops.dto.PostResponse;
import com.travelops.dto.PostUpdateRequest;
import com.travelops.entity.Post;
import com.travelops.exception.ApiException;
import com.travelops.repository.PostRepository;
import com.travelops.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository);
    }

    @Test
    void createStripsWhitespaceAndSaves() {
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        PostResponse response = postService.create(
                new PostCreateRequest("  Three Days in Vancouver ", "Day 1: Stanley Park", " Vancouver ", "Canada"));

        assertThat(response.title()).isEqualTo("Three Days in Vancouver");
        assertThat(response.destinationCity()).isEqualTo("Vancouver");
    }

    @Test
    void getMissingPostThrowsNotFound() {
        when(postRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.get(42L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.getCode()).isEqualTo("POST_NOT_FOUND");
                });
    }

    @Test
    void updateChangesOnlyProvidedFields() {
        Post existing = new Post("Old title", "Old content", "Vancouver", "Canada");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.saveAndFlush(existing)).thenReturn(existing);

        PostResponse response = postService.update(1L, new PostUpdateRequest("New title", null, null, null));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.content()).isEqualTo("Old content");
        assertThat(response.destinationCity()).isEqualTo("Vancouver");
    }

    @Test
    void deleteMissingPostThrowsNotFoundAndDeletesNothing() {
        when(postRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(7L)).isInstanceOf(ApiException.class);
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPassesPageSizeAndWhitelistedSortToRepository() {
        Page<Post> empty = new PageImpl<>(List.of(), PageRequest.of(2, 5), 0);
        when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

        postService.list("vancouver", null, 2, 5, "createdAt_asc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listRejectsUnknownSort() {
        assertThatThrownBy(() -> postService.list(null, null, 0, 20, "password_desc"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("sort must be one of");
    }
}
