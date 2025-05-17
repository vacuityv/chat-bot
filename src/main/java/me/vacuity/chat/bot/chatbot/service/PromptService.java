package me.vacuity.chat.bot.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: vacuity
 * @create: 2025-05-15 13:45
 **/

@Service
public class PromptService {

    @Value("${vac.prompt.meng:11}")
    private String meng;
    @Value("${vac.prompt.zixuan:11}")
    private String zixuan;
    @Value("${vac.prompt.dan:11}")
    private String dan;

    @Value("${vac.prompt.group:11}")
    private String group;

    @Value("${vac.prompt.solo:11}")
    private String solo;

    @Value("${vac.help:11}")
    private String help;

    public String getPrompt(String role) {
        String prompt = "";
        switch (role) {
            case "meng":
                prompt = meng;
                break;
            case "zixuan":
                prompt = zixuan;
                break;
            case "dan":
                prompt = dan;
                break;
        }
        return prompt;
    }
    
    public String getGroupPrompt() {
        return group;
    }
    
    public String getSoloPrompt() {
        return solo;
    }
    
    public String getHelp() {
        return help;
    }
}
