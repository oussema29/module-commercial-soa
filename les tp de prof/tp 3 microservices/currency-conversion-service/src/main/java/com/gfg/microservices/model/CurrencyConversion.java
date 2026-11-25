package com.gfg.microservices.model;

import java.math.BigDecimal;

// Réponse renvoyée au client par le service de conversion
public class CurrencyConversion {

    private String fromCurrency;
    private String toCurrency;
    private BigDecimal conversionMultiple;
    private BigDecimal quantity;
    private BigDecimal totalCalculatedAmount;
    // Port de l'instance de currency-exchange-service qui a répondu
    private int exchangePort;

    public CurrencyConversion() {
    }

    public CurrencyConversion(String fromCurrency, String toCurrency, BigDecimal conversionMultiple,
                              BigDecimal quantity, BigDecimal totalCalculatedAmount, int exchangePort) {
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.conversionMultiple = conversionMultiple;
        this.quantity = quantity;
        this.totalCalculatedAmount = totalCalculatedAmount;
        this.exchangePort = exchangePort;
    }

    public String getFromCurrency() {
        return fromCurrency;
    }

    public void setFromCurrency(String fromCurrency) {
        this.fromCurrency = fromCurrency;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public void setToCurrency(String toCurrency) {
        this.toCurrency = toCurrency;
    }

    public BigDecimal getConversionMultiple() {
        return conversionMultiple;
    }

    public void setConversionMultiple(BigDecimal conversionMultiple) {
        this.conversionMultiple = conversionMultiple;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotalCalculatedAmount() {
        return totalCalculatedAmount;
    }

    public void setTotalCalculatedAmount(BigDecimal totalCalculatedAmount) {
        this.totalCalculatedAmount = totalCalculatedAmount;
    }

    public int getExchangePort() {
        return exchangePort;
    }

    public void setExchangePort(int exchangePort) {
        this.exchangePort = exchangePort;
    }
}
