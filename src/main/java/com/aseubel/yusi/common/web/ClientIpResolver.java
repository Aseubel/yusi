package com.aseubel.yusi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves client addresses without trusting spoofable forwarding headers by default. */
@Component
public class ClientIpResolver {

    private static final String[] FORWARDED_HEADERS = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    private final Set<String> trustedProxyAddresses;

    public ClientIpResolver(
            @Value("${yusi.web.trusted-proxy-addresses:}") String configuredProxyAddresses) {
        this.trustedProxyAddresses = Arrays.stream(configuredProxyAddresses.split(","))
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String remoteAddress = normalize(request.getRemoteAddr());
        if (remoteAddress == null) {
            return "unknown";
        }

        if (!trustedProxyAddresses.contains(remoteAddress)) {
            return remoteAddress;
        }

        for (String headerName : FORWARDED_HEADERS) {
            String forwardedAddress = firstAddress(request.getHeader(headerName));
            if (forwardedAddress != null) {
                return forwardedAddress;
            }
        }
        return remoteAddress;
    }

    private String firstAddress(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String address = headerValue.split(",", 2)[0].trim();
        if (address.length() > 45 || address.indexOf('\r') >= 0 || address.indexOf('\n') >= 0
                || (address.indexOf('.') < 0 && address.indexOf(':') < 0)
                || !address.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        return address;
    }

    private String normalize(String address) {
        if (address == null || address.isBlank() || "unknown".equalsIgnoreCase(address)) {
            return null;
        }
        return address.trim();
    }
}
