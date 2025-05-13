package me.vacuity.chat.bot.chatbot.service;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-12-09 15:25
 **/


@Service
public class ImgService {

    @Value("${vac.together.key:123456}")
    private String key;
    @Value("${vac.together.model:black-forest-labs/FLUX.1-schnell-Free}")
    private String model;
    
    public static final String URL = "https://api.together.xyz/v1/images/generations";
    
    
    public String gemerateImg(String prompt) {

        JSONObject req = new JSONObject();
        req.put("prompt", prompt);
        req.put("model", model);
        req.put("response_format", "url");
        String res = HttpRequest.post(URL)
                .header("authorization", "Bearer " + key)
                .body(req.toJSONString())
                .execute().body();
        JSONObject resObj = JSON.parseObject(res);
        return resObj.getJSONArray("data").getJSONObject(0).getString("url");
    }

    public static void main(String[] args) {
        String prompt = "A girl in red clothes riding a horse running on the beach, splashing water, with a sunset in the background";
        String akey = "5b74da94875bd3171b8b2d812957dd06271bec8802a1980651ccccb303a47790";
        JSONObject req = new JSONObject();
        req.put("prompt", prompt);
        req.put("model", "black-forest-labs/FLUX.1-schnell-Free");
        req.put("response_format", "url");
        String res = HttpRequest.post(URL)
                .header("authorization", "Bearer " + akey)
                .body(req.toJSONString())
                .execute().body();
        JSONObject resObj = JSON.parseObject(res);
        String imgUrl = resObj.getJSONArray("data").getJSONObject(0).getString("url");
    }
}
