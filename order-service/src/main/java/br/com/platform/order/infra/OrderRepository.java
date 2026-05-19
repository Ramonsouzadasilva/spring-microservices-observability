package br.com.platform.order.infra;
import br.com.platform.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface OrderRepository extends JpaRepository<Order, UUID> {}
