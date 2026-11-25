package com.example.customer.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Réponse paginée du contrat : { contenu, page, taille, total }. */
public record PageResponse<T>(List<T> contenu, int page, int taille, long total) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
