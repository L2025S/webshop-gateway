package se.iths.lw.webshopgateway.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.iths.lw.webshopgateway.service.GatewayProxyService;

@RestController
@RequestMapping("/gateway")
public class GatewayController {

    // Defines the cookie name and authorization prefix used for JWT authentication.
    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String BEARER_PREFIX = "Bearer ";
    private final GatewayProxyService gatewayProxyService;

    // Injects the service that forwards requests to backend services.
    public GatewayController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    // =============================== auth controller ==================================

    // Forwards login credentials to the authentication server and sets the access token cookie.
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(
            @RequestBody String requestBody, HttpServletRequest request
    ) {
        return gatewayProxyService.loginWithCookie(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    // Clears the access token cookie to log the user out.
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return gatewayProxyService.logout();
    }

    // Forwards a user registration request to the authentication server.
    @PostMapping("/appusers")
    public ResponseEntity<String> registerUser(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.registerUser(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    // Retrieves the currently authenticated user's details.
    @GetMapping("/appusers/me")
    public ResponseEntity<String> currentUser(HttpServletRequest request) {
        return gatewayProxyService.currentUser(authorizationFrom(request));
    }

    // Retrieves all registered users.
    @GetMapping("/appusers")
    public ResponseEntity<String> allUsers(HttpServletRequest request) {
        return gatewayProxyService.allUsers(authorizationFrom(request));
    }

    // Retrieves a user by ID.
    @GetMapping("/appusers/{id}")
    public ResponseEntity<String> userById(@PathVariable Long id, HttpServletRequest request) {
        return gatewayProxyService.userById(id, authorizationFrom(request));
    }

    // ================================== product controller =================================

    // Retrieves all products.
    @GetMapping("/products/all")
    public ResponseEntity<String> allProducts(HttpServletRequest request) {
        return gatewayProxyService.allProducts(authorizationFrom(request));
    }

    // Retrieves a paginated product list and forwards its query parameters.
    @GetMapping("/products/all-paged")
    public ResponseEntity<String> allProductsPaged(HttpServletRequest request) {
        return gatewayProxyService.allProductsPaged(request.getQueryString(), authorizationFrom(request));
    }

    // Retrieves a product by its numeric ID.
    @GetMapping("/products/{id}")
    public ResponseEntity<String> productById(@PathVariable Long id, HttpServletRequest request) {
        return gatewayProxyService.productById(id, authorizationFrom(request));
    }

    // Retrieves a product by UUID.
    @GetMapping("/products/uuid/{uuid}")
    public ResponseEntity<String> productByUuid(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.productByUuid(uuid, authorizationFrom(request));
    }

    // Retrieves products belonging to a specific category.
    @GetMapping("/products/category/{categoryId}")
    public ResponseEntity<String> productsByCategory(@PathVariable Long categoryId, HttpServletRequest request) {
        return gatewayProxyService.productsByCategory(categoryId, authorizationFrom(request));
    }

    // Retrieves all product categories.
    @GetMapping("/categories/all")
    public ResponseEntity<String> allCategories(HttpServletRequest request) {
        return gatewayProxyService.allCategories(authorizationFrom(request));
    }

    // Forwards a request to create a product.
    @PostMapping("/products/new")
    public ResponseEntity<String> createProduct(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.createProduct(
                requestBody,
                authorizationFrom(request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    // Forwards a request to create a product category.
    @PostMapping("/categories/new")
    public ResponseEntity<String> createCategory(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.createCategory(
                requestBody,
                authorizationFrom(request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    // Forwards a request to reduce a product's stock quantity.
    @PatchMapping("/products/{uuid}/stock/reduce")
    public ResponseEntity<String> reduceStock(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.reduceStock(uuid, request.getQueryString(), authorizationFrom(request));
    }

    // Forwards a request to increase a product's stock quantity.
    @PatchMapping("/products/{uuid}/stock/increase")
    public ResponseEntity<String> increaseStock(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.increaseStock(uuid, request.getQueryString(), authorizationFrom(request));
    }

    // Forwards a request to update a product's basic information.
    @PatchMapping("/products/{uuid}/basic-info")
    public ResponseEntity<String> updateBasicInfo(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.updateBasicInfo(uuid, request.getQueryString(), authorizationFrom(request));
    }

    // Forwards a request to update a product's image.
    @PatchMapping("/products/{uuid}/image")
    public ResponseEntity<String> updateImage(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.updateImage(uuid, request.getQueryString(), authorizationFrom(request));
    }

    // Forwards a request to change a product's category.
    @PatchMapping("/products/{uuid}/category")
    public ResponseEntity<String> updateCategory(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.updateCategory(uuid, request.getQueryString(), authorizationFrom(request));
    }

    // Forwards a request to delete a product by ID.
    @DeleteMapping("/products/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable Long id, HttpServletRequest request) {
        return gatewayProxyService.deleteProduct(id, authorizationFrom(request));
    }

    // ======================== order controller ======================================

    // Forwards a request to create an order.
    @PostMapping("/order")
    public ResponseEntity<String> createOrder(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.createOrder(
                requestBody,
                authorizationFrom(request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    // Retrieves the authenticated user's orders.
    @GetMapping("/order/orders")
    public ResponseEntity<String> myOrders(HttpServletRequest request) {
        return gatewayProxyService.myOrders(authorizationFrom(request));
    }

    // Retrieves the authorization header or builds one from the access token cookie.
    private String authorizationFrom(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Uses the existing authorization header when one is provided.
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return authorizationHeader;
        }

        // Returns null if the request contains no cookies.
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        // Finds a non-empty access token cookie and converts it to a Bearer token.
        for (Cookie cookie : cookies) {
            boolean isAccessTokenCookie = ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName());
            boolean hasValue = cookie.getValue() != null && !cookie.getValue().isBlank();

            if (isAccessTokenCookie && hasValue) {
                return BEARER_PREFIX + cookie.getValue();
            }
        }

        // Returns null when no usable access token is available.
        return null;
    }
}