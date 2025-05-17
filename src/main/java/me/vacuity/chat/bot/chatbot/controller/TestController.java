package me.vacuity.chat.bot.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import me.vacuity.chat.bot.chatbot.service.PromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @description:
 * @author: vacuity
 * @create: 2025-05-15 14:16
 **/


@Slf4j
@Controller
@RequestMapping("/test")
public class TestController {
    
    @Autowired
    private PromptService promptService;
    
    @RequestMapping("/prompt/")
    @ResponseBody
    public String test() {
        return promptService.getPrompt("meng");
    }
}
