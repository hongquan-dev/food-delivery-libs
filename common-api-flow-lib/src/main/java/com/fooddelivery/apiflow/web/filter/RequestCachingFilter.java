package com.fooddelivery.apiflow.web.filter;

import com.fooddelivery.apiflow.exception.ApiFlowErrorCode;
import com.fooddelivery.apiflow.web.response.ApiResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestCachingFilter extends OncePerRequestFilter {
    private static final Logger log = LogManager.getLogger(RequestCachingFilter.class);
    private final long maxBodySize;
    private final ApiResponseWriter apiResponseWriter;

    public RequestCachingFilter(long maxBodySize, ApiResponseWriter apiResponseWriter) {
        this.maxBodySize = maxBodySize;
        this.apiResponseWriter = apiResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isJson(request.getContentType());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodySize) {
            rejectTooLarge(request, response);
            return;
        }

        int readLimit = (int) Math.min(maxBodySize + 1, Integer.MAX_VALUE - 8);
        byte[] body = request.getInputStream().readNBytes(readLimit);
        if (body.length > maxBodySize) {
            rejectTooLarge(request, response);
            return;
        }

        chain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private void rejectTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("Request body exceeds {} bytes: [{}] {}", maxBodySize, request.getMethod(), request.getRequestURI());
        ApiFlowErrorCode error = ApiFlowErrorCode.PAYLOAD_TOO_LARGE;
        apiResponseWriter.writeError(request, response, error.getStatus(), error.getCode(), error.getMessage());
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.equalsTypeAndSubtype(mediaType)
                    || ("application".equals(mediaType.getType()) && mediaType.getSubtype().endsWith("+json"));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
