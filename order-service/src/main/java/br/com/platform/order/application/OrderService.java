package br.com.platform.order.application;

import br.com.platform.order.application.dto.CreateOrderRequest;
import br.com.platform.order.application.dto.OrderResponse;
import br.com.platform.order.application.messaging.OrderCreatedEvent;
import br.com.platform.order.domain.Order;
import br.com.platform.order.domain.OrderStatus;
import br.com.platform.order.infra.OrderRepository;
import br.com.platform.order.infra.RabbitMqPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final RabbitMqPublisher rabbitMqPublisher;

    @Transactional
    public OrderResponse createOrder(String userId, String idempotencyKey, CreateOrderRequest request) {
        idempotencyService.checkAndLock(idempotencyKey, userId);

        Order order = new Order();
        order.setUserId(UUID.fromString(userId));
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(request.total());

        orderRepository.save(order);

        rabbitMqPublisher.publishOrderCreated(new OrderCreatedEvent(order.getId(), order.getUserId()));

        OrderResponse response = new OrderResponse(order.getId().toString(), order.getStatus().name(), order.getTotalAmount());
        idempotencyService.markSuccess(idempotencyKey, userId, response);

        return response;
    }

    public OrderResponse getOrder(String id) {
        Order order = orderRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return new OrderResponse(order.getId().toString(), order.getStatus().name(), order.getTotalAmount());
    }
}
