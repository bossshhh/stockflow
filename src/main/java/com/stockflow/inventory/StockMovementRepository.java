package com.stockflow.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByProductId(long productId, Pageable pageable);

    Page<StockMovement> findByProductIdAndType(long productId, MovementType type, Pageable pageable);
}
