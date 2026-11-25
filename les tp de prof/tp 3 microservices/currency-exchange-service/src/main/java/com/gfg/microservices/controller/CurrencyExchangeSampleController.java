package com.gfg.microservices.controller;

import com.gfg.microservices.model.ExchangeValue;
import com.gfg.microservices.service.ExchangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// @RestController = @Controller + @ResponseBody : les retours sont convertis en JSON
@RestController
public class CurrencyExchangeSampleController {

    @Autowired
    private Environment environment;

    @Autowired
    private ExchangeService exchangeService;

    // Exemple : GET /currency-exchange-sample/fromCurrency/USD/toCurrency/INR
    @GetMapping("/currency-exchange-sample/fromCurrency/{fromCurrency}/toCurrency/{toCurrency}")
    public ExchangeValue retrieveExchangeValue(@PathVariable String fromCurrency,
                                               @PathVariable String toCurrency) {
        ExchangeValue exchangeValue = exchangeService.findByFromAndTo(fromCurrency, toCurrency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Taux introuvable pour " + fromCurrency + " -> " + toCurrency));

        // Port de l'instance qui répond (utile quand plusieurs instances tournent)
        exchangeValue.setPort(Integer.parseInt(environment.getProperty("local.server.port")));
        return exchangeValue;
    }

    // Liste de tous les taux
    @GetMapping("/currency-exchange-sample")
    public List<ExchangeValue> retrieveAll() {
        return exchangeService.findAll();
    }

    // Création d'un taux (JSON dans le corps de la requête)
    @PostMapping("/currency-exchange-sample")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeValue create(@RequestBody ExchangeValue exchangeValue) {
        return exchangeService.createExchangeValue(exchangeValue);
    }
}
