package com.dlt.predictor.prediction.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PredictionModelRepository extends JpaRepository<PredictionModelEntity, Long> {

    List<PredictionModelEntity> findByStatusOrderByModelTypeAsc(PredictionModelStatus status);

    @Modifying
    @Query(value = "UPDATE prediction_model SET status = 'INACTIVE' WHERE status = 'ACTIVE'", nativeQuery = true)
    int deactivateAllActive();
}
