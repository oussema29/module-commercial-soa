package com.example.campaign.dto;

import com.example.campaign.entity.PerformanceCampagne;
import java.math.BigDecimal;

public record BudgetReponse(BigDecimal montantPrevu, BigDecimal montantDepense) {

    public static BudgetReponse de(PerformanceCampagne p) {
        return new BudgetReponse(p.getMontantPrevu(), p.getMontantDepense());
    }
}
