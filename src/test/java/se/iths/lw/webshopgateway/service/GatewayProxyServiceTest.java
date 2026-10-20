package se.iths.lw.webshopgateway.service;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.iths.lw.webshopgateway.config.HttpClientConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// Starts a tiny fake service on a free port and checks what the proxy sends to it
// and what it sends back. No real auth, product or order service is needed.
class GatewayProxyServiceTest {

    private HttpServer fakeService;
    private String fakeServiceUrl;
    private GatewayProxyService gatewayProxyService;

    // What the fake service received in the last request
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    // ✅ Added: the query string, for example "quantity=2" (null when there is none)
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @BeforeEach
    void startFakeService() throws IOException {
        fakeService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeService.createContext("/", this::handleRequest);
        fakeService.start();

        fakeServiceUrl = "http://127.0.0.1:" + fakeService.getAddress().getPort();
        gatewayProxyService = new GatewayProxyService(
                fakeServiceUrl,
                fakeServiceUrl,
                fakeServiceUrl,
                false,
                "Lax",
                new HttpClientConfig().httpClient()
        );
    }

    @AfterEach
    void stopFakeService() {
        fakeService.stop(0);
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();

        receivedMethod.set(exchange.getRequestMethod());
        receivedPath.set(path);
        receivedQuery.set(exchange.getRequestURI().getRawQuery());
        receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        receivedBody.set(requestBody);

        if (path.equals("/auth/login")) {
            exchange.getResponseHeaders().add("Set-Cookie", "accessToken=abc; Path=/; HttpOnly");
            respond(exchange, 200, "{\"accessToken\":\"abc\"}");
        } else if (path.equals("/products/404")) {
            respond(exchange, 404, "{\"message\":\"Not found\"}");
        } else if (exchange.getRequestMethod().equals("DELETE")) {
            respond(exchange, 204, "");
        } else {
            respond(exchange, 200, "{\"ok\":true}");
        }
    }

    private void respond(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);

