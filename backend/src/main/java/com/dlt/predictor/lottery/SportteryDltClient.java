package com.dlt.predictor.lottery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 * 访问中国体彩网官方接口（gameNo=85），并将分页 JSON 记录转换为内部开奖模型。
 *
 */
@Component
class SportteryDltClient {

    private static final String DLT_GAME_NO = "85";
    private static final int MIN_PAGE_COUNT = 1;
    private static final int MAX_PAGE_COUNT = 100;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SportteryDltProperties properties;

    SportteryDltClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            SportteryDltProperties properties) {
        this.restClient = restClientBuilder.defaultHeader("User-Agent", "dlt-predictor/1.0").build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    List<OfficialDraw> fetchRecentDraws() {
        return fetchRecentDraws(properties.pageCount());
    }

    List<OfficialDraw> fetchRecentDraws(int pageCount) {
        validatePageCount(pageCount);
        Map<String, OfficialDraw> drawsByIssue = new LinkedHashMap<>();
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            List<OfficialDraw> page = fetchPage(pageNo);
            if (page.isEmpty()) {
                break;
            }
            page.forEach(draw -> drawsByIssue.putIfAbsent(draw.issueNo(), draw));
        }
        return List.copyOf(drawsByIssue.values());
    }

    private List<OfficialDraw> fetchPage(int pageNo) {
        URI uri = UriComponentsBuilder.fromUriString(properties.sourceUrl())
                .queryParam("gameNo", DLT_GAME_NO)
                .queryParam("provinceId", "0")
                .queryParam("pageSize", properties.pageSize())
                .queryParam("isVerify", "1")
                .queryParam("pageNo", pageNo)
                .build(true)
                .toUri();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false) || !root.path("value").path("list").isArray()) {
                throw new IllegalStateException("中国体彩网返回了无效的大乐透响应");
            }
            return java.util.stream.StreamSupport.stream(root.path("value").path("list").spliterator(), false)
                    .map(node -> toOfficialDraw(node, uri.toString()))
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("获取中国体彩网大乐透开奖数据失败，页码: " + pageNo, exception);
        }
    }

    private OfficialDraw toOfficialDraw(JsonNode node, String sourceUrl) {
        String issueNo = requiredText(node, "lotteryDrawNum");
        String drawTime = requiredText(node, "lotteryDrawTime");
        List<Integer> numbers = Arrays.stream(requiredText(node, "lotteryDrawResult").trim().split("\\s+"))
                .map(Integer::parseInt)
                .toList();
        if (numbers.size() != 7) {
            throw new IllegalArgumentException("期号 " + issueNo + " 的开奖号码数量不是 7 个");
        }
        return new OfficialDraw(
                issueNo,
                LocalDate.parse(drawTime.substring(0, 10)),
                numbers.subList(0, 5),
                numbers.subList(5, 7),
                sourceUrl,
                node.toString());
    }

    private static void validatePageCount(int pageCount) {
        if (pageCount < MIN_PAGE_COUNT || pageCount > MAX_PAGE_COUNT) {
            throw new IllegalArgumentException("采集页数必须在 1 至 100 之间");
        }
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("中国体彩网响应缺少字段: " + fieldName);
        }
        return value;
    }
}
