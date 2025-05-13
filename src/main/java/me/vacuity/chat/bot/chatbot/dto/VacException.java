package me.vacuity.chat.bot.chatbot.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @description:
 * @author: vacuity
 * @create: 2023-04-15 10:16
 **/


@Data
public class VacException extends RuntimeException implements Serializable {

    private String code;

    private String msg;

    public VacException(String code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public VacException(String msg) {
        super(msg);
        this.code = "-1";
        this.msg = msg;
    }

    public static VacException init(String code, String msg) {
        return new VacException(code, msg);
    }
}
