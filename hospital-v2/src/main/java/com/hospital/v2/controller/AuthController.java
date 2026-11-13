package com.hospital.v2.controller;

import com.hospital.v2.common.BizException;
import com.hospital.v2.common.Result;
import com.hospital.v2.dao.Db;
import com.hospital.v2.security.AuthPrincipal;
import com.hospital.v2.security.TokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Db db;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final boolean cookieSecure;
    private final long cookieMaxAgeSeconds;

    public AuthController(Db db, PasswordEncoder passwords, TokenService tokens,
                          @Value("${hospital.auth.cookie-secure:false}") boolean cookieSecure,
                          @Value("${hospital.auth.token-hours:8}") long tokenHours) {
        this.db = db;
        this.passwords = passwords;
        this.tokens = tokens;
        this.cookieSecure = cookieSecure;
        this.cookieMaxAgeSeconds = Math.max(1, tokenHours) * 3600;
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
        return Result.ok(out);
    }

    @PostMapping("/register")
    @Transactional
    public Result<?> register(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String username = text(body.get("username"));
        String password = text(body.get("password"));
        String name = text(body.get("name"));
        if (!username.matches("[A-Za-z0-9_]{4,32}")) throw new BizException("用户名需为 4-32 位字母、数字或下划线");
        if (password.length() < 8 || password.length() > 72) throw new BizException("密码长度需为 8-72 位");
        if (name.isBlank() || name.length() > 32) throw new BizException("请填写有效姓名");
        if (db.queryOne("SELECT id FROM user_account WHERE username=:u", Map.of("u", username)) != null) {
            throw new BizException("用户名已存在");
        }
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("username", username); u.put("password", passwords.encode(password));
        u.put("real_name", name); u.put("role", "patient"); u.put("phone", text(body.get("phone")));
        long uid = db.insert("INSERT INTO user_account(username,password,real_name,role,phone) "
                + "VALUES(:username,:password,:real_name,:role,:phone)", u);
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("uid", uid); patient.put("name", name); patient.put("gender", text(body.get("gender")));
        patient.put("age", number(body.get("age"), 0));
        db.insert("INSERT INTO patient_profile(user_id,name,gender,age) VALUES(:uid,:name,:gender,:age)", patient);
        AuthPrincipal p = new AuthPrincipal(uid, username, "patient");
        Map<String, Object> out = response(p, name);
        // 注册成功即建立同一 HttpOnly 会话，避免前端只能拿到响应 token 却无法进入受保护页面。
        setSessionCookie(response, String.valueOf(out.get("token")));
        return Result.ok(out);
    }

    @GetMapping("/me")
    public Result<?> me(@AuthenticationPrincipal AuthPrincipal p) { return Result.ok(response(p, p.username())); }

    @PostMapping("/logout")
    public Result<?> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("").maxAge(0).build().toString());
        return Result.ok();
    }

    private void setSessionCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(token).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder sessionCookie(String token) {
        return ResponseCookie.from("hospital_session", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/v2")
                .maxAge(token.isBlank() ? 0 : cookieMaxAgeSeconds);
    }

    private Map<String, Object> response(AuthPrincipal p, String name) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", tokens.issue(p)); out.put("userId", p.userId());
        out.put("username", p.username()); out.put("name", name); out.put("role", p.role());
        return out;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private int number(Object value, int fallback) {
        try { return Integer.parseInt(text(value)); } catch (Exception e) { return fallback; }
    }
}
