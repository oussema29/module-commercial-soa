package com.gfg.microservices.controller;

import com.gfg.microservices.model.CurrencyConversion;
import com.gfg.microservices.model.ExchangeValueResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
public class CurrencyConversionController {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${currency-exchange.url}")
    private String exchangeUrl;

    // Exemple : GET /currency-conversion/from/USD/to/INR/quantity/100
    @GetMapping("/currency-conversion/from/{from}/to/{to}/quantity/{quantity}")
    public CurrencyConversion convertCurrency(@PathVariable String from,
                                              @PathVariable String to,
                                              @PathVariable BigDecimal quantity) {
        ExchangeValueResponse exchange;
        try {
            // Appel REST vers currency-exchange-service
            exchange = restTemplate.getForObject(
                    exchangeUrl + "/currency-exchange-sample/fromCurrency/{from}/toCurrency/{to}",
                    ExchangeValueResponse.class, from, to);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Taux introuvable pour " + from + " -> " + to);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "currency-exchange-service est injoignable");
        }

        return new CurrencyConversion(
                exchange.getFromCurrency(),
                exchange.getToCurrency(),
                exchange.getConversionMultiple(),
                quantity,
                quantity.multiply(exchange.getConversionMultiple()),
                exchange.getPort());
    }
}
