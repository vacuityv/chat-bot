package me.vacuity.chat.bot.chatbot.dto;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * @description: res
 * @author: vacuity
 * @create: 2023-04-15 01:49
 **/


@Data
public class VacRes<T> {

    private String code;
    private String msg;
    private String logId;

    private T data;


    public VacRes(String msg) {
        this.code = "-1";
        this.msg = msg;
    }

    public VacRes(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static <T> VacRes<T> success(T data) {
        VacRes res = new VacRes("0", "success");
        res.setData(data);
        return res;
    }

    public static VacRes fail(String msg) {
        VacRes res = new VacRes(msg);
        return res;
    }
    
    public static VacRes fail(String msg, String logId) {
        VacRes res = new VacRes(msg);
        res.setLogId(logId);
        return res;
    }

    public static VacRes fail(String code, String msg, String logId) {
        if (StringUtils.isEmpty(code)) {
            code = "-1";
        }
        VacRes res = new VacRes(code, msg);
        res.setLogId(logId);
        return res;
    }
}
