package com.dlt.predictor.admin;

import com.dlt.predictor.api.ApiResponse;
import com.dlt.predictor.auth.AuthService;
import com.dlt.predictor.auth.PendingUserPageResponse;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.lottery.DltCrawlerResult;
import com.dlt.predictor.lottery.DltCrawlerService;
import com.dlt.predictor.prediction.DrawVectorSyncService;
import com.dlt.predictor.prediction.VectorSyncResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 *
 * 管理端接口：处理用户审核、官方开奖同步、人工导入及 Milvus 索引重试。
 *
 */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminController {

    private static final String SUCCESS_RESULT = "SUCCESS";
    private static final String SYNC_MESSAGE = "MVP 使用手动导入与可配置采集适配层";

    private final AuthService authService;
    private final DrawStore drawStore;
    private final DrawVectorSyncService drawVectorSyncService;
    private final DltCrawlerService dltCrawlerService;

    AdminController(
            AuthService authService,
            DrawStore drawStore,
            DrawVectorSyncService drawVectorSyncService,
            DltCrawlerService dltCrawlerService) {
        this.authService = authService;
        this.drawStore = drawStore;
        this.drawVectorSyncService = drawVectorSyncService;
        this.dltCrawlerService = dltCrawlerService;
    }

    /**
     *
     * 分页查询状态为 PENDING 的账户，避免审核列表随注册量增长而一次性加载全部记录。
     *
     */
    @GetMapping("/users")
    ApiResponse<PendingUserPageResponse> pending(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(PendingUserPageResponse.from(authService.pendingUsers(page, size)));
    }

    @PostMapping("/users/{username}/approval")
    ApiResponse<Void> approval(
            @PathVariable String username,
            @RequestBody ApprovalRequest request) {
        authService.approve(username, request.approved());
        return ApiResponse.ok(null);
    }

    /**
     *
     * 立即执行一次官方采集；与定时任务复用相同的数据处理流程。
     *
     */
    @PostMapping("/dlt/sync")
    ApiResponse<SyncResponse> sync() {
        DltCrawlerResult result = dltCrawlerService.importRecentDraws();
        return ApiResponse.ok(new SyncResponse(result.result(), result.insertedCount(), result.message()));
    }

    /**
     *
     * 供管理员补录或修正开奖数据，成功入库后立即补偿待同步向量。
     *
     */
    @PostMapping("/dlt/import")
    ApiResponse<Draw> importDraw(@Valid @RequestBody ImportDrawRequest request) {
        Draw draw = drawStore.upsert(
                request.issueNo(),
                request.drawDate(),
                request.frontNumbers(),
                request.backNumbers());
        drawVectorSyncService.syncPendingAndFailed();
        return ApiResponse.ok(draw);
    }

    /**
     *
     * 仅重试 PENDING/FAILED 的向量，不重新抓取开奖记录。
     *
     */
    @PostMapping("/dlt/vectors/retry")
    ApiResponse<VectorSyncResult> retryVectors() {
        return ApiResponse.ok(drawVectorSyncService.syncPendingAndFailed());
    }
}
