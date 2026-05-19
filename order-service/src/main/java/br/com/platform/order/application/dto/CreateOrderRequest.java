package br.com.platform.order.application.dto;
import java.math.BigDecimal;
public record CreateOrderRequest(BigDecimal total) {}
