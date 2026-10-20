package se.iths.lw.webshopgateway.controller;


import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.iths.lw.webshopgateway.service.GatewayProxyService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// The proxy service is replaced by a mock, so these tests only check what the controller
// reads from the request and what it hands to the proxy service.
class GatewayControllerTest {

    private final GatewayProxyService gatewayProxyService = mock(GatewayProxyService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new GatewayController(gatewayProxyService))
            .build();

    @Test
    void turnsTheAccessTokenCookieIntoABearerAuthorization() throws Exception {
        when(gatewayProxyService.currentUser("Bearer my-jwt"))
                .thenReturn(ResponseEntity.ok("{\"username\":\"anna\"}"));

        mockMvc.perform(get("/gateway/appusers/me").cookie(new Cookie("accessToken", "my-jwt")))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"username\":\"anna\"}"));

        verify(gatewayProxyService).currentUser("Bearer my-jwt");
    }

    @Test
    void anAuthorizationHeaderWinsOverTheCookie() throws Exception {
        when(gatewayProxyService.currentUser("Bearer header-jwt")).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(get("/gateway/appusers/me")
                        .header("Authorization", "Bearer header-jwt")
                        .cookie(new Cookie("accessToken", "cookie-jwt")))
                .andExpect(status().isOk());

        verify(gatewayProxyService).currentUser("Bearer header-jwt");
    }

    @Test
    void passesNullWhenThereIsNoTokenAtAll() throws Exception {
        when(gatewayProxyService.allProducts(null)).thenReturn(ResponseEntity.ok("[]"));

        mockMvc.perform(get("/gateway/products/all"))
                .andExpect(status().isOk());

        verify(gatewayProxyService).allProducts(null);
    }

    @Test
    void ignoresAnEmptyAccessTokenCookie() throws Exception {
        when(gatewayProxyService.allProducts(null)).thenReturn(ResponseEntity.ok("[]"));

        mockMvc.perform(get("/gateway/products/all").cookie(new Cookie("accessToken", "")))
                .andExpect(status().isOk());

        verify(gatewayProxyService).allProducts(null);
    }

    @Test
    void loginPassesBodyAndContentTypeButNeverTheCookie() throws Exception {
        String loginBody = "{\"username\":\"anna\",\"password\":\"secret\"}";

        when(gatewayProxyService.loginWithCookie("{}", "application/json"))
                .thenAnswer(invocation -> ResponseEntity.ok("{}"));

        mockMvc.perform(post("/gateway/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody)
                        .cookie(new Cookie("accessToken", "old-jwt")))
                .andExpect(status().isOk());

        verify(gatewayProxyService).loginWithCookie(loginBody, "application/json");
    }

    @Test
    void returnsTheStatusAndSetCookieHeaderFromTheService() throws Exception {
        doReturn(ResponseEntity.status(200)
                .header("Set-Cookie", "accessToken=abc; Path=/; HttpOnly")
                .body("{}"))
                .when(gatewayProxyService)
                .loginWithCookie("{}", "application/json");

        mockMvc.perform(post("/gateway/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Set-Cookie",
                        "accessToken=abc; Path=/; HttpOnly"
                ));

        verify(gatewayProxyService).loginWithCookie("{}", "application/json");
    }

    @Test
    void productByIdPassesTheNumericId() throws Exception {
        when(gatewayProxyService.productById(42L, null)).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(get("/gateway/products/42"))
                .andExpect(status().isOk());

        verify(gatewayProxyService).productById(42L, null);
    }

    @Test
    void rejectsAProductIdThatIsNotANumber() throws Exception {
        mockMvc.perform(get("/gateway/products/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrderPassesBodyTokenFromCookieAndContentType() throws Exception {
        String orderBody = "{\"items\":[]}";
        when(gatewayProxyService.createOrder(orderBody, "Bearer my-jwt", "application/json"))
                .thenReturn(ResponseEntity.status(201).body("{}"));

        mockMvc.perform(post("/gateway/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody)
                        .cookie(new Cookie("accessToken", "my-jwt")))
                .andExpect(status().isCreated());

        verify(gatewayProxyService).createOrder(orderBody, "Bearer my-jwt", "application/json");
    }

    // ===================== Tests for the newer routes =====================

    @Test
    void reduceStockPassesUuidQueryStringAndTokenFromCookie() throws Exception {
        when(gatewayProxyService.reduceStock("abc-123", "quantity=2", "Bearer my-jwt"))
                .thenReturn(ResponseEntity.ok("{\"stock\":8}"));

        mockMvc.perform(patch("/gateway/products/abc-123/stock/reduce?quantity=2")
                        .cookie(new Cookie("accessToken", "my-jwt")))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"stock\":8}"));

        verify(gatewayProxyService).reduceStock("abc-123", "quantity=2", "Bearer my-jwt");
    }

    @Test
    void increaseStockUpdateImageAndUpdateCategoryAreRouted() throws Exception {
        when(gatewayProxyService.increaseStock("abc-123", "quantity=5", null)).thenReturn(ResponseEntity.ok("{}"));
        when(gatewayProxyService.updateImage("abc-123", "imageUrl=https://example.com/a.png", null))
                .thenReturn(ResponseEntity.ok("{}"));
        when(gatewayProxyService.updateCategory("abc-123", "categoryId=3", null)).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(patch("/gateway/products/abc-123/stock/increase?quantity=5")).andExpect(status().isOk());
        mockMvc.perform(patch("/gateway/products/abc-123/image?imageUrl=https://example.com/a.png"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/gateway/products/abc-123/category?categoryId=3")).andExpect(status().isOk());

        verify(gatewayProxyService).increaseStock("abc-123", "quantity=5", null);
        verify(gatewayProxyService).updateImage("abc-123", "imageUrl=https://example.com/a.png", null);
        verify(gatewayProxyService).updateCategory("abc-123", "categoryId=3", null);
    }

    @Test
    void updateBasicInfoPassesTheWholeQueryString() throws Exception {
        when(gatewayProxyService.updateBasicInfo("abc-123", "name=Mug&description=Blue&price=9.90", null))
                .thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(patch("/gateway/products/abc-123/basic-info?name=Mug&description=Blue&price=9.90"))
                .andExpect(status().isOk());

        verify(gatewayProxyService).updateBasicInfo("abc-123", "name=Mug&description=Blue&price=9.90", null);
    }

    @Test
    void allProductsPagedPassesTheQueryString() throws Exception {
        when(gatewayProxyService.allProductsPaged("page=0&size=5", null)).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(get("/gateway/products/all-paged?page=0&size=5"))
                .andExpect(status().isOk());

        verify(gatewayProxyService).allProductsPaged("page=0&size=5", null);
    }

    @Test
    void allProductsPagedWithoutQueryStringPassesNull() throws Exception {
        when(gatewayProxyService.allProductsPaged(null, null)).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(get("/gateway/products/all-paged")).andExpect(status().isOk());

        verify(gatewayProxyService).allProductsPaged(null, null);
    }

    @Test
    void productByUuidAndProductsByCategoryAreRouted() throws Exception {
        when(gatewayProxyService.productByUuid("abc-123", null)).thenReturn(ResponseEntity.ok("{}"));
        when(gatewayProxyService.productsByCategory(4L, null)).thenReturn(ResponseEntity.ok("[]"));

        mockMvc.perform(get("/gateway/products/uuid/abc-123")).andExpect(status().isOk());
        mockMvc.perform(get("/gateway/products/category/4")).andExpect(status().isOk());

        verify(gatewayProxyService).productByUuid("abc-123", null);
        verify(gatewayProxyService).productsByCategory(4L, null);
    }

    @Test
    void createCategoryPassesBodyTokenAndContentType() throws Exception {
        String categoryBody = "{\"name\":\"Books\"}";
        when(gatewayProxyService.createCategory(categoryBody, "Bearer admin-jwt", "application/json"))
                .thenReturn(ResponseEntity.status(201).body("{}"));

        mockMvc.perform(post("/gateway/categories/new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody)
                        .cookie(new Cookie("accessToken", "admin-jwt")))
                .andExpect(status().isCreated());

        verify(gatewayProxyService).createCategory(categoryBody, "Bearer admin-jwt", "application/json");
    }

    @Test
    void allUsersAndUserByIdAreRoutedAndMeStillGoesToCurrentUser() throws Exception {
        when(gatewayProxyService.allUsers("Bearer my-jwt")).thenReturn(ResponseEntity.ok("[]"));
        when(gatewayProxyService.userById(9L, "Bearer my-jwt")).thenReturn(ResponseEntity.ok("{}"));
        when(gatewayProxyService.currentUser("Bearer my-jwt")).thenReturn(ResponseEntity.ok("{\"me\":true}"));

        Cookie loginCookie = new Cookie("accessToken", "my-jwt");
        mockMvc.perform(get("/gateway/appusers").cookie(loginCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/gateway/appusers/9").cookie(loginCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/gateway/appusers/me").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"me\":true}"));

        verify(gatewayProxyService).allUsers("Bearer my-jwt");
        verify(gatewayProxyService).userById(9L, "Bearer my-jwt");
        verify(gatewayProxyService).currentUser("Bearer my-jwt");
    }
}
