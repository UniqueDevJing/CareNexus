package com.hospital.v2.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 轻量 HMAC-SHA256 JWT，实现无状态登录，不把密钥或隐私字段写入令牌。 */
@Service
public class TokenService {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private final ObjectMapper json;
    private final byte[] secret;
    private final long tokenSeconds;
    private final long refreshSeconds;

    @Autowired
    public TokenService(ObjectMapper json,
                        @Value("${hospital.auth.jwt-secret}") String secret,
                        @Value("${hospital.auth.token-hours:8}") long tokenHours,
                        @Value("${hospital.auth.refresh-days:30}") long refreshDays,
                        @Value("${spring.profiles.active:}") String activeProfile) {
        boolean productionProfile = activeProfile != null && activeProfile.toLowerCase(java.util.Locale.ROOT).contains("mysql");
        boolean placeholder = secret == null || secret.toLowerCase(java.util.Locale.ROOT).matches(".*(change[-_]?me|replace[-_]?with|local[-_]?only|default).*" );
        if (secret == null || secret.length() < 32 || (productionProfile && placeholder)) {
            throw new IllegalStateException("hospital.auth.jwt-secret 至少需要 32 个字符");
        }
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenSeconds = Math.max(1, tokenHours) * 3600;
        this.refreshSeconds = Math.max(1, refreshDays) * 86400;
    }

    /** Kept for focused unit tests and integrations that construct the service directly. */
    public TokenService(ObjectMapper json, String secret, long tokenHours, String activeProfile) {
        this(json, secret, tokenHours, 30, activeProfile);
    }

    public String issue(AuthPrincipal p) {
        try {
            String header = enc(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", p.username());
            claims.put("uid", p.userId());
            claims.put("role", p.role());
            claims.put("typ", "access");
            claims.put("iat", Instant.now().getEpochSecond());
            claims.put("exp", Instant.now().getEpochSecond() + tokenSeconds);
            String payload = enc(json.writeValueAsBytes(claims));
            String unsigned = header + "." + payload;
            return unsigned + "." + enc(sign(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("令牌生成失败", e);
        }
    }

    public String issueRefresh(AuthPrincipal p) {
        return issue(p, "refresh", refreshSeconds);
    }

    public AuthPrincipal verifyRefresh(String token) {
        return verify(token, "refresh");
    }

    public AuthPrincipal verify(String token) {
        return verify(token, null);
    }

    private AuthPrincipal verify(String token, String expectedType) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("令牌格式错误");
            String unsigned = parts[0] + "." + parts[1];
            if (!java.security.MessageDigest.isEqual(sign(unsigned), B64D.decode(parts[2]))) {
                throw new IllegalArgumentException("令牌签名无效");
            }
            Map<String, Object> c = json.readValue(B64D.decode(parts[1]), new TypeReference<>() {});
            String type = c.get("typ") == null ? "access" : String.valueOf(c.get("typ"));
            if (expectedType != null && !expectedType.equals(type)) throw new IllegalArgumentException("令牌类型错误");
            if (expectedType == null && !"access".equals(type)) throw new IllegalArgumentException("访问令牌类型错误");
            long exp = ((Number) c.get("exp")).longValue();
            if (exp <= Instant.now().getEpochSecond()) throw new IllegalArgumentException("令牌已过期");
            return new AuthPrincipal(((Number) c.get("uid")).longValue(),
                    String.valueOf(c.get("sub")), String.valueOf(c.get("role")));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("令牌无效", e);
        }
    }

    private String issue(AuthPrincipal p, String type, long lifetimeSeconds) {
        try {
            String header = enc(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            long now = Instant.now().getEpochSecond();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", p.username());
            claims.put("uid", p.userId());
            claims.put("role", p.role());
            claims.put("typ", type);
            claims.put("iat", now);
            claims.put("exp", now + lifetimeSeconds);
            String payload = enc(json.writeValueAsBytes(claims));
            String unsigned = header + "." + payload;
            return unsigned + "." + enc(sign(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("令牌生成失败", e);
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private String enc(byte[] value) { return B64.encodeToString(value); }
}
