package com.edgar.springlab.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.charset.StandardCharsets;

@RestController
public class StreamDemoController {

    @GetMapping(value = "/io/demo",
            produces = "text/plain;charset=UTF-8")
    public String demo() throws IOException {

        // 模拟服务器返回的一段文本
        String text = "Hello\n你好\nSpring";

        // 将字符串编码成 UTF-8 字节
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        // 英文字符编码成一个字节，中文字符编码成三个字节

        StringBuilder result = new StringBuilder();

        // 实验1：InputStream
        result.append("=== InputStream ===\n");

        try (InputStream inputStream =
                     new ByteArrayInputStream(bytes)) {

            int data;

            while ((data = inputStream.read()) != -1) {
                result.append(data).append(" ");
            }
        }

        // 实验2：InputStreamReader
        result.append("\n\n=== InputStreamReader ===\n");

        try (InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8)) {

            int data;

            while ((data = reader.read()) != -1) {
                result.append((char) data);
            }
        }

        // 实验3：BufferedReader
        result.append("\n\n=== BufferedReader ===\n");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new ByteArrayInputStream(bytes),
                        StandardCharsets.UTF_8))) {

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                result.append("第")
                        .append(lineNumber++)
                        .append("行：")
                        .append(line)
                        .append("\n");
            }
        }

        return result.toString();
    }
}
