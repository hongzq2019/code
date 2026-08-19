package com.dlt.predictor;
import com.dlt.predictor.lottery.DltNumbers; import com.dlt.predictor.prediction.*; import org.junit.jupiter.api.Test; import java.time.LocalDate; import java.util.List; import static org.junit.jupiter.api.Assertions.*;
/**
 *
 * 验证大乐透领域对象的号码规范化、特征编码与推荐结果。
 *
 */
class CoreDomainTest {
 @Test void validates_sorts_and_encodes_dlt_numbers(){var n=DltNumbers.of(List.of(35,1,20,6,12),List.of(11,3));assertEquals(List.of(1,6,12,20,35),n.front());assertEquals(47,FeatureEncoder.encode(n).length);assertThrows(IllegalArgumentException.class,()->DltNumbers.of(List.of(1,1,2,3,4),List.of(1,2)));}
 @Test void weights_similar_draws_into_reference_numbers(){var d=new SimilarDraw(1L,"2024001",LocalDate.now().minusDays(1),DltNumbers.of(List.of(1,2,3,4,5),List.of(1,2)),.9);var r=new RecommendationEngine().recommend(List.of(d),LocalDate.now());assertEquals(5,r.numbers().front().size());assertTrue(r.disclaimer().contains("娱乐"));}
}
