package me.vacuity.chat.bot.chatbot.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @description:
 * @author: vacuity
 * @create: 2025-02-10 13:34
 **/

@Data
@Builder
public class ModelAnswer {

    private String result;
    
    private String answer;

    private boolean success;
}
