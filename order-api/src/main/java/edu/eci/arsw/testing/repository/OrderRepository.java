package edu.eci.arsw.testing.repository;

import edu.eci.arsw.testing.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de Spring Data JPA para entidades {@link Order}.
 */
public interface OrderRepository extends JpaRepository<Order, String> {
}
