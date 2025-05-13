package me.vacuity.chat.bot.chatbot.service;

import me.vacuity.ai.sdk.openai.entity.ChatFunction;
import me.vacuity.ai.sdk.openai.service.FunctionExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-09-11 17:38
 **/


@Service
public class ChatFuncService {
    
    @Autowired
    private GoogleSearchService googleSearchService;
    
    public FunctionExecutor getChatgptFuncExecutor() {
        FunctionExecutor functionExecutor = new FunctionExecutor(Collections.singletonList(ChatFunction.builder()
                .name("search_result_from_google")
                .description("search the result from google")
                .executor(GoogleSearchRequest.class, w -> googleSearchService.search(w.getQuery()))
                .build()));
        return functionExecutor;
    }

    public me.vacuity.ai.sdk.claude.service.FunctionExecutor getClaudeFuncExecutor() {
        me.vacuity.ai.sdk.claude.service.FunctionExecutor functionExecutor = new me.vacuity.ai.sdk.claude.service.FunctionExecutor(Collections.singletonList(me. vacuity. ai. sdk. claude. entity. ChatFunction.builder()
                .name("search_result_from_google")
                .description("search the result from google")
                .executor(GoogleSearchRequest.class, w -> googleSearchService.search(w.getQuery()))
                .build()));
        return functionExecutor;
    }

    // 内部类用于定义搜索请求的结构
    public static class GoogleSearchRequest {
        
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
    
    
}
