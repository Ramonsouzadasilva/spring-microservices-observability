package br.com.platform.order.infra;

import br.com.platform.order.application.messaging.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    @RabbitListener(queues = br.com.platform.order.config.RabbitMqConfig.QUEUE_NAME)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received order created event: Order ID = {}, User ID = {}", event.orderId(), event.userId());
        // Lógica assíncrona (ex: envio de email, atualização de estoque)
    }
}
