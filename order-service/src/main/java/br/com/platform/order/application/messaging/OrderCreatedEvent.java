package br.com.platform.order.application.messaging;
import java.util.UUID;
public record OrderCreatedEvent(UUID orderId, UUID userId) {}
