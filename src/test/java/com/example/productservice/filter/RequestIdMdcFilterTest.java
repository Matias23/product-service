package com.example.productservice.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdMdcFilterTest {

    private final RequestIdMdcFilter filter = new RequestIdMdcFilter();

    @Test
    void putsRequestIdInMdcDuringChainAndRemovesItAfter() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdMdcFilter.HEADER, "req-123");
        var seenInChain = new AtomicReference<String>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seenInChain.set(MDC.get(RequestIdMdcFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isEqualTo("req-123");
        assertThat(MDC.get(RequestIdMdcFilter.MDC_KEY)).isNull();
    }

    @Test
    void leavesMdcEmptyWhenHeaderMissing() throws Exception {
        var seenInChain = new AtomicReference<String>("not-called");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> seenInChain.set(MDC.get(RequestIdMdcFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isNull();
    }
}
