package com.dlt.predictor.lottery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * 开奖主表的 JPA 查询定义，方法名直接表达查询排序和状态条件。
 *
 */
interface DrawRepository extends JpaRepository<DrawEntity, Long> {

    Optional<DrawEntity> findByIssueNo(String issueNo);

    List<DrawEntity> findAllByOrderByDrawDateDesc();

    List<DrawEntity> findAllByOrderByDrawDateAscIdAsc();

    List<DrawEntity> findByDrawDateBetweenOrderByDrawDateAsc(LocalDate startDate, LocalDate endDate);

    List<DrawEntity> findByIdIn(Collection<Long> ids);

    List<DrawEntity> findByVectorStatusInOrderByDrawDateAsc(Collection<VectorStatus> vectorStatuses);
}
