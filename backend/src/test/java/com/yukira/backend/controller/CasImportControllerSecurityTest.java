package com.yukira.backend.controller;

import com.yukira.backend.dto.portfolio.CasImportConfirmResponseDto;
import com.yukira.backend.dto.portfolio.CasImportResultDto;
import com.yukira.backend.service.portfolio.cas.CasImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CasImportControllerSecurityTest {

    @Mock
    private CasImportService importService;

    private CasImportController controller;

    private static final String AUTHENTICATED_INVESTOR = "auth0|investor-verified-123";
    private static final String ATTACKER_SPOOFED_SUBJECT = "auth0|victim-investor-456";

    private Jwt createTestJwt(String subject) {
        return new Jwt(
            "mock-jwt-token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            java.util.Map.of("alg", "none"),
            java.util.Map.of("sub", subject, "aud", "https://api.yukira.app")
        );
    }

    @BeforeEach
    void setUp() {
        controller = new CasImportController(importService);
    }

    @Nested
    @DisplayName("1. Unauthenticated Request Rejection")
    class UnauthenticatedRejectionTests {

        @Test
        @DisplayName("uploadCasFile rejects unauthenticated request (no JWT, no Authentication)")
        void uploadCasFile_unauthenticated_throwsAccessDenied() {
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1, 2, 3});
            assertThatThrownBy(() -> controller.uploadCasFile(file, null, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authenticated investor principal required");
            verifyNoInteractions(importService);
        }

        @Test
        @DisplayName("stageCasJson rejects unauthenticated request")
        void stageCasJson_unauthenticated_throwsAccessDenied() {
            assertThatThrownBy(() -> controller.stageCasJson("{}", "test.json", null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authenticated investor principal required");
            verifyNoInteractions(importService);
        }

        @Test
        @DisplayName("getImportDetails rejects unauthenticated request")
        void getImportDetails_unauthenticated_throwsAccessDenied() {
            assertThatThrownBy(() -> controller.getImportDetails(100L, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authenticated investor principal required");
            verifyNoInteractions(importService);
        }

        @Test
        @DisplayName("confirmImport rejects unauthenticated request")
        void confirmImport_unauthenticated_throwsAccessDenied() {
            assertThatThrownBy(() -> controller.confirmImport(100L, "MERGE", null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authenticated investor principal required");
            verifyNoInteractions(importService);
        }

        @Test
        @DisplayName("listImports rejects unauthenticated request")
        void listImports_unauthenticated_throwsAccessDenied() {
            assertThatThrownBy(() -> controller.listImports(null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authenticated investor principal required");
            verifyNoInteractions(importService);
        }

        @Test
        @DisplayName("Requests with anonymous authentication are rejected")
        void anonymousAuthentication_throwsAccessDenied() {
            Authentication anon = new TestingAuthenticationToken("anonymousUser", null);
            assertThatThrownBy(() -> controller.listImports(null, anon))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authenticated investor principal required");
            verifyNoInteractions(importService);
        }

        @Test
        @DisplayName("Requests with empty or blank subject are rejected")
        void blankSubject_throwsAccessDenied() {
            Jwt blankJwt = createTestJwt("   ");
            assertThatThrownBy(() -> controller.listImports(blankJwt, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authenticated investor principal required");
            verifyNoInteractions(importService);
        }
    }

    @Nested
    @DisplayName("2. Authenticated Identity Derivation from Security Principal")
    class AuthenticatedIdentityTests {

        @Test
        @DisplayName("uploadCasFile derives identity strictly from JWT subject")
        void uploadCasFile_authenticated_usesJwtSubject() throws Exception {
            Jwt jwt = createTestJwt(AUTHENTICATED_INVESTOR);
            MockMultipartFile file = new MockMultipartFile("file", "cas_statement.pdf", "application/pdf", "data".getBytes());
            CasImportResultDto expectedResult = mock(CasImportResultDto.class);
            when(importService.stageImport(eq(AUTHENTICATED_INVESTOR), eq("cas_statement.pdf"), any(InputStream.class), eq("pwd123")))
                .thenReturn(expectedResult);

            ResponseEntity<CasImportResultDto> response = controller.uploadCasFile(file, "pwd123", jwt, null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isSameAs(expectedResult);
            verify(importService).stageImport(eq(AUTHENTICATED_INVESTOR), eq("cas_statement.pdf"), any(InputStream.class), eq("pwd123"));
        }

        @Test
        @DisplayName("stageCasJson derives identity strictly from JWT subject")
        void stageCasJson_authenticated_usesJwtSubject() {
            Jwt jwt = createTestJwt(AUTHENTICATED_INVESTOR);
            CasImportResultDto expectedResult = mock(CasImportResultDto.class);
            when(importService.stageImport(eq(AUTHENTICATED_INVESTOR), eq("cas.json"), any(InputStream.class)))
                .thenReturn(expectedResult);

            ResponseEntity<CasImportResultDto> response = controller.stageCasJson("{\"data\": 1}", "cas.json", jwt, null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isSameAs(expectedResult);
            verify(importService).stageImport(eq(AUTHENTICATED_INVESTOR), eq("cas.json"), any(InputStream.class));
        }

        @Test
        @DisplayName("getImportDetails derives identity strictly from JWT subject")
        void getImportDetails_authenticated_usesJwtSubject() {
            Jwt jwt = createTestJwt(AUTHENTICATED_INVESTOR);
            CasImportResultDto expectedResult = mock(CasImportResultDto.class);
            when(importService.getImportDetails(AUTHENTICATED_INVESTOR, 3L)).thenReturn(expectedResult);

            ResponseEntity<CasImportResultDto> response = controller.getImportDetails(3L, jwt, null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isSameAs(expectedResult);
            verify(importService).getImportDetails(AUTHENTICATED_INVESTOR, 3L);
        }

        @Test
        @DisplayName("confirmImport derives identity strictly from JWT subject")
        void confirmImport_authenticated_usesJwtSubject() {
            Jwt jwt = createTestJwt(AUTHENTICATED_INVESTOR);
            CasImportConfirmResponseDto expectedResult = mock(CasImportConfirmResponseDto.class);
            when(importService.confirmImport(AUTHENTICATED_INVESTOR, 4L, "MERGE")).thenReturn(expectedResult);

            ResponseEntity<CasImportConfirmResponseDto> response = controller.confirmImport(4L, "MERGE", jwt, null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isSameAs(expectedResult);
            verify(importService).confirmImport(AUTHENTICATED_INVESTOR, 4L, "MERGE");
        }

        @Test
        @DisplayName("listImports derives identity strictly from JWT subject")
        void listImports_authenticated_usesJwtSubject() {
            Jwt jwt = createTestJwt(AUTHENTICATED_INVESTOR);
            CasImportResultDto item = mock(CasImportResultDto.class);
            List<CasImportResultDto> expectedList = List.of(item);
            when(importService.listInvestorImports(AUTHENTICATED_INVESTOR)).thenReturn(expectedList);

            ResponseEntity<List<CasImportResultDto>> response = controller.listImports(jwt, null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isSameAs(expectedList);
            verify(importService).listInvestorImports(AUTHENTICATED_INVESTOR);
        }

        @Test
        @DisplayName("Fallback to authenticated non-JWT Authentication principal succeeds")
        void authenticationFallback_usesAuthenticationName() {
            Authentication auth = new TestingAuthenticationToken("auth-token-investor-789", null);
            auth.setAuthenticated(true);
            when(importService.listInvestorImports("auth-token-investor-789")).thenReturn(List.of());

            ResponseEntity<List<CasImportResultDto>> response = controller.listImports(null, auth);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            verify(importService).listInvestorImports("auth-token-investor-789");
        }
    }

    @Nested
    @DisplayName("3. Spoofed Identity Header Isolation & Regression Prevention")
    class SpoofingPreventionTests {

        private MockMvc mockMvc;

        @BeforeEach
        void setupMockMvc() {
            // Resolver that provides the authenticated JWT principal regardless of HTTP headers
            HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return Jwt.class.isAssignableFrom(parameter.getParameterType());
                }

                @Override
                public Object resolveArgument(MethodParameter parameter,
                                              ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest,
                                              WebDataBinderFactory binderFactory) {
                    return createTestJwt(AUTHENTICATED_INVESTOR);
                }
            };

            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(jwtResolver)
                .build();
        }

        @Test
        @DisplayName("GET /imports with spoofed X-Dev-Subject ignores header and uses JWT principal")
        void listImports_withSpoofedHeader_ignoresHeader() throws Exception {
            when(importService.listInvestorImports(AUTHENTICATED_INVESTOR)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/investor/portfolio/imports")
                    .header("X-Dev-Subject", ATTACKER_SPOOFED_SUBJECT))
                .andExpect(status().isOk());

            // Proves ATTACKER_SPOOFED_SUBJECT was NEVER passed to importService
            verify(importService).listInvestorImports(AUTHENTICATED_INVESTOR);
            verify(importService, never()).listInvestorImports(ATTACKER_SPOOFED_SUBJECT);
            verify(importService, never()).listInvestorImports("dev-investor-local");
        }

        @Test
        @DisplayName("GET /imports/{id} with spoofed X-Dev-Subject ignores header and uses JWT principal")
        void getImportDetails_withSpoofedHeader_ignoresHeader() throws Exception {
            CasImportResultDto mockResult = mock(CasImportResultDto.class);
            when(importService.getImportDetails(AUTHENTICATED_INVESTOR, 10L)).thenReturn(mockResult);

            mockMvc.perform(get("/api/v1/investor/portfolio/imports/10")
                    .header("X-Dev-Subject", ATTACKER_SPOOFED_SUBJECT))
                .andExpect(status().isOk());

            verify(importService).getImportDetails(AUTHENTICATED_INVESTOR, 10L);
            verify(importService, never()).getImportDetails(eq(ATTACKER_SPOOFED_SUBJECT), anyLong());
            verify(importService, never()).getImportDetails(eq("dev-investor-local"), anyLong());
        }

        @Test
        @DisplayName("POST /imports/{id}/confirm with spoofed X-Dev-Subject ignores header and uses JWT principal")
        void confirmImport_withSpoofedHeader_ignoresHeader() throws Exception {
            CasImportConfirmResponseDto mockConfirm = mock(CasImportConfirmResponseDto.class);
            when(importService.confirmImport(AUTHENTICATED_INVESTOR, 10L, "MERGE")).thenReturn(mockConfirm);

            mockMvc.perform(post("/api/v1/investor/portfolio/imports/10/confirm")
                    .header("X-Dev-Subject", ATTACKER_SPOOFED_SUBJECT))
                .andExpect(status().isOk());

            verify(importService).confirmImport(AUTHENTICATED_INVESTOR, 10L, "MERGE");
            verify(importService, never()).confirmImport(eq(ATTACKER_SPOOFED_SUBJECT), anyLong(), anyString());
            verify(importService, never()).confirmImport(eq("dev-investor-local"), anyLong(), anyString());
        }

        @Test
        @DisplayName("POST /imports JSON with spoofed X-Dev-Subject ignores header and uses JWT principal")
        void stageCasJson_withSpoofedHeader_ignoresHeader() throws Exception {
            CasImportResultDto mockResult = mock(CasImportResultDto.class);
            when(importService.stageImport(eq(AUTHENTICATED_INVESTOR), eq("cams.json"), any(InputStream.class))).thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/investor/portfolio/imports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mock\": true}")
                    .header("X-Dev-Subject", ATTACKER_SPOOFED_SUBJECT)
                    .param("filename", "cams.json"))
                .andExpect(status().isOk());

            verify(importService).stageImport(eq(AUTHENTICATED_INVESTOR), eq("cams.json"), any(InputStream.class));
            verify(importService, never()).stageImport(eq(ATTACKER_SPOOFED_SUBJECT), anyString(), any(InputStream.class));
            verify(importService, never()).stageImport(eq("dev-investor-local"), anyString(), any(InputStream.class));
        }

        @Test
        @DisplayName("POST /imports multipart file with spoofed X-Dev-Subject ignores header and uses JWT principal")
        void uploadCasFile_withSpoofedHeader_ignoresHeader() throws Exception {
            MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8));
            CasImportResultDto mockResult = mock(CasImportResultDto.class);
            when(importService.stageImport(eq(AUTHENTICATED_INVESTOR), eq("statement.pdf"), any(InputStream.class), eq(null))).thenReturn(mockResult);

            mockMvc.perform(multipart("/api/v1/investor/portfolio/imports")
                    .file(file)
                    .header("X-Dev-Subject", ATTACKER_SPOOFED_SUBJECT))
                .andExpect(status().isOk());

            verify(importService).stageImport(eq(AUTHENTICATED_INVESTOR), eq("statement.pdf"), any(InputStream.class), eq(null));
            verify(importService, never()).stageImport(eq(ATTACKER_SPOOFED_SUBJECT), anyString(), any(InputStream.class), any());
            verify(importService, never()).stageImport(eq("dev-investor-local"), anyString(), any(InputStream.class), any());
        }
    }

    @Nested
    @DisplayName("4. MockMvc Unauthenticated HTTP Rejection")
    class MockMvcUnauthenticatedTests {

        private MockMvc mockMvc;

        @BeforeEach
        void setupMockMvcNoAuth() {
            // Register AuthenticationPrincipalArgumentResolver so @AuthenticationPrincipal Jwt is properly resolved to null when unauthenticated
            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        }

        @Test
        @DisplayName("Unauthenticated MockMvc call to list imports fails with AccessDeniedException")
        void listImports_noPrincipal_fails() {
            assertThatThrownBy(() ->
                mockMvc.perform(get("/api/v1/investor/portfolio/imports"))
            ).hasRootCauseInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Unauthenticated MockMvc call with dev header still fails (dev fallback disabled in controller)")
        void listImports_withDevHeaderOnly_fails() {
            assertThatThrownBy(() ->
                mockMvc.perform(get("/api/v1/investor/portfolio/imports")
                    .header("X-Dev-Subject", "dev-investor-local"))
            ).hasRootCauseInstanceOf(AccessDeniedException.class);
        }
    }
}
