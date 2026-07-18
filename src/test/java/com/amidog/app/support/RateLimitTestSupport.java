package com.amidog.app.support;

import com.amidog.app.common.security.RateLimitService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class RateLimitTestSupport {

    private RateLimitTestSupport() {
    }

    public static void reset(RateLimitService rateLimits) {
        ((Map<?, ?>) ReflectionTestUtils.getField(rateLimits, "windows")).clear();
        ((AtomicLong) ReflectionTestUtils.getField(rateLimits, "acquisitions")).set(0);
    }
}
