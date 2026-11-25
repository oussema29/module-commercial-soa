package com.gfg.microservices.repository;

import com.gfg.microservices.model.ExchangeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExchangeRepository extends JpaRepository<ExchangeValue, Long> {

    // Requête générée automatiquement par Spring Data à partir du nom de la méthode
    Optional<ExchangeValue> findByFromCurrencyAndToCurrency(String fromCurrency, String toCurrency);
}
