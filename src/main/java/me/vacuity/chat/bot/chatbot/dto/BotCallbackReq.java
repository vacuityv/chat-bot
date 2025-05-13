package me.vacuity.chat.bot.chatbot.dto;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-12-06 08:34
 **/


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BotCallbackReq {
    
    private String TypeName;
    
    private String Appid;
    
    private String Wxid;
    
    private JSONObject Data;
}
