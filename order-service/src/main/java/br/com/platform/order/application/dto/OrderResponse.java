package br.com.platform.order.application.dto;
import java.math.BigDecimal;
public record OrderResponse(String id, String status, BigDecimal totalAmount) {}
