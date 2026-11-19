package com.example.campaign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une seule ligne par campagne, créée avec elle. On ne stocke que ce qu'on ne peut pas retrouver :
 * le CPC, le ROI et le nombre de clients intéressés sont calculés à chaque lecture.
 */
@Entity
public class PerformanceCampagne {

    @Id
    private UUID id = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campagne_id", unique = true)
    private Campagne campagne;

    // Vide tant que le budget prévu n'est pas défini (0 est une valeur valide)
    @Column(precision = 15, scale = 3)
    private BigDecimal montantPrevu;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal montantDepense = BigDecimal.ZERO;

    private long impressions;
    private long clics;
    private long prospectsGeneres;
    private long devisAcceptes;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal chiffreAffairesAttribue = BigDecimal.ZERO;

    public UUID getId() { return id; }

    public Campagne getCampagne() { return campagne; }
    public void setCampagne(Campagne campagne) { this.campagne = campagne; }

    public BigDecimal getMontantPrevu() { return montantPrevu; }
    public void setMontantPrevu(BigDecimal montantPrevu) { this.montantPrevu = montantPrevu; }

    public BigDecimal getMontantDepense() { return montantDepense; }
    public void setMontantDepense(BigDecimal montantDepense) { this.montantDepense = montantDepense; }

    public long getImpressions() { return impressions; }
    public void setImpressions(long impressions) { this.impressions = impressions; }

    public long getClics() { return clics; }
    public void setClics(long clics) { this.clics = clics; }

    public long getProspectsGeneres() { return prospectsGeneres; }
    public void setProspectsGeneres(long prospectsGeneres) { this.prospectsGeneres = prospectsGeneres; }

    public long getDevisAcceptes() { return devisAcceptes; }
    public void setDevisAcceptes(long devisAcceptes) { this.devisAcceptes = devisAcceptes; }

    public BigDecimal getChiffreAffairesAttribue() { return chiffreAffairesAttribue; }
    public void setChiffreAffairesAttribue(BigDecimal chiffreAffairesAttribue) { this.chiffreAffairesAttribue = chiffreAffairesAttribue; }
}
