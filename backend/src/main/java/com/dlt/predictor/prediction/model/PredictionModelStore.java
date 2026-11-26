package com.dlt.predictor.prediction.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * 保存模型版本，并以单个事务切换完整的五模型生效批次。
 *
 */
@Service
public class PredictionModelStore {

    private final PredictionModelRepository repository;

    PredictionModelStore(PredictionModelRepository repository) {
        this.repository = repository;
    }

    public Optional<List<ModelSnapshot>> activeBatch() {
        List<ModelSnapshot> snapshots = repository
                .findByStatusOrderByModelTypeAsc(PredictionModelStatus.ACTIVE)
                .stream()
                .map(PredictionModelEntity::toSnapshot)
                .toList();
        return isCompleteConsistentBatch(snapshots) ? Optional.of(snapshots) : Optional.empty();
    }

    @Transactional
    public ModelSnapshot saveInactive(ModelSnapshot snapshot) {
        ModelSnapshot inactive = snapshot.withStatus(PredictionModelStatus.INACTIVE);
        PredictionModelEntity saved = repository.save(PredictionModelEntity.from(inactive));
        return saved == null ? inactive : saved.toSnapshot();
    }

    @Transactional
    public List<ModelSnapshot> activateBatch(List<ModelSnapshot> snapshots) {
        validateCompleteBatch(snapshots);
        List<PredictionModelEntity> entities = snapshots.stream()
                .map(snapshot -> snapshot.withStatus(PredictionModelStatus.INACTIVE))
                .map(PredictionModelEntity::from)
                .toList();
        repository.saveAll(entities);
        repository.deactivateAllActive();
        entities.forEach(PredictionModelEntity::activate);
        repository.saveAll(entities);
        return entities.stream().map(PredictionModelEntity::toSnapshot).toList();
    }

    private static void validateCompleteBatch(List<ModelSnapshot> snapshots) {
        if (!isCompleteConsistentBatch(snapshots)) {
            throw new IllegalArgumentException("必须提供同一批次、同一训练水位的五种模型");
        }
    }

    private static boolean isCompleteConsistentBatch(List<ModelSnapshot> snapshots) {
        if (snapshots == null || snapshots.size() != PredictionModelType.values().length) {
            return false;
        }
        EnumSet<PredictionModelType> types = EnumSet.noneOf(PredictionModelType.class);
        snapshots.forEach(snapshot -> types.add(snapshot.type()));
        ModelSnapshot first = snapshots.getFirst();
        return types.size() == PredictionModelType.values().length
                && snapshots.stream().allMatch(snapshot -> snapshot.batchVersion().equals(first.batchVersion()))
                && snapshots.stream().allMatch(snapshot -> snapshot.trainedThroughDrawId() == first.trainedThroughDrawId())
                && snapshots.stream().allMatch(snapshot -> snapshot.trainedThroughIssueNo().equals(first.trainedThroughIssueNo()))
                && snapshots.stream().allMatch(snapshot -> snapshot.trainedThroughDate().equals(first.trainedThroughDate()));
    }
}
