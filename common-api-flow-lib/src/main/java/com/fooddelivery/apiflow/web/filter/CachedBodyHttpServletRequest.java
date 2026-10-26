package com.fooddelivery.apiflow.web.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Request wrapper, created by {@link RequestCachingFilter}, that keeps the body in memory so it can be read more than
 * once: every {@link #getInputStream()}/{@link #getReader()} call returns a fresh stream over the same bytes, so
 * {@code @RequestBody} still works after the API flow has read the body.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
        super(request);
        this.cachedBody = cachedBody;
    }

    // Body decoded with the request charset (UTF-8 if none is given)
    public String getBodyAsString() {
        return new String(cachedBody, getCharset());
    }

    // Body cached by RequestCachingFilter, "" if the request has no JSON body (the filter skipped it).
    // Works even if other wrappers were added on top of this one
    public static String readCachedBody(HttpServletRequest request) {
        CachedBodyHttpServletRequest cachedRequest = WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest.class);
        return cachedRequest == null ? "" : cachedRequest.getBodyAsString();
    }

    // New blocking stream over the cached bytes on every call. Async (non-blocking) reading is not supported
    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() { return inputStream.available() == 0; }

            @Override
            public boolean isReady() { return true; }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() { return inputStream.read(); }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), getCharset()));
    }

    private Charset getCharset() {
        String encoding = getCharacterEncoding();
        return encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
    }
}
