package com.dlt.predictor.lottery;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * PostgreSQL 开奖主库访问层，同时维护记录与 Milvus 索引同步状态的一致性。
 *
 */
@Service
public class DrawStore {

    private static final String MANUAL_SOURCE = "manual";

    private final DrawRepository drawRepository;

    DrawStore(DrawRepository drawRepository) {
        this.drawRepository = drawRepository;
    }

    @Transactional
    public Draw upsert(
            String issueNo,
            LocalDate drawDate,
            List<Integer> frontNumbers,
            List<Integer> backNumbers) {
        return upsertWithResult(issueNo, drawDate, frontNumbers, backNumbers).draw();
    }

    @Transactional
    public DrawUpsertResult upsertWithResult(
            String issueNo,
            LocalDate drawDate,
            List<Integer> frontNumbers,
            List<Integer> backNumbers) {
        return upsertInternal(issueNo, drawDate, frontNumbers, backNumbers, MANUAL_SOURCE, null);
    }

    @Transactional
    DrawUpsertResult upsertOfficial(OfficialDraw officialDraw) {
        return upsertInternal(
                officialDraw.issueNo(),
                officialDraw.drawDate(),
                officialDraw.frontNumbers(),
                officialDraw.backNumbers(),
                officialDraw.sourceUrl(),
                officialDraw.sourcePayload());
    }

    private DrawUpsertResult upsertInternal(
            String issueNo,
            LocalDate drawDate,
            List<Integer> frontNumbers,
            List<Integer> backNumbers,
            String sourceUrl,
            String sourcePayload) {
        DltNumbers numbers = DltNumbers.of(frontNumbers, backNumbers);
        boolean[] vectorRefreshRequired = {false};
        DrawEntity entity = drawRepository.findByIssueNo(issueNo)
                .map(existing -> {
                    vectorRefreshRequired[0] = existing.update(drawDate, numbers, sourceUrl, sourcePayload);
                    return existing;
                })
                .orElseGet(() -> {
                    vectorRefreshRequired[0] = true;
                    return new DrawEntity(issueNo, drawDate, numbers, sourceUrl, sourcePayload);
                });
        return new DrawUpsertResult(drawRepository.save(entity).toDraw(), vectorRefreshRequired[0]);
    }

    public List<Draw> all() {
        return drawRepository.findAllByOrderByDrawDateDesc().stream()
                .map(DrawEntity::toDraw)
                .toList();
    }

    public List<Draw> allChronological() {
        return drawRepository.findAllByOrderByDrawDateAscIdAsc().stream()
                .map(DrawEntity::toDraw)
                .toList();
    }

    public List<Draw> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return drawRepository.findByDrawDateBetweenOrderByDrawDateAsc(startDate, endDate).stream()
                .map(DrawEntity::toDraw)
                .toList();
    }

    public Optional<Draw> find(String issueNo) {
        return drawRepository.findByIssueNo(issueNo).map(DrawEntity::toDraw);
    }

    public List<Draw> findByIdsInOrder(Collection<Long> ids) {
        Map<Long, Draw> drawsById = drawRepository.findByIdIn(ids).stream()
                .map(DrawEntity::toDraw)
                .collect(java.util.stream.Collectors.toMap(Draw::id, Function.identity()));
        return ids.stream().map(drawsById::get).filter(java.util.Objects::nonNull).toList();
    }

    public List<Draw> pendingOrFailedForVectorSync() {
        return drawRepository.findByVectorStatusInOrderByDrawDateAsc(
                        List.of(VectorStatus.PENDING, VectorStatus.FAILED)).stream()
                .map(DrawEntity::toDraw)
                .toList();
    }

    @Transactional
    public void markVectorReady(long drawId) {
        findEntity(drawId).markVectorReady();
    }

    @Transactional
    public void markVectorFailed(long drawId, String error) {
        findEntity(drawId).markVectorFailed(error);
    }

    private DrawEntity findEntity(long drawId) {
        return drawRepository.findById(drawId)
                .orElseThrow(() -> new IllegalArgumentException("开奖数据不存在: " + drawId));
    }
}
