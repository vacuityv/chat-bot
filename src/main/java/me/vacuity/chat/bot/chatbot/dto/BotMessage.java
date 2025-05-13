package me.vacuity.chat.bot.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-12-06 12:51
 **/


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BotMessage {

    private String role;
    
    private String nickName;
    
    private String content;
    
    private String type;
    
    private MessageImg img;
    
    private long timestamp;
}
