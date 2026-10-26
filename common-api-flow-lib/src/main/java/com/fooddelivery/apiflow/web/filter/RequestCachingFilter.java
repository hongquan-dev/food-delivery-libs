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

/**
 * First step of the API flow: caches JSON request bodies ({@code application/json}, {@code application/*+json}) in a
 * {@link CachedBodyHttpServletRequest} so both the API flow and the controller can read them. Other content types
 * pass through untouched. Applies to every path, including excluded ones.
 *
 * <p>Also enforces {@code api-flow.max-body-size} (413 {@code PAYLOAD_TOO_LARGE}), reading at most limit + 1 bytes,
 * so chunked requests without {@code Content-Length} cannot exhaust memory.
 */
public class RequestCachingFilter extends OncePerRequestFilter {
    private static final Logger log = LogManager.getLogger(RequestCachingFilter.class);
    // Limit in bytes, from api-flow.max-body-size
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
        // Fast path: the declared Content-Length is already too large (-1 when unknown)
        if (request.getContentLengthLong() > maxBodySize) {
            rejectTooLarge(request, response);
            return;
        }

        // Read one byte more than allowed: getting it means the body is too large.
        // Capped just below Integer.MAX_VALUE, the largest array size the JVM allows
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

    // application/json (with any parameters such as charset) or a JSON-based type like application/problem+json
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
