package com.example.campaign.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Table de liaison interne : l'identifiant est un simple entier, jamais exposé dans l'API. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"campagne_id", "canal_id"}))
public class CampagneCanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campagne_id")
    private Campagne campagne;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "canal_id")
    private CanalPublicitaire canal;

    public Long getId() { return id; }

    public Campagne getCampagne() { return campagne; }
    public void setCampagne(Campagne campagne) { this.campagne = campagne; }

    public CanalPublicitaire getCanal() { return canal; }
    public void setCanal(CanalPublicitaire canal) { this.canal = canal; }
}
