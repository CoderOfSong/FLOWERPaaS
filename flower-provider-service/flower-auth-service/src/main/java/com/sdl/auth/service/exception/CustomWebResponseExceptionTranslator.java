package com.sdl.auth.service.exception;

import com.sdl.common.base.enums.ResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.DefaultThrowableAnalyzer;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InsufficientScopeException;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.security.oauth2.provider.error.WebResponseExceptionTranslator;
import org.springframework.security.web.util.ThrowableAnalyzer;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/**
 * @program flowerPaaS
 * @description: 自定义异常翻译
 * @author: songdeling
 * @create: 2019/12/24 10:55
 */
@Slf4j
public class CustomWebResponseExceptionTranslator implements WebResponseExceptionTranslator {

    private ThrowableAnalyzer throwableAnalyzer = new DefaultThrowableAnalyzer();

    @Override
    public ResponseEntity<CustomOAuth2Exception> translate(Exception e) throws Exception {

        log.error(e.getMessage());

        // Try to extract a SpringSecurityException from the stacktrace
        Throwable[] causeChain = throwableAnalyzer.determineCauseChain(e);

        // 异常栈获取 CustomOAuth2Exception 异常
        Exception ase = (CustomOAuth2Exception) throwableAnalyzer.getFirstThrowableOfType(CustomOAuth2Exception.class, causeChain);
        if (ase != null) {
            return handleOAuth2Exception((CustomOAuth2Exception) ase);
        }
        ase = (AuthenticationException) throwableAnalyzer.getFirstThrowableOfType(AuthenticationException.class, causeChain);
        if (ase != null) {
            return handleOAuth2Exception(new CustomOAuth2Exception(ResponseStatus.UNAUTHORIZED.code(), ase.getMessage()));
        }
        ase = (AccessDeniedException) throwableAnalyzer.getFirstThrowableOfType(AccessDeniedException.class, causeChain);
        if (ase instanceof AccessDeniedException) {
            return handleOAuth2Exception(new CustomOAuth2Exception(ResponseStatus.FORBIDDEN.code(), "权限不足无法访问"));
        }
        ase = (InvalidTokenException) throwableAnalyzer.getFirstThrowableOfType(InvalidTokenException.class, causeChain);
        if (ase instanceof InvalidTokenException) {
            return handleOAuth2Exception(new CustomOAuth2Exception(ResponseStatus.UNAUTHORIZED.code(), "无效的 access_token"));
        }
        ase = (InvalidGrantException) throwableAnalyzer.getFirstThrowableOfType(InvalidGrantException.class, causeChain);
        if (ase instanceof InvalidGrantException) {
            String msg = ase.getMessage();
            if (msg.contains("User is disabled")) {
                msg = "用户被禁用";
            }
            if (msg.contains("User account has expired")) {
                msg = "用户帐户已过期";
            }
            if (msg.contains("User credentials have expired")) {
                msg = "用户凭据已过期";
            }
            if (msg.contains("User account is locked")) {
                msg = "用户帐户已锁定";
            }
            if (msg.contains("Invalid refresh token")) {
                msg = "无效的 refresh_token";
            }
            return handleOAuth2Exception(new CustomOAuth2Exception(ResponseStatus.UNAUTHORIZED.code(), msg));
        }
        ase = (HttpRequestMethodNotSupportedException) throwableAnalyzer.getFirstThrowableOfType(HttpRequestMethodNotSupportedException.class, causeChain);
        if (ase instanceof HttpRequestMethodNotSupportedException) {
            return handleOAuth2Exception(new CustomOAuth2Exception(ResponseStatus.METHOD_NOT_ALLOWED.code(), e.getMessage()));
        }
        return handleOAuth2Exception(new CustomOAuth2Exception(ResponseStatus.FAILURE.code(), e.getMessage()));
    }

    private ResponseEntity<CustomOAuth2Exception> handleOAuth2Exception(OAuth2Exception e) {
        int code = e.getHttpErrorCode();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cache-Control", "no-store");
        headers.set("Pragma", "no-cache");
        if (code == ResponseStatus.UNAUTHORIZED.code() || (e instanceof InsufficientScopeException)) {
            headers.set("WWW-Authenticate", String.format("%s %s", OAuth2AccessToken.BEARER_TYPE, e.getSummary()));
        }
        ResponseEntity<CustomOAuth2Exception> response = new ResponseEntity(e, headers, HttpStatus.valueOf(code));
        return response;
    }

}
