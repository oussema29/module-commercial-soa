package com.example.campaign.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Réponse paginée du contrat : { contenu, page, taille, total }. */
public record PageResponse<T>(List<T> contenu, int page, int taille, long total) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Page demandée, avec une taille entre 1 et 100. */
    public static Pageable pageable(int page, int taille, Sort tri) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 100), tri);
    }
}
