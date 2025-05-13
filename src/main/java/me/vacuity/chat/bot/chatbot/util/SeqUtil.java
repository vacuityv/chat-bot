package me.vacuity.chat.bot.chatbot.util;

/**
 * @description: 序列生成
 * @author: vacuity
 * @create: 2021-03-24 17:15
 **/

public class SeqUtil {

    private static final Sequence seq = new Sequence();

    public static long getNextId() {
        return seq.nextId();
    }

    public static String getNextStringId() {
        return seq.nextId() + "";
    }
}
