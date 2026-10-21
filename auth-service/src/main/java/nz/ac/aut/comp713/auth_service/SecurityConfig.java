package nz.ac.aut.comp713.auth_service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class SecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServer(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(server -> {
            http.securityMatcher(server.getEndpointsMatcher());
            server.oidc(oidc -> oidc.logoutEndpoint(logout -> {
                OidcLogoutAuthenticationSuccessHandler handler = new OidcLogoutAuthenticationSuccessHandler();
                SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();
                CookieClearingLogoutHandler cookieLogout = serviceSessionCookies();
                handler.setLogoutHandler((request, response, authentication) -> {
                    if (authentication instanceof OidcLogoutAuthenticationToken oidcToken
                            && oidcToken.isPrincipalAuthenticated()
                            && oidcToken.getPrincipal() instanceof Authentication principal) {
                        sessionLogout.logout(request, response, principal);
                    }
                    cookieLogout.logout(request, response, authentication);
                });
                logout.logoutResponseHandler(handler);
            }));
        });
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain accountPages(HttpSecurity http) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        SavedRequestAwareAuthenticationSuccessHandler oauthSuccess =
                new SavedRequestAwareAuthenticationSuccessHandler();
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/", "/accounts", "/accounts/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            SavedRequest savedRequest = requestCache.getRequest(request, response);
                            if (savedRequest != null && "/oauth2/authorize".equals(
                                    URI.create(savedRequest.getRedirectUrl()).getPath())) {
                                oauthSuccess.onAuthenticationSuccess(request, response, authentication);
                                return;
                            }
                            requestCache.removeRequest(request, response);
                            response.sendRedirect("http://localhost:8082/allocations");
                        }).permitAll())
                .logout(logout -> logout.addLogoutHandler(serviceSessionCookies()));
        return http.build();
    }

    private CookieClearingLogoutHandler serviceSessionCookies() {
        return new CookieClearingLogoutHandler(
                "TARO_AUTH_SESSION", "TARO_CUSTOMER_SESSION", "TARO_TARO_SESSION", "TARO_ALLOCATION_SESSION",
                "TARO_CUSTOMER_XSRF_TOKEN", "TARO_TARO_XSRF_TOKEN", "TARO_ALLOCATION_XSRF_TOKEN", "XSRF-TOKEN");
    }

    @Bean
    UserDetailsService userDetailsService(AccountRepository accounts) {
        return username -> {
            Account account = accounts.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
            return User.withUsername(account.getUsername())
                    .password(account.getPasswordHash())
                    .roles(account.getRole())
                    .disabled(!account.isEnabled())
                    .build();
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    RegisteredClientRepository registeredClients(
            @Value("${auth.service-client-secret}") String serviceSecret,
            @Value("${auth.issuer}") String issuer) {
        if (serviceSecret.length() < 24)
            throw new IllegalStateException("Configure auth.service-client-secret with at least 24 characters");
        ClientSettings publicClient = ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .build();
        TokenSettings tokens = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(15))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .reuseRefreshTokens(false)
                .build();
        RegisteredClient web = RegisteredClient.withId(UUID.nameUUIDFromBytes("taro-web".getBytes()).toString())
                .clientId("taro-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8081/login/oauth2/code/taro-web")
                .redirectUri("http://localhost:8082/login/oauth2/code/taro-web")
                .redirectUri("http://localhost:8083/login/oauth2/code/taro-web")
                .postLogoutRedirectUri(issuer + "/login?logout")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(publicClient)
                .tokenSettings(tokens)
                .build();
        RegisteredClient android = RegisteredClient.withId(UUID.nameUUIDFromBytes("taro-android".getBytes()).toString())
                .clientId("taro-android")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("nz.ac.aut.comp713.tarocartmobile:/oauth2redirect")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(publicClient)
                .tokenSettings(tokens)
                .build();
        RegisteredClient allocationService = RegisteredClient.withId(
                        UUID.nameUUIDFromBytes("allocation-service".getBytes()).toString())
                .clientId("allocation-service")
                .clientSecret("{bcrypt}" + new BCryptPasswordEncoder().encode(serviceSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("references.read")
                .tokenSettings(tokens)
                .build();
        return new InMemoryRegisteredClientRepository(web, android, allocationService);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    RSAKey signingKey(@Value("${auth.signing-key-path}") String keyPath) throws Exception {
        Path path = Path.of(keyPath);
        RSAPrivateCrtKey privateKey;
        if (Files.exists(path)) {
            privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(path)));
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            privateKey = (RSAPrivateCrtKey) keyPair.getPrivate();
            writePrivateKey(path, privateKey);
        }
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return new RSAKey.Builder(publicKey).privateKey(privateKey)
                .keyID(UUID.nameUUIDFromBytes(publicKey.getEncoded()).toString()).build();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(RSAKey signingKey) {
        JWKSet keys = new JWKSet(signingKey);
        return (selector, context) -> selector.select(keys);
    }

    private void writePrivateKey(Path path, RSAPrivateCrtKey privateKey) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(path, privateKey.getEncoded());
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey) throws Exception {
        return NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> roleClaims(AccountRepository accounts) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                    || "id_token".equals(context.getTokenType().getValue())) {
                if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                    context.getClaims().audience(List.of("taro-api"));
                }
                if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                    context.getClaims().claim("roles", List.of("SERVICE"));
                    return;
                }
                Account account = accounts.findByUsernameIgnoreCase(context.getPrincipal().getName())
                        .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
                context.getClaims().claim("sub", account.getId());
                context.getClaims().claim("roles", List.of(account.getRole()));
            }
        };
    }
}
