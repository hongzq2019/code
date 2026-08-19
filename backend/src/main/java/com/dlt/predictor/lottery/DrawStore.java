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
        // 人工录入没有原始响应载荷，使用独立来源标识以便后续追溯。
        return upsertInternal(issueNo, drawDate, frontNumbers, backNumbers, MANUAL_SOURCE, null).draw();
    }

    @Transactional
    DrawUpsertResult upsertOfficial(OfficialDraw officialDraw) {
        // 官方数据保留来源 URL 与原始 JSON，方便诊断数据争议或接口变更。
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
        // 在 lambda 内携带更新结果；新建记录或号码/日期变动时才需要重算向量。
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

    /**
     * 按开奖日期范围升序查询记录，为历史点位分析提供稳定的时间序列。
     */
    public List<Draw> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return drawRepository.findByDrawDateBetweenOrderByDrawDateAsc(startDate, endDate).stream()
                .map(DrawEntity::toDraw)
                .toList();
    }

    public Optional<Draw> find(String issueNo) {
        return drawRepository.findByIssueNo(issueNo).map(DrawEntity::toDraw);
    }

    public List<Draw> findByIdsInOrder(Collection<Long> ids) {
        // JPA 的 IN 查询不保证返回顺序，按 Milvus 命中顺序重组结果。
        Map<Long, Draw> drawsById = drawRepository.findByIdIn(ids).stream()
                .map(DrawEntity::toDraw)
                .collect(java.util.stream.Collectors.toMap(Draw::id, Function.identity()));
        return ids.stream().map(drawsById::get).filter(java.util.Objects::nonNull).toList();
    }

    public List<Draw> pendingOrFailedForVectorSync() {
        // FAILED 同样进入补偿队列，使短暂的 Milvus 故障可在后续任务中自动恢复。
        return drawRepository.findByVectorStatusInOrderByDrawDateAsc(List.of(VectorStatus.PENDING, VectorStatus.FAILED)).stream()
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
