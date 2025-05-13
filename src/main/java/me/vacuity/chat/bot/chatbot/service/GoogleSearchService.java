package me.vacuity.chat.bot.chatbot.service;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-09-11 17:24
 **/


@Slf4j
@Service
public class GoogleSearchService {

    @Value("${vac.google.searchKey:123456}")
    private String apiKey;
    @Value("${vac.google.searchCx:123456}")
    private String cx;

    public static final String SEARCH_URL = "https://www.googleapis.com/customsearch/v1?key=#API_KEY&cx=#CX&q=#QUERY";
    public static final String SEARCH_URL_PROXY = "https://chat.vacuity.me/googleapis/customsearch/v1?key=#API_KEY&cx=#CX&q=#QUERY";

    public JSONArray search(String query) {
        log.info("search query: {}", query);
        String baseUrl = SEARCH_URL_PROXY;
        String url = baseUrl.replace("#API_KEY", apiKey).replace("#CX", cx).replace("#QUERY", query);
        String fields = "kind,items(title,link,displayLink,snippet)";
        url = url + "&fields=" + fields;
        String res = HttpUtil.get(url);
        JSONObject resObj = JSON.parseObject(res);
        return resObj.getJSONArray("items");
    }
}
