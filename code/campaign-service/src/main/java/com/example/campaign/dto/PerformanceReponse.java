package com.example.campaign.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** cpc, roi et clientsInteresses sont CALCULÉS à chaque lecture : on ne les stocke jamais. */
public record PerformanceReponse(UUID campagneId, BigDecimal montantPrevu, BigDecimal montantDepense, long impressions,
                                 long clics, long prospectsGeneres, long clientsInteresses, long devisAcceptes,
                                 BigDecimal chiffreAffairesAttribue, BigDecimal cpc, BigDecimal roi) {
}
