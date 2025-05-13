package me.vacuity.chat.bot.chatbot.dto;

import lombok.Data;

/**
 * @description:
 * @author: vacuity
 * @create: 2023-04-15 01:48
 **/


@Data
public class VacReq<T> {

    private String sessionId;

    private T data;
}
