package com.dlt.predictor.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dlt.predictor.auth.AuthService;
import com.dlt.predictor.lottery.DltCrawlerResult;
import com.dlt.predictor.lottery.DltCrawlerService;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.prediction.DrawVectorSyncService;
import com.dlt.predictor.prediction.model.ModelTrainingCoordinator;
import com.dlt.predictor.prediction.model.TrainingRunResult;
import com.dlt.predictor.prediction.model.TrainingRunStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AdminControllerLoggingTest {

    @Test
    void logsManualDrawSyncStartAndResult() {
        DltCrawlerService crawlerService = mock(DltCrawlerService.class);
        when(crawlerService.importRecentDraws(30))
                .thenReturn(new DltCrawlerResult("SUCCESS", 900, 12, "同步完成"));
        AdminController controller = controller(crawlerService, mock(ModelTrainingCoordinator.class));

        List<ILoggingEvent> events = captureLogs(() -> controller.sync(30));

        assertThat(events).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("手动同步开奖开始", "pageCount=30");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .contains("手动同步开奖完成", "status=SUCCESS", "fetched=900", "changed=12");
        });
    }

    @Test
    void logsManualModelRetrainingStartAndResult() {
        ModelTrainingCoordinator coordinator = mock(ModelTrainingCoordinator.class);
        when(coordinator.retrainNow()).thenReturn(new TrainingRunResult(
                TrainingRunStatus.SUCCESS, "batch-1", "训练完成", List.of()));
        AdminController controller = controller(mock(DltCrawlerService.class), coordinator);

        List<ILoggingEvent> events = captureLogs(controller::retrainModel);

        assertThat(events).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("手动重训模型开始");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .contains("手动重训模型完成", "status=SUCCESS", "batchVersion=batch-1");
        });
    }

    private static AdminController controller(
            DltCrawlerService crawlerService,
            ModelTrainingCoordinator coordinator) {
        return new AdminController(
                mock(AuthService.class),
                mock(DrawStore.class),
                mock(DrawVectorSyncService.class),
                crawlerService,
                coordinator);
    }

    private static List<ILoggingEvent> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(AdminController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
