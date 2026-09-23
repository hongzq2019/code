package com.dlt.predictor.lottery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SportteryDltClientTest {

    private HttpServer server;
    private final List<Integer> requestedPages = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/draws", this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void stopsAtEmptyPageAndKeepsFirstDuplicateIssue() {
        SportteryDltClient client = client(5);

        List<OfficialDraw> draws = client.fetchRecentDraws(5);

        assertThat(draws).extracting(OfficialDraw::issueNo)
                .containsExactly("2026003", "2026002", "2026001");
        assertThat(requestedPages).containsExactly(1, 2, 3);
        assertThat(draws.get(1).sourcePayload()).contains("02 07 14 21 32");
    }

    @Test
    void rejectsPageCountOutsideSupportedRange() {
        SportteryDltClient client = client(30);

        assertThatThrownBy(() -> client.fetchRecentDraws(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 至 100");
        assertThatThrownBy(() -> client.fetchRecentDraws(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 至 100");
        assertThat(requestedPages).isEmpty();
    }

    private SportteryDltClient client(int pageCount) {
        String url = "http://localhost:" + server.getAddress().getPort() + "/draws";
        return new SportteryDltClient(
                RestClient.builder(),
                new ObjectMapper(),
                new SportteryDltProperties(url, 30, pageCount));
    }

    private void respond(HttpExchange exchange) throws IOException {
        int pageNo = queryValue(exchange.getRequestURI().getQuery(), "pageNo");
        requestedPages.add(pageNo);
        String body = switch (pageNo) {
            case 1 -> page("""
                    {"lotteryDrawNum":"2026003","lotteryDrawTime":"2026-01-07","lotteryDrawResult":"03 08 13 22 35 04 11"},
                    {"lotteryDrawNum":"2026002","lotteryDrawTime":"2026-01-05","lotteryDrawResult":"02 07 14 21 32 01 09"}
                    """);
            case 2 -> page("""
                    {"lotteryDrawNum":"2026002","lotteryDrawTime":"2026-01-05","lotteryDrawResult":"01 02 03 04 05 06 07"},
                    {"lotteryDrawNum":"2026001","lotteryDrawTime":"2026-01-03","lotteryDrawResult":"01 06 12 20 35 03 11"}
                    """);
            default -> page("");
        };
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String page(String rows) {
        return "{\"success\":true,\"value\":{\"list\":[" + rows + "]}}";
    }

    private static int queryValue(String query, String name) {
        return java.util.Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts[0].equals(name))
                .map(parts -> Integer.parseInt(parts[1]))
                .findFirst()
                .orElseThrow();
    }
}
