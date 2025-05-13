package me.vacuity.chat.bot.chatbot.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import me.vacuity.chat.bot.chatbot.constant.BotConstant;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * @description:
 * @author: vacuity
 * @create: 2025-01-26 11:23
 **/


@Slf4j
@Service
public class HaiguitangService {

    @Autowired
    private RedisUtil redisUtil;

    @Value("${vac.bot.hgtPath:3000}")
    private String hgtPath;

    private volatile static JSONArray hgtArray = null;

    public JSONArray getHgtContent() {
        if (hgtArray == null) {
            try {
                String content = FileUtils.readFileToString(new File(hgtPath), "UTF-8");
                JSONArray array = JSON.parseArray(content);
                hgtArray = array;
            } catch (IOException e) {
                log.error("read hgt file error", e);
            }
        }
        return hgtArray;
    }

    public JSONObject getHaiguitangItem() {
        JSONArray array = getHgtContent();
        Integer index = (int) (Math.random() * array.size());
        return array.getJSONObject(index);
    }

    public String getHaiguitangInfoStr(String key) {
        JSONObject info = getHaiguitangItem();
        String content = BotConstant.HAIGUITANG;
        content = content.replace("#TITLE", info.getString("title"))
                .replace("#CONTENT", info.getString("surface"))
                .replace("#ANSWER", info.getString("bottom"));

        redisUtil.set(BotConstant.PROMPT_CACHE + key, content);
        redisUtil.set(BotConstant.ROLE_CACHE + key, "hgt");
        String res = "题目：" + info.getString("title") + "\n汤面：" + info.getString("surface");
        return res;
    }

    public void checkHgtAnswer(String key, String answer) {
        if (redisUtil.hasKey(BotConstant.ROLE_CACHE + key)
                && "hgt".equals(redisUtil.get(BotConstant.ROLE_CACHE + key).toString())
                && StringUtils.isNotBlank(answer) && answer.contains("结束游戏")) {
            redisUtil.delete(BotConstant.ROLE_CACHE + key);
            redisUtil.delete(BotConstant.PROMPT_CACHE + key);
        }
    }
}
