package com.dlt.predictor.admin;

import com.dlt.predictor.api.ApiResponse;
import com.dlt.predictor.auth.AuthService;
import com.dlt.predictor.auth.PendingUserPageResponse;
import com.dlt.predictor.lottery.DltCrawlerResult;
import com.dlt.predictor.lottery.DltCrawlerService;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.lottery.DrawUpsertResult;
import com.dlt.predictor.prediction.DrawVectorSyncService;
import com.dlt.predictor.prediction.VectorSyncResult;
import com.dlt.predictor.prediction.model.ModelTrainingCoordinator;
import com.dlt.predictor.prediction.model.TrainingRunResult;
import com.dlt.predictor.prediction.model.TrainingRunStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * 管理端接口：处理用户审核、官方开奖同步、人工导入、向量补偿及模型重训。
 *
 */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminController {

    private final AuthService authService;
    private final DrawStore drawStore;
    private final DrawVectorSyncService drawVectorSyncService;
    private final DltCrawlerService dltCrawlerService;
    private final ModelTrainingCoordinator modelTrainingCoordinator;

    AdminController(
            AuthService authService,
            DrawStore drawStore,
            DrawVectorSyncService drawVectorSyncService,
            DltCrawlerService dltCrawlerService,
            ModelTrainingCoordinator modelTrainingCoordinator) {
        this.authService = authService;
        this.drawStore = drawStore;
        this.drawVectorSyncService = drawVectorSyncService;
        this.dltCrawlerService = dltCrawlerService;
        this.modelTrainingCoordinator = modelTrainingCoordinator;
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
     * 立即执行一次官方采集；未指定页数时与定时任务使用相同的默认配置。
     *
     */
    @PostMapping("/dlt/sync")
    ApiResponse<SyncResponse> sync(
            @RequestParam(required = false) @Min(1) @Max(100) Integer pageCount) {
        DltCrawlerResult result = pageCount == null
                ? dltCrawlerService.importRecentDraws()
                : dltCrawlerService.importRecentDraws(pageCount);
        return ApiResponse.ok(new SyncResponse(
                result.result(), result.fetchedCount(), result.insertedCount(), result.message()));
    }

    /**
     *
     * 供管理员补录或修正开奖数据，向量同步后按需异步重训模型。
     *
     */
    @PostMapping("/dlt/import")
    ApiResponse<Draw> importDraw(@Valid @RequestBody ImportDrawRequest request) {
        DrawUpsertResult result = drawStore.upsertWithResult(
                request.issueNo(),
                request.drawDate(),
                request.frontNumbers(),
                request.backNumbers());
        drawVectorSyncService.syncPendingAndFailed();
        if (result.vectorRefreshRequired()) {
            modelTrainingCoordinator.requestRetrain();
        }
        return ApiResponse.ok(result.draw());
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

    /**
     *
     * 立即执行一次完整训练，并仅在整批满足条件时切换生效模型。
     *
     */
    @PostMapping("/dlt/model/retrain")
    ApiResponse<ModelTrainingResponse> retrainModel() {
        TrainingRunResult result = modelTrainingCoordinator.retrainNow();
        if (result.status() == TrainingRunStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, result.message());
        }
        return ApiResponse.ok(ModelTrainingResponse.from(result));
    }
}
