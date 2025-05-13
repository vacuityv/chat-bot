package me.vacuity.chat.bot.chatbot.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @description:
 * @author: vacuity
 * @create: 2025-01-16 17:28
 **/

@Data
@Builder
public class PromptResult {

    private String cleanText;
    
    private List<String> prompts;

}
