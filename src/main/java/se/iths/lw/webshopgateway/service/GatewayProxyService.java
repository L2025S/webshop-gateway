package se.iths.lw.webshopgateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;



@Service
public class GatewayProxyService {

    // Add a limit for how long we wait for a service to answer
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String authServerUrl;
    private final String productServiceUrl;
    private final String orderServiceUrl;
    private final HttpClient httpClient;

    public GatewayProxyService (
            @Value("${app.auth-server-url}") String authServerUrl,
            @Value("${app.product-service-url}") String productServiceUrl,
            @Value("${app.order-service-url}") String orderServiceUrl,
            HttpClient httpClient
    ) {
        this.authServerUrl = removeTrailingSlash(authServerUrl);
        this.productServiceUrl = removeTrailingSlash(productServiceUrl);
        this.orderServiceUrl=removeTrailingSlash(orderServiceUrl);
        this.httpClient = httpClient;
    }




    // ======================================= auth =========================================
    // No Authorization header: the user is not logged in yet. Content-Type is forwarded.
    public ResponseEntity<String> login(String requestBody, String contentType) {
        return forwardPost(authServerUrl, "/auth/login", requestBody, null, contentType);
    }

    // No Authorization header needed. The auth server answers with a cookie that removes the login.
    public ResponseEntity<String> logout(){
        return forwardPost(authServerUrl, "/auth/logout", "", null, null);
    }

    public ResponseEntity<String> registerUser(String requestBody, String contentType) {
        return forwardPost(authServerUrl, "/appusers", requestBody, null, contentType);
    }

    public ResponseEntity<String> currentUser(String authorization) {
        return forwardGet(authServerUrl, "/appusers/me", authorization);
    }

    public ResponseEntity<String> allUsers(String authorization) {
        return forwardGet(authServerUrl, "/appusers", authorization);
    }

    public ResponseEntity<String> userById(Long id, String authorization) {
        return forwardGet(authServerUrl, "/appusers/" + id, authorization);
    }


    // ==================================== product ============================================

    public ResponseEntity<String> allProducts(String authorization) {
        return forwardGet(productServiceUrl, "/products/all", authorization);
    }

    // The query string ( page, size, sort) is added to the path
    public ResponseEntity<String> allProductsPaged (String queryString, String authorization){
        return forwardGet(productServiceUrl, withQuery("/products/all-paged", queryString), authorization);
    }

    public ResponseEntity<String> productById(Long id, String authorization) {
        return forwardGet(productServiceUrl, "/products/" + id, authorization);
    }

    public ResponseEntity<String> productByUuid(String uuid, String authorization) {
        return forwardGet(productServiceUrl, "/products/uuid/" +  encodePathSegment(uuid), authorization);
    }

    public ResponseEntity<String> productsByCategory(Long categoryId, String authorization) {
        return forwardGet(productServiceUrl, "/products/category/" + categoryId, authorization);
    }

    public ResponseEntity<String> createProduct(String requestBody, String authorization, String contentType) {
        return forwardPost(productServiceUrl, "/products/new", requestBody, authorization, contentType);
    }

    public ResponseEntity<String> reduceStock(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/stock/reduce",queryString), authorization);
    }

    public ResponseEntity<String> increaseStock(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/stock/increase", queryString), authorization);
    }

    public ResponseEntity<String> updateBasicInfo(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/basic-info", queryString), authorization);
    }

    public ResponseEntity<String> updateImage(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/image", queryString), authorization);
    }

    public ResponseEntity<String> updateCategory(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/category", queryString), authorization);
    }

    public ResponseEntity<String> deleteProduct(Long id, String authorization) {
        return forwardDelete(productServiceUrl, "/products/" +id, authorization);
    }

    public ResponseEntity<String> allCategories(String authorization) {
        return forwardGet(productServiceUrl, "/categories/all", authorization);
    }

    public ResponseEntity<String> createCategory(String requestBody, String authorization, String contentType) {
        return forwardPost(productServiceUrl, "/categories/new", requestBody, authorization, contentType);
    }


    // ================================= order =======================================

    public ResponseEntity<String> createOrder(String requestBody, String authorization, String contentType) {
        return forwardPost(orderServiceUrl, "/order", requestBody, authorization, contentType);
    }

    public ResponseEntity<String> myOrders(String authorization) {
        return forwardGet(orderServiceUrl, "/order/orders", authorization);
    }


    // ================================= helper methods ======================================

    private ResponseEntity<String> forwardGet(String baseUrl, String path, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        return send(builder, authorization);
    }

    private ResponseEntity<String> forwardPost(
            String baseUrl,
            String path,
            String requestBody,
            String authorization,
            String contentType) {
        String bodyToSend = requestBody == null ? "" : requestBody;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(bodyToSend));
        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }

        return send( builder, authorization);
    }

    private ResponseEntity<String> forwardPatch(String baseUrl, String path, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .method("PATCH", HttpRequest.BodyPublishers.noBody());
        return send(builder, authorization);
    }

    private ResponseEntity<String> forwardDelete(String baseUrl, String path, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE();
        return send(builder,authorization);
    }

    private ResponseEntity<String> send (HttpRequest.Builder builder, String authorization ) {
        if (authorization != null && !authorization.isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }

        builder.timeout(REQUEST_TIMEOUT);

        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // The status code is kept: a failed answer is never turned into a success.
            ResponseEntity.BodyBuilder result = ResponseEntity.status( response.statusCode());

            response.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value ->result.header(HttpHeaders.CONTENT_TYPE, value));

            // Send back the Set-Cookie headers of the service. The browser stores the cookie form the gateway address,
            // the controller turns it into an Authorization header on the next requests.
            response.headers().allValues(HttpHeaders.SET_COOKIE)
                    .forEach(cookie -> result.header(HttpHeaders.SET_COOKIE, cookie));

            return result.body(response.body());

        }  catch ( IOException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Could not reach the downstream service");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("The request was interrupted");
        }
    }


    // The gateway works with or without a slash at the end.
    private String removeTrailingSlash(String url) {
        return url.endsWith("/")? url.substring(0, url.length() -1) :url;
    }

    private String withQuery(String path, String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return path;
        }
        return path +  "?" + queryString;
    }

    private String encodePathSegment(String pathSegment) {
        return UriUtils.encodePathSegment(pathSegment, StandardCharsets.UTF_8);
    }
}

