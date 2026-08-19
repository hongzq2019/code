package com.dlt.predictor.lottery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * 大乐透号码值对象：前区 5 个（1-35），后区 2 个（1-12）。
 *
 * <p>工厂方法会完成范围、数量、重复值校验并排序，调用方无需重复处理。</p>
 *
 */
public record DltNumbers(List<Integer> front, List<Integer> back) {

    private static final int FRONT_COUNT = 5;
    private static final int FRONT_MAX_NUMBER = 35;
    private static final int BACK_COUNT = 2;
    private static final int BACK_MAX_NUMBER = 12;

    public DltNumbers {
        front = List.copyOf(front);
        back = List.copyOf(back);
    }

    public static DltNumbers of(List<Integer> front, List<Integer> back) {
        return new DltNumbers(
                normalize(front, FRONT_COUNT, FRONT_MAX_NUMBER, "前区"),
                normalize(back, BACK_COUNT, BACK_MAX_NUMBER, "后区"));
    }

    public String frontText() {
        // 使用两位数的稳定格式保存/展示，例如 01,06,12,20,35。
        return formatNumbers(front);
    }

    public String backText() {
        return formatNumbers(back);
    }

    private static List<Integer> normalize(
            List<Integer> numbers,
            int expectedCount,
            int maximum,
            String zone) {
        // 所有完整性规则集中在领域对象，阻止非法数据进入 PostgreSQL 和 Milvus。
        if (numbers == null
                || numbers.size() != expectedCount
                || containsOutOfRangeNumber(numbers, maximum)
                || containsDuplicates(numbers, expectedCount)) {
            throw new IllegalArgumentException(zone + "号码不合法");
        }
        return numbers.stream().sorted().toList();
    }

    private static boolean containsOutOfRangeNumber(List<Integer> numbers, int maximum) {
        return numbers.stream().anyMatch(number -> number == null || number < 1 || number > maximum);
    }

    private static boolean containsDuplicates(List<Integer> numbers, int expectedCount) {
        Set<Integer> distinctNumbers = new HashSet<>(numbers);
        return distinctNumbers.size() != expectedCount;
    }

    private static String formatNumbers(List<Integer> numbers) {
        return numbers.stream()
                .map(number -> "%02d".formatted(number))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
