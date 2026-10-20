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

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String BEARER_PREFIX = "Bearer ";
    private final GatewayProxyService gatewayProxyService;

    public GatewayController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    // =============================== auth controller ==================================
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(
            @RequestBody String requestBody, HttpServletRequest request
    ) {
        return gatewayProxyService.loginWithCookie(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }


    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return gatewayProxyService.logout();
    }

    @PostMapping("/appusers")
    public ResponseEntity<String> registerUser(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.registerUser(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    @GetMapping("/appusers/me")
    public ResponseEntity<String> currentUser(HttpServletRequest request) {
        return gatewayProxyService.currentUser(authorizationFrom(request));
    }

    @GetMapping("/appusers")
    public ResponseEntity<String> allUsers(HttpServletRequest request) {
        return gatewayProxyService.allUsers(authorizationFrom(request));
    }

    @GetMapping("/appusers/{id}")
    public ResponseEntity<String> userById(@PathVariable Long id, HttpServletRequest request) {
        return gatewayProxyService.userById(id, authorizationFrom(request));
    }


    // ================================== product controller =================================
    @GetMapping("/products/all")
    public ResponseEntity<String> allProducts(HttpServletRequest request) {
        return gatewayProxyService.allProducts(authorizationFrom(request));
    }

    @GetMapping("/products/all-paged")
    public ResponseEntity<String> allProductsPaged(HttpServletRequest request) {
        return gatewayProxyService.allProductsPaged(request.getQueryString(), authorizationFrom(request));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<String> productById(@PathVariable Long id, HttpServletRequest request) {
        return gatewayProxyService.productById(id, authorizationFrom(request));
    }

    @GetMapping("/products/uuid/{uuid}")
    public ResponseEntity<String> productByUuid(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.productByUuid(uuid, authorizationFrom(request));
    }

    @GetMapping("/products/category/{categoryId}")
    public ResponseEntity<String> productsByCategory(@PathVariable Long categoryId, HttpServletRequest request) {
        return gatewayProxyService.productsByCategory(categoryId, authorizationFrom(request));
    }

    @GetMapping("/categories/all")
    public ResponseEntity<String> allCategories(HttpServletRequest request) {
        return gatewayProxyService.allCategories(authorizationFrom(request));
    }


    @PostMapping("/products/new")
    public ResponseEntity<String> createProduct(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.createProduct(
                requestBody,
                authorizationFrom(request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    @PostMapping("/categories/new")
    public ResponseEntity<String> createCategory(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.createCategory(
                requestBody,
                authorizationFrom(request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    @PatchMapping("/products/{uuid}/stock/reduce")
    public ResponseEntity<String> reduceStock(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.reduceStock(uuid, request.getQueryString(), authorizationFrom(request));
    }

    @PatchMapping("/products/{uuid}/stock/increase")
    public ResponseEntity<String> increaseStock(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.increaseStock(uuid, request.getQueryString(), authorizationFrom(request));
    }

    @PatchMapping("/products/{uuid}/basic-info")
    public ResponseEntity<String> updateBasicInfo(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.updateBasicInfo(uuid, request.getQueryString(), authorizationFrom(request));
    }

    @PatchMapping("/products/{uuid}/image")
    public ResponseEntity<String> updateImage(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.updateImage(uuid, request.getQueryString(), authorizationFrom(request));
    }

    @PatchMapping("/products/{uuid}/category")
    public ResponseEntity<String> updateCategory(@PathVariable String uuid, HttpServletRequest request) {
        return gatewayProxyService.updateCategory(uuid, request.getQueryString(), authorizationFrom(request));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable Long id, HttpServletRequest request) {
        return gatewayProxyService.deleteProduct(id, authorizationFrom(request));
    }


    // ======================== order  controller ======================================


    @PostMapping("/order")
    public ResponseEntity<String> createOrder(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.createOrder(
                requestBody,
                authorizationFrom(request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }


    @GetMapping("/order/orders")
    public ResponseEntity<String> myOrders(HttpServletRequest request) {
        return gatewayProxyService.myOrders(authorizationFrom(request));
    }


    private String authorizationFrom(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return authorizationHeader;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            boolean isAccessTokenCookie = ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName());
            boolean hasValue = cookie.getValue() != null && !cookie.getValue().isBlank();

            if (isAccessTokenCookie && hasValue) {
                return BEARER_PREFIX + cookie.getValue();
            }
        }
        return null;
    }
}
