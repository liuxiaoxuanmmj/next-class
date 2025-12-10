package edu.zzttc.backend.controller;

import edu.zzttc.backend.domain.entity.RestBean;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiTestController {

    private final ChatClient chatClient;

    public AiTestController(ChatClient.Builder builder) {
        // Spring AI 会自动注入带 DashScope 模型的 builder
        this.chatClient = builder.build();
    }

    @GetMapping("/api/ai/test")
    public RestBean<String> test(@RequestParam String q) {
        String answer = chatClient
                .prompt()
                .user(q)
                .call()
                .content();

        return RestBean.success(answer);
    }
}
