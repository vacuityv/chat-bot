package me.vacuity.chat.bot.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description:
 * @author: vacuity
 * @create: 2025-02-06 18:15
 **/

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageImg {
    
    private String mimeType;
    
    private String base64String;
}
