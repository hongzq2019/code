package com.dlt.predictor.lottery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 * 访问中国体彩网官方接口（gameNo=85），并将 JSON 记录转换为内部开奖模型。
 *
 */
@Component
class SportteryDltClient {

    private static final String DLT_GAME_NO = "85";

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
        // 固定只拉取第一页的近期数据，防止定时任务每次都进行全量历史抓取。
        URI uri = UriComponentsBuilder.fromUriString(properties.sourceUrl())
                .queryParam("gameNo", DLT_GAME_NO)
                .queryParam("provinceId", "0")
                .queryParam("pageSize", properties.pageSize())
                .queryParam("isVerify", "1")
                .queryParam("pageNo", "1")
                .build(true)
                .toUri();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            // 同时校验成功标志和列表节点，避免将错误页或变更后的响应写入主库。
            if (!root.path("success").asBoolean(false) || !root.path("value").path("list").isArray()) {
                throw new IllegalStateException("中国体彩网返回了无效的大乐透响应");
            }
            return java.util.stream.StreamSupport.stream(root.path("value").path("list").spliterator(), false)
                    .map(node -> toOfficialDraw(node, uri.toString()))
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("获取中国体彩网大乐透开奖数据失败", exception);
        }
    }

    private OfficialDraw toOfficialDraw(JsonNode node, String sourceUrl) {
        String issueNo = requiredText(node, "lotteryDrawNum");
        String drawTime = requiredText(node, "lotteryDrawTime");
        List<Integer> numbers = Arrays.stream(requiredText(node, "lotteryDrawResult").trim().split("\\s+"))
                .map(Integer::parseInt)
                .toList();
        // 大乐透规则固定为前区 5 个、后区 2 个号码。
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

    private static String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("中国体彩网响应缺少字段: " + fieldName);
        }
        return value;
    }
}
