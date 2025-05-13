package me.vacuity.chat.bot.chatbot.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-12-13 16:40
 **/


@Data
@Builder
public class BotProcessRes {
    
    private boolean success;

    private String data;
}
