package com.hospital.v2.controller;

import com.hospital.v2.common.BizException;
import com.hospital.v2.common.Result;
import com.hospital.v2.dao.Db;
import com.hospital.v2.security.AuthPrincipal;
import com.hospital.v2.security.TokenService;
import com.hospital.v2.security.SensitiveDataCrypto;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DuplicateKeyException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Db db;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final SensitiveDataCrypto crypto;
    private final boolean cookieSecure;
    private final long cookieMaxAgeSeconds;
    private final long refreshCookieMaxAgeSeconds;

    public AuthController(Db db, PasswordEncoder passwords, TokenService tokens, SensitiveDataCrypto crypto,
                          @Value("${hospital.auth.cookie-secure:false}") boolean cookieSecure,
                          @Value("${hospital.auth.token-hours:8}") long tokenHours,
                          @Value("${hospital.auth.refresh-days:30}") long refreshDays) {
        this.db = db;
        this.passwords = passwords;
        this.tokens = tokens;
        this.crypto = crypto;
        this.cookieSecure = cookieSecure;
        this.cookieMaxAgeSeconds = Math.max(1, tokenHours) * 3600;
        this.refreshCookieMaxAgeSeconds = Math.max(1, refreshDays) * 86400;
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String username = text(body.get("username"));
        String password = text(body.get("password"));
        Map<String, Object> row = db.queryOne(
                "SELECT id,username,password,real_name,role FROM user_account WHERE username=:u",
                Map.of("u", username));
        if (row == null || !passwords.matches(password, String.valueOf(row.get("password")))) {
            throw new BizException("用户名或密码错误");
        }
        AuthPrincipal p = new AuthPrincipal(((Number) row.get("id")).longValue(), username,
                String.valueOf(row.get("role")));
        Map<String, Object> out = response(p, String.valueOf(row.get("real_name")));
        setSessionCookie(response, String.valueOf(out.get("token")));
        setRefreshCookie(response, tokens.issueRefresh(p));
        return Result.ok(out);
    }

    @PostMapping("/register")
    @Transactional
    public Result<?> register(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String username = text(body.get("username"));
        String password = text(body.get("password"));
        String name = text(body.get("name"));
        if (!username.matches("[A-Za-z0-9_]{4,32}")) throw new BizException("用户名需为 4-32 位字母、数字或下划线");
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 8 || password.length() > 72 || passwordBytes > 72) {
            throw new BizException("密码需为 8-72 个字符且 UTF-8 字节数不超过 72");
        }
        if (name.isBlank() || name.length() > 32) throw new BizException("请填写有效姓名");
        if (db.queryOne("SELECT id FROM user_account WHERE username=:u", Map.of("u", username)) != null) {
            throw new BizException("用户名已存在");
        }
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("username", username); u.put("password", passwords.encode(password));
        String phone = text(body.get("phone"));
        String idCard = text(body.get("id_card"));
        u.put("real_name", name); u.put("role", "patient"); u.put("phone", crypto.encrypt(phone));
        u.put("id_card", crypto.encrypt(idCard));
        long uid;
        try {
            uid = db.insert("INSERT INTO user_account(username,password,real_name,role,phone,id_card) "
                    + "VALUES(:username,:password,:real_name,:role,:phone,:id_card)", u);
            Map<String, Object> patient = new LinkedHashMap<>();
            patient.put("uid", uid); patient.put("name", name); patient.put("gender", text(body.get("gender")));
            patient.put("phone", crypto.encrypt(phone));
            patient.put("id_card", crypto.encrypt(idCard));
            patient.put("id_card_hash", crypto.sha256(idCard));
            Integer age = optionalInteger(body.get("age"));
            if (age != null && (age < 0 || age > 130)) throw new BizException("年龄需为 0-130 的整数");
            patient.put("age", age);
            db.insert("INSERT INTO patient_profile(user_id,name,gender,age,phone,id_card,id_card_hash) "
                    + "VALUES(:uid,:name,:gender,:age,:phone,:id_card,:id_card_hash)", patient);
        } catch (DuplicateKeyException e) {
            throw new BizException("用户名已存在");
        }
        AuthPrincipal p = new AuthPrincipal(uid, username, "patient");
        Map<String, Object> out = response(p, name);
        // 注册成功即建立同一 HttpOnly 会话，避免前端只能拿到响应 token 却无法进入受保护页面。
        setSessionCookie(response, String.valueOf(out.get("token")));
        setRefreshCookie(response, tokens.issueRefresh(p));
        return Result.ok(out);
    }

    @GetMapping("/me")
    public Result<?> me(@AuthenticationPrincipal AuthPrincipal p) { return Result.ok(response(p, p.username())); }

    @PostMapping("/logout")
    public Result<?> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("").maxAge(0).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("").maxAge(0).build().toString());
        return Result.ok();
    }

    /** 用 HttpOnly refresh cookie 换取短时 access cookie，前端不接触 refresh token。 */
    @PostMapping("/refresh")
    public Result<?> refresh(@CookieValue(value = "hospital_refresh", required = false) String refreshToken,
                             HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) throw new BizException(401, "刷新令牌不存在或已失效");
        AuthPrincipal p;
        try { p = tokens.verifyRefresh(refreshToken); }
        catch (IllegalArgumentException e) { throw new BizException(401, "刷新令牌不存在或已失效"); }
        Map<String, Object> out = response(p, p.username());
        setSessionCookie(response, String.valueOf(out.get("token")));
        return Result.ok(Map.of("token", out.get("token"), "expiresIn", cookieMaxAgeSeconds));
    }

    private void setSessionCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(token).build().toString());
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(token).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder sessionCookie(String token) {
        return ResponseCookie.from("hospital_session", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/v2")
                .maxAge(token.isBlank() ? 0 : cookieMaxAgeSeconds);
    }

    private ResponseCookie.ResponseCookieBuilder refreshCookie(String token) {
        return ResponseCookie.from("hospital_refresh", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/v2/api/auth")
                .maxAge(token.isBlank() ? 0 : refreshCookieMaxAgeSeconds);
    }

    private Map<String, Object> response(AuthPrincipal p, String name) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", tokens.issue(p)); out.put("userId", p.userId());
        out.put("username", p.username()); out.put("name", name); out.put("role", p.role());
        out.put("expiresIn", cookieMaxAgeSeconds);
        return out;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Integer optionalInteger(Object value) {
        if (value == null || text(value).isBlank()) return null;
        try { return Integer.valueOf(text(value)); }
        catch (Exception e) { throw new BizException("年龄必须是 0-130 的整数"); }
    }
}
