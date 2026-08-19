package com.dlt.predictor.lottery;

import com.dlt.predictor.api.ApiResponse;
import com.dlt.predictor.lottery.dto.DrawPageResponse;
import com.dlt.predictor.lottery.dto.DrawResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dlt/draws")
public class LotteryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DrawStore drawStore;

    LotteryController(DrawStore drawStore) {
        this.drawStore = drawStore;
    }

    @GetMapping
    public ApiResponse<DrawPageResponse> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String issueNo) {
        List<Draw> matchingDraws = drawStore.all().stream()
                .filter(draw -> issueNo == null || draw.issueNo().contains(issueNo))
                .toList();
        int effectivePageSize = Math.min(size, MAX_PAGE_SIZE);
        int startIndex = Math.min(Math.max(0, (page - 1) * effectivePageSize), matchingDraws.size());
        int endIndex = Math.min(startIndex + effectivePageSize, matchingDraws.size());
        List<DrawResponse> items = matchingDraws.subList(startIndex, endIndex).stream()
                .map(DrawResponse::from)
                .toList();

        return ApiResponse.ok(new DrawPageResponse(items, page, size, matchingDraws.size()));
    }

    @GetMapping("/{issueNo}")
    public ApiResponse<DrawResponse> detail(@PathVariable String issueNo) {
        Draw draw = drawStore.find(issueNo)
                .orElseThrow(() -> new IllegalArgumentException("期号不存在"));
        return ApiResponse.ok(DrawResponse.from(draw));
    }
}
