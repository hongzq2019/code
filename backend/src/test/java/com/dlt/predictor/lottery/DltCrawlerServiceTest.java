package com.dlt.predictor.lottery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dlt.predictor.prediction.DrawVectorSyncService;
import com.dlt.predictor.prediction.VectorSyncResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class DltCrawlerServiceTest {

    @Test
    void usesConfiguredPageCountByDefaultAndAllowsManualOverride() {
        SportteryDltClient client = mock(SportteryDltClient.class);
        DrawStore drawStore = mock(DrawStore.class);
        DrawVectorSyncService vectorSyncService = mock(DrawVectorSyncService.class);
        CrawlerJobLogRepository jobLogRepository = mock(CrawlerJobLogRepository.class);
        SportteryDltProperties properties = new SportteryDltProperties("https://example.test", 30, 30);
        when(client.fetchRecentDraws(30)).thenReturn(List.of());
        when(client.fetchRecentDraws(12)).thenReturn(List.of());
        when(vectorSyncService.syncPendingAndFailed()).thenReturn(new VectorSyncResult(0, 0, 0));
        DltCrawlerService service = new DltCrawlerService(
                client, drawStore, vectorSyncService, jobLogRepository, properties);

        DltCrawlerResult defaultResult = service.importRecentDraws();
        DltCrawlerResult overrideResult = service.importRecentDraws(12);

        assertThat(defaultResult.result()).isEqualTo("SUCCESS");
        assertThat(overrideResult.result()).isEqualTo("SUCCESS");
        verify(client).fetchRecentDraws(30);
        verify(client).fetchRecentDraws(12);
    }
}
