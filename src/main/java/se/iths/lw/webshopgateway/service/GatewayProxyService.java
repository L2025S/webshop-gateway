package se.iths.lw.webshopgateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import se.iths.lw.webshopgateway.dto.AuthServiceResponse;
import se.iths.lw.webshopgateway.dto.LoginResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Forwards requests to backend services and handles authentication cookies.
@Service
public class GatewayProxyService {

    // Sets the maximum time allowed for a downstream request.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String authServerUrl;
    private final String productServiceUrl;
    private final String orderServiceUrl;
    private final HttpClient httpClient;

    private final boolean cookieSecure;
    private final String cookieSameSite;

    // Converts JSON responses into Java objects.
    private final ObjectMapper objectMapper =
            new ObjectMapper();

    // Injects backend URLs, cookie settings, and the HTTP client.
    public GatewayProxyService(
            @Value("${app.auth-server-url}") String authServerUrl,
            @Value("${app.product-service-url}") String productServiceUrl,
            @Value("${app.order-service-url}") String orderServiceUrl,
            @Value("${COOKIE_SECURE:true}") boolean cookieSecure,
            @Value("${COOKIE_SAME_SITE:None}") String cookieSameSite,
            HttpClient httpClient
    ) {
        this.authServerUrl = removeTrailingSlash(authServerUrl);
        this.productServiceUrl = removeTrailingSlash(productServiceUrl);
        this.orderServiceUrl = removeTrailingSlash(orderServiceUrl);
        this.httpClient = httpClient;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    // ======================================= auth =========================================

    // Forwards login credentials to the authentication server without an access token.
    public ResponseEntity<String> login(String requestBody, String contentType) {
        return forwardPost(authServerUrl, "/auth/login", requestBody, null, contentType);
    }

    // Authenticates the user and prepares the response for cookie-based login.
    public ResponseEntity<?> loginWithCookie(
            String requestBody,
            String contentType
    ) {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create(authServerUrl + "/auth/login")
                )
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        contentType
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(requestBody)
                );
        return sendLogin(request);
    }

    // Sends the login request and handles the authentication server's response.
    private ResponseEntity<?> sendLogin(
            HttpRequest.Builder request
    ) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // Preserves the authentication server's status and response body on failure.
            if (response.statusCode() < 200 ||
                    response.statusCode() >= 300) {
                return ResponseEntity
                        .status(response.statusCode())
                        .body(response.body());
            }

            return createLoginResponse(response.body());
        } catch (IOException | InterruptedException ex) {
            // Restores the interrupt flag if the request is interrupted.
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            // Returns a gateway error when the authentication server cannot be reached.
            return ResponseEntity.status(502)
                    .body("Gateway could not reach auth service");
        }
    }

    // Converts the authentication response into a secure cookie and a login response.
    private ResponseEntity<?> createLoginResponse(
            String responseBody
    ) {
        try {
            // Extracts the access token, expiration time, subject, and roles from JSON.
            AuthServiceResponse authResponse =
                    objectMapper.readValue(
                            responseBody,
                            AuthServiceResponse.class
                    );

            // Creates an HTTP-only cookie containing the access token.
            ResponseCookie cookie = ResponseCookie.from(
                            "accessToken",
                            authResponse.accessToken()
                    )
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite(cookieSameSite)
                    .path("/")
                    .maxAge(Duration.ofSeconds(
                            authResponse.expiresIn()
                    ))
                    .build();

            // Excludes the access token from the response body.
            LoginResponse loginResponse = new LoginResponse(
                    authResponse.expiresIn(),
                    authResponse.subject(),
                    authResponse.roles()
            );

            // Returns the user details and sets the access token as a cookie.
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(loginResponse);
        } catch (JacksonException ex) {
            // Reports invalid JSON from the authentication server as a gateway error.
            return ResponseEntity.status(502)
                    .body("Auth service returned an invalid response");
        }
    }

    // Clears the access token cookie to log the user out of the gateway.
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie
                .from("accessToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie.toString()
                )
                .build();
    }

    // Forwards a user registration request to the authentication server.
    public ResponseEntity<String> registerUser(String requestBody, String contentType) {
        return forwardPost(authServerUrl, "/appusers", requestBody, null, contentType);
    }

    // Retrieves the currently authenticated user.
    public ResponseEntity<String> currentUser(String authorization) {
        return forwardGet(authServerUrl, "/appusers/me", authorization);
    }

    // Retrieves all registered users.
    public ResponseEntity<String> allUsers(String authorization) {
        return forwardGet(authServerUrl, "/appusers", authorization);
    }

    // Retrieves a user by ID.
    public ResponseEntity<String> userById(Long id, String authorization) {
        return forwardGet(authServerUrl, "/appusers/" + id, authorization);
    }

    // ==================================== product ============================================

    // Retrieves all products.
    public ResponseEntity<String> allProducts(String authorization) {
        return forwardGet(productServiceUrl, "/products/all", authorization);
    }

    // Retrieves a paginated product list using the supplied query parameters.
    public ResponseEntity<String> allProductsPaged(String queryString, String authorization) {
        return forwardGet(productServiceUrl, withQuery("/products/all-paged", queryString), authorization);
    }

    // Retrieves a product by its numeric ID.
    public ResponseEntity<String> productById(Long id, String authorization) {
        return forwardGet(productServiceUrl, "/products/" + id, authorization);
    }

    // Retrieves a product by UUID, encoding the UUID as a URL path segment.
    public ResponseEntity<String> productByUuid(String uuid, String authorization) {
        return forwardGet(productServiceUrl, "/products/uuid/" + encodePathSegment(uuid), authorization);
    }

    // Retrieves products belonging to a specific category.
    public ResponseEntity<String> productsByCategory(Long categoryId, String authorization) {
        return forwardGet(productServiceUrl, "/products/category/" + categoryId, authorization);
    }

    // Forwards a product creation request.
    public ResponseEntity<String> createProduct(String requestBody, String authorization, String contentType) {
        return forwardPost(productServiceUrl, "/products/new", requestBody, authorization, contentType);
    }

    // Requests a reduction in a product's stock quantity.
    public ResponseEntity<String> reduceStock(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/stock/reduce", queryString), authorization);
    }

    // Requests an increase in a product's stock quantity.
    public ResponseEntity<String> increaseStock(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/stock/increase", queryString), authorization);
    }

    // Updates a product's basic information.
    public ResponseEntity<String> updateBasicInfo(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/basic-info", queryString), authorization);
    }

    // Updates a product's image.
    public ResponseEntity<String> updateImage(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/image", queryString), authorization);
    }

    // Updates a product's category.
    public ResponseEntity<String> updateCategory(String uuid, String queryString, String authorization) {
        return forwardPatch(productServiceUrl,
                withQuery("/products/" + encodePathSegment(uuid) + "/category", queryString), authorization);
    }

    // Deletes a product by ID.
    public ResponseEntity<String> deleteProduct(Long id, String authorization) {
        return forwardDelete(productServiceUrl, "/products/" + id, authorization);
    }

    // Retrieves all product categories.
    public ResponseEntity<String> allCategories(String authorization) {
        return forwardGet(productServiceUrl, "/categories/all", authorization);
    }

    // Forwards a category creation request.
    public ResponseEntity<String> createCategory(String requestBody, String authorization, String contentType) {
        return forwardPost(productServiceUrl, "/categories/new", requestBody, authorization, contentType);
    }

    // ================================= order =======================================

    // Forwards an order creation request to the order service.
    public ResponseEntity<String> createOrder(String requestBody, String authorization, String contentType) {
        return forwardPost(orderServiceUrl, "/order", requestBody, authorization, contentType);
    }

    // Retrieves the authenticated user's orders.
    public ResponseEntity<String> myOrders(String authorization) {
        return forwardGet(orderServiceUrl, "/order/orders", authorization);
    }

    // ================================= helper methods ======================================

    // Sends a GET request to a downstream service.
    private ResponseEntity<String> forwardGet(String baseUrl, String path, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        return send(builder, authorization);
    }

    // Sends a POST request with an optional body, content type, and authorization header.
    private ResponseEntity<String> forwardPost(
            String baseUrl,
            String path,
            String requestBody,
            String authorization,
            String contentType) {
        String bodyToSend = requestBody == null ? "" : requestBody;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(bodyToSend));

        // Forwards the content type when one is provided.
        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }

        return send(builder, authorization);
    }

    // Sends a PATCH request without a request body.
    private ResponseEntity<String> forwardPatch(String baseUrl, String path, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .method("PATCH", HttpRequest.BodyPublishers.noBody());
        return send(builder, authorization);
    }

    // Sends a DELETE request to a downstream service.
    private ResponseEntity<String> forwardDelete(String baseUrl, String path, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE();
        return send(builder, authorization);
    }

    // Executes the request and forwards the downstream response to the client.
    private ResponseEntity<String> send(HttpRequest.Builder builder, String authorization) {
        // Forwards the authorization header when the request includes one.
        if (authorization != null && !authorization.isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }

        // Applies the maximum allowed request duration.
        builder.timeout(REQUEST_TIMEOUT);

        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // Preserves the downstream HTTP status instead of treating failures as successes.
            ResponseEntity.BodyBuilder result = ResponseEntity.status(response.statusCode());

            // Forwards the response content type when available.
            response.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value -> result.header(HttpHeaders.CONTENT_TYPE, value));

            // Forwards cookies so the browser can process them through the gateway's origin.
            response.headers().allValues(HttpHeaders.SET_COOKIE)
                    .forEach(cookie -> result.header(HttpHeaders.SET_COOKIE, cookie));

            return result.body(response.body());

        } catch (IOException exception) {
            // Returns 502 when the downstream service cannot be reached.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Could not reach the downstream service");
        } catch (InterruptedException exception) {
            // Restores the interrupt flag and returns 503 when the request is interrupted.
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("The request was interrupted");
        }
    }

    // Removes a trailing slash to keep constructed URLs consistent.
    private String removeTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // Appends query parameters when the query string is not empty.
    private String withQuery(String path, String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return path;
        }
        return path + "?" + queryString;
    }

    // Encodes a path segment to safely include special characters in a URL.
    private String encodePathSegment(String pathSegment) {
        return UriUtils.encodePathSegment(pathSegment, StandardCharsets.UTF_8);
    }
}