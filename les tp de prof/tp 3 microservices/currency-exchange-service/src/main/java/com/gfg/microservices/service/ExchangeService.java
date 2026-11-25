package com.gfg.microservices.service;

import com.gfg.microservices.model.ExchangeValue;
import com.gfg.microservices.repository.ExchangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ExchangeService {

    @Autowired
    private ExchangeRepository exchangeRepository;

    // CREATE
    public ExchangeValue createExchangeValue(ExchangeValue ex) {
        return exchangeRepository.save(ex);
    }

    // READ : tous les taux
    public List<ExchangeValue> findAll() {
        return exchangeRepository.findAll();
    }

    // READ : un taux pour une paire de devises
    public Optional<ExchangeValue> findByFromAndTo(String from, String to) {
        return exchangeRepository.findByFromCurrencyAndToCurrency(from.toUpperCase(), to.toUpperCase());
    }
}
