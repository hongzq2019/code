package com.dlt.predictor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.prediction.FeatureEncoder;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 *
 * 验证大乐透领域对象的号码规范化与特征编码。
 *
 */
class CoreDomainTest {

    @Test
    void validatesSortsAndEncodesDltNumbers() {
        DltNumbers numbers = DltNumbers.of(List.of(35, 1, 20, 6, 12), List.of(11, 3));

        assertEquals(List.of(1, 6, 12, 20, 35), numbers.front());
        assertEquals(47, FeatureEncoder.encode(numbers).length);
        assertThrows(IllegalArgumentException.class,
                () -> DltNumbers.of(List.of(1, 1, 2, 3, 4), List.of(1, 2)));
    }
}
