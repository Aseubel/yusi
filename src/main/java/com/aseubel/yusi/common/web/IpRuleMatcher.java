package com.aseubel.yusi.common.web;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Matches literal IPv4/IPv6 addresses and CIDR rules without DNS lookups. */
public final class IpRuleMatcher {

    private IpRuleMatcher() {
    }

    public static boolean matches(String address, String rule) {
        byte[] addressBytes = parseLiteral(address);
        if (addressBytes == null || rule == null || rule.isBlank()) {
            return false;
        }

        String normalizedRule = rule.trim();
        int slash = normalizedRule.indexOf('/');
        if (slash < 0) {
            byte[] ruleBytes = parseLiteral(normalizedRule);
            return ruleBytes != null && sameBytes(addressBytes, ruleBytes);
        }
        if (slash == 0 || slash == normalizedRule.length() - 1
                || normalizedRule.indexOf('/', slash + 1) >= 0) {
            return false;
        }

        byte[] networkBytes = parseLiteral(normalizedRule.substring(0, slash));
        if (networkBytes == null || networkBytes.length != addressBytes.length) {
            return false;
        }
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(normalizedRule.substring(slash + 1));
        } catch (NumberFormatException exception) {
            return false;
        }
        if (prefixLength < 0 || prefixLength > networkBytes.length * 8) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int index = 0; index < fullBytes; index++) {
            if (addressBytes[index] != networkBytes[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (addressBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    }

    public static boolean isValidRule(String rule) {
        if (rule == null || rule.isBlank()) {
            return false;
        }
        String normalizedRule = rule.trim();
        int slash = normalizedRule.indexOf('/');
        if (slash < 0) {
            return parseLiteral(normalizedRule) != null;
        }
        if (slash == 0 || slash == normalizedRule.length() - 1
                || normalizedRule.indexOf('/', slash + 1) >= 0) {
            return false;
        }
        byte[] networkBytes = parseLiteral(normalizedRule.substring(0, slash));
        if (networkBytes == null) {
            return false;
        }
        try {
            int prefixLength = Integer.parseInt(normalizedRule.substring(slash + 1));
            return prefixLength >= 0 && prefixLength <= networkBytes.length * 8;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static byte[] parseLiteral(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(normalized).getAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static boolean sameBytes(byte[] left, byte[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int index = 0; index < left.length; index++) {
            if (left[index] != right[index]) {
                return false;
            }
        }
        return true;
    }
}
