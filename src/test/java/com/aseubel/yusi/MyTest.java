package com.aseubel.yusi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class MyTest {
    @Test
    public void testCompressUtils() {
        String text = "<user_input>你好，世界</user_input>";
        Pattern pattern = Pattern.compile("<user_input>(.+?)</user_input>");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1);
        }
        assertEquals("你好，世界", text);
    }
}