        if (responseBytes.length == 0) {
            exchange.sendResponseHeaders(statusCode, -1);
        } else {
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream responseStream = exchange.getResponseBody()) {
                responseStream.write(responseBytes);
            }
        }
        exchange.close();
    }

    @Test
    void forwardsAuthorizationHeaderAndKeepsStatusBodyAndContentType() {
        ResponseEntity<String> response = gatewayProxyService.currentUser("Bearer my-jwt");

        assertEquals("GET", receivedMethod.get());
        assertEquals("/appusers/me", receivedPath.get());
        assertEquals("Bearer my-jwt", receivedAuthorization.get());
        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"ok\":true}", response.getBody());
        assertEquals("application/json", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void sendsNoAuthorizationHeaderWhenThereIsNoToken() {
        gatewayProxyService.allProducts(null);

        assertEquals("/products/all", receivedPath.get());
        assertNull(receivedAuthorization.get());
    }

    @Test
    void loginForwardsBodyAndContentTypeAndReturnsTheCookie() {
        String loginBody = "{\"username\":\"anna\",\"password\":\"secret\"}";

        ResponseEntity<String> response = gatewayProxyService.login(loginBody, "application/json");

        assertEquals("POST", receivedMethod.get());
        assertEquals("/auth/login", receivedPath.get());
        assertEquals(loginBody, receivedBody.get());
        assertEquals("application/json", receivedContentType.get());
        assertNull(receivedAuthorization.get());
        assertTrue(response.getHeaders().get(HttpHeaders.SET_COOKIE).contains("accessToken=abc; Path=/; HttpOnly"));
    }

    @Test
    void logoutPostsToTheAuthServerWithoutAuthorization() {
        gatewayProxyService.logout();

        assertEquals("POST", receivedMethod.get());
        assertEquals("/auth/logout", receivedPath.get());
        assertNull(receivedAuthorization.get());
    }

    @Test
    void createOrderForwardsBodyTokenAndContentType() {
        String orderBody = "{\"items\":[]}";

        gatewayProxyService.createOrder(orderBody, "Bearer my-jwt", "application/json");

        assertEquals("POST", receivedMethod.get());
        assertEquals("/order", receivedPath.get());
        assertEquals(orderBody, receivedBody.get());
        assertEquals("Bearer my-jwt", receivedAuthorization.get());
        assertEquals("application/json", receivedContentType.get());
    }

    @Test
    void keepsErrorStatusCodes() {
        ResponseEntity<String> response = gatewayProxyService.productById(404L, null);

        assertEquals("/products/404", receivedPath.get());
        assertEquals(404, response.getStatusCode().value());
        assertEquals("{\"message\":\"Not found\"}", response.getBody());
    }

    @Test
    void deleteProductUsesTheDeleteMethod() {
        ResponseEntity<String> response = gatewayProxyService.deleteProduct(7L, "Bearer admin-jwt");

        assertEquals("DELETE", receivedMethod.get());
        assertEquals("/products/7", receivedPath.get());
        assertEquals("Bearer admin-jwt", receivedAuthorization.get());
        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void worksWhenTheServiceUrlEndsWithASlash() {
        GatewayProxyService proxyServiceWithSlash = new GatewayProxyService(
                fakeServiceUrl + "/",
                fakeServiceUrl + "/",
                fakeServiceUrl + "/",
                false,
                "Lax",
                new HttpClientConfig().httpClient()
        );

        proxyServiceWithSlash.allCategories(null);

        assertEquals("/categories/all", receivedPath.get());
    }

    @Test
    void returnsBadGatewayWhenTheServiceCannotBeReached() throws IOException {
        // Find a free port and close it again, so nothing is listening there
        int closedPort;
        try (ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            closedPort = serverSocket.getLocalPort();
        }
        String closedUrl = "http://127.0.0.1:" + closedPort;
        GatewayProxyService proxyServiceWithClosedPort = new GatewayProxyService(
                closedUrl,
                closedUrl,
                closedUrl,
                false,
                "Lax",
                new HttpClientConfig().httpClient()
        );

        ResponseEntity<String> response = proxyServiceWithClosedPort.allProducts(null);

        assertEquals(502, response.getStatusCode().value());
        assertEquals("Could not reach the downstream service", response.getBody());
    }

    // ===================== ✅ Added: tests for the new routes =====================

    @Test
    void reduceStockSendsAPatchWithTheQueryStringAndNoBody() {
        ResponseEntity<String> response = gatewayProxyService.reduceStock("abc-123", "quantity=2", "Bearer my-jwt");

        assertEquals("PATCH", receivedMethod.get());
        assertEquals("/products/abc-123/stock/reduce", receivedPath.get());
        assertEquals("quantity=2", receivedQuery.get());
        assertEquals("Bearer my-jwt", receivedAuthorization.get());
        assertEquals("", receivedBody.get());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void increaseStockUsesTheIncreasePath() {
        gatewayProxyService.increaseStock("abc-123", "quantity=5", "Bearer my-jwt");

        assertEquals("PATCH", receivedMethod.get());
        assertEquals("/products/abc-123/stock/increase", receivedPath.get());
        assertEquals("quantity=5", receivedQuery.get());
    }

    @Test
    void updateBasicInfoKeepsEveryQueryParameter() {
        gatewayProxyService.updateBasicInfo("abc-123", "name=Mug&description=Blue%20mug&price=9.90", "Bearer admin-jwt");

        assertEquals("PATCH", receivedMethod.get());
        assertEquals("/products/abc-123/basic-info", receivedPath.get());
        assertEquals("name=Mug&description=Blue%20mug&price=9.90", receivedQuery.get());
    }

    @Test
    void updateImageAndUpdateCategoryUseTheirOwnPaths() {
        gatewayProxyService.updateImage("abc-123", "imageUrl=https://example.com/mug.png", "Bearer admin-jwt");
        assertEquals("/products/abc-123/image", receivedPath.get());
        assertEquals("imageUrl=https://example.com/mug.png", receivedQuery.get());

        gatewayProxyService.updateCategory("abc-123", "categoryId=3", "Bearer admin-jwt");
        assertEquals("/products/abc-123/category", receivedPath.get());
        assertEquals("categoryId=3", receivedQuery.get());
    }

    @Test
    void patchWithoutQueryStringHasNoQuestionMark() {
        gatewayProxyService.reduceStock("abc-123", null, null);

        assertEquals("/products/abc-123/stock/reduce", receivedPath.get());
        assertNull(receivedQuery.get());
    }

    @Test
    void allProductsPagedForwardsPageSizeAndSort() {
        gatewayProxyService.allProductsPaged("page=0&size=5&sort=name,asc", null);

        assertEquals("GET", receivedMethod.get());
        assertEquals("/products/all-paged", receivedPath.get());
        assertEquals("page=0&size=5&sort=name,asc", receivedQuery.get());
    }

    @Test
    void productByUuidAndProductsByCategoryUseTheRightPaths() {
        gatewayProxyService.productByUuid("abc-123", null);
        assertEquals("/products/uuid/abc-123", receivedPath.get());

        gatewayProxyService.productsByCategory(4L, null);
        assertEquals("/products/category/4", receivedPath.get());
    }

    @Test
    void aUuidWithSpecialCharactersCannotAddAQueryString() {
        // Spring has already decoded the uuid, so "a?b=c" arrives here as plain text
        gatewayProxyService.productByUuid("a?b=c", null);

        assertEquals("/products/uuid/a?b=c", receivedPath.get());
        assertNull(receivedQuery.get());
    }

    @Test
    void createCategoryForwardsBodyTokenAndContentType() {
        String categoryBody = "{\"name\":\"Books\"}";

        gatewayProxyService.createCategory(categoryBody, "Bearer admin-jwt", "application/json");

        assertEquals("POST", receivedMethod.get());
        assertEquals("/categories/new", receivedPath.get());
        assertEquals(categoryBody, receivedBody.get());
        assertEquals("Bearer admin-jwt", receivedAuthorization.get());
        assertEquals("application/json", receivedContentType.get());
    }

    @Test
    void allUsersAndUserByIdGoToTheAuthServer() {
        gatewayProxyService.allUsers("Bearer my-jwt");
        assertEquals("GET", receivedMethod.get());
        assertEquals("/appusers", receivedPath.get());

        gatewayProxyService.userById(9L, "Bearer my-jwt");
        assertEquals("/appusers/9", receivedPath.get());
    }
}
