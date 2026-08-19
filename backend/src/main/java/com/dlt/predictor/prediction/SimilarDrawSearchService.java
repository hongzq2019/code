package com.dlt.predictor.prediction;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 *
 * 基于 Milvus 向量近邻结果查询 PostgreSQL，生成带完整开奖信息的相似样本。
 *
 */
@Service
class SimilarDrawSearchService {

    private final DrawStore drawStore;
    private final VectorStore vectorStore;

    SimilarDrawSearchService(DrawStore drawStore, VectorStore vectorStore) {
        this.drawStore = drawStore;
        this.vectorStore = vectorStore;
    }

    List<SimilarDraw> findSimilarDraws(DltNumbers input, int topK) {
        // 多查询一条，预留空间排除与输入号码完全相同的历史开奖记录。
        List<VectorSearchHit> hits = vectorStore.search(FeatureEncoder.encode(input), Math.min(topK + 1, 11));
        Map<Long, Draw> drawsById = drawStore.findByIdsInOrder(hits.stream().map(VectorSearchHit::drawId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Draw::id, Function.identity()));
        return hits.stream()
                // Milvus 可因索引滞后返回已不存在的主键，此处安全跳过。
                .map(hit -> toSimilarDraw(drawsById.get(hit.drawId()), hit))
                .filter(java.util.Objects::nonNull)
                .filter(draw -> !draw.numbers().equals(input))
                .limit(topK)
                .toList();
    }

    private SimilarDraw toSimilarDraw(Draw draw, VectorSearchHit hit) {
        if (draw == null) {
            return null;
        }
        return new SimilarDraw(draw.id(), draw.issueNo(), draw.drawDate(), draw.numbers(), hit.similarity());
    }
}
