package me.vacuity.chat.bot.chatbot.service;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import me.vacuity.ai.sdk.claude.ClaudeClient;
import me.vacuity.ai.sdk.claude.entity.ChatFunction;
import me.vacuity.ai.sdk.claude.entity.ChatMessageContent;
import me.vacuity.ai.sdk.claude.error.ChatResponseError;
import me.vacuity.ai.sdk.common.VacSdkException;
import me.vacuity.ai.sdk.openai.OpenaiClient;
import me.vacuity.ai.sdk.openai.entity.ChatFunctionCall;
import me.vacuity.ai.sdk.openai.entity.ChatMessage;
import me.vacuity.ai.sdk.openai.entity.ChatTool;
import me.vacuity.ai.sdk.openai.request.ChatRequest;
import me.vacuity.ai.sdk.openai.response.ChatResponse;
import me.vacuity.ai.sdk.openai.response.ChatResponseMessage;
import me.vacuity.ai.sdk.openai.service.FunctionExecutor;
import me.vacuity.chat.bot.chatbot.constant.ApiModeConstant;
import me.vacuity.chat.bot.chatbot.constant.BotConstant;
import me.vacuity.chat.bot.chatbot.dto.BotMessage;
import me.vacuity.chat.bot.chatbot.dto.MessageImg;
import me.vacuity.chat.bot.chatbot.dto.ModelAnswer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-12-06 13:02
 **/

@Slf4j
@Service
public class BotChatService {

    
    @Autowired
    private RedisUtil redisUtil;

    @Value("${vac.bot.openaiKey:3000}")
    private String openaiKey;
    @Value("${vac.bot.claudeKey:3000}")
    private String claudeKey;
    
    @Value("${vac.bot.deepseekKey:3000}")
    private String deepseekKey;
    @Value("${vac.bot.grokKey:3000}")
    private String grokKey;

    private volatile static OpenaiClient client;
    
    private volatile static OpenaiClient deepseekClient;
    
    private volatile static OpenaiClient grokClient;

    private volatile static ClaudeClient claudeClient;
    


    public OpenaiClient getClient() {
        if (client == null) {
            synchronized (OpenaiClient.class) {
                if (client == null) {
                    OpenaiClient clientBak = new OpenaiClient(openaiKey, Duration.ofSeconds(120), "https://chat.vacuity.me/openai/");
                    client = clientBak;
                }
            }
        }
        return client;
    }

    public OpenaiClient getDeepseekClient() {
        if (deepseekClient == null) {
            synchronized (OpenaiClient.class) {
                if (deepseekClient == null) {
                    OpenaiClient clientBak = new OpenaiClient(deepseekKey, Duration.ofSeconds(120), "https://api.siliconflow.cn/");
                    deepseekClient = clientBak;
                }
            }
        }
        return deepseekClient;
    }

    public OpenaiClient getGrokClient() {
        if (grokClient == null) {
            synchronized (OpenaiClient.class) {
                if (grokClient == null) {
                    OpenaiClient clientBak = new OpenaiClient(grokKey, Duration.ofSeconds(120), "https://chat.vacuity.me/xai/");
                    grokClient = clientBak;
                }
            }
        }
        return grokClient;
    }
    

    public ClaudeClient getClaudeClient() {
        if (claudeClient == null) {
            synchronized (ClaudeClient.class) {
                if (claudeClient == null) {
                    ClaudeClient clientBak = new ClaudeClient(claudeKey, Duration.ofSeconds(120), "https://chat.vacuity.me/claude/");
                    claudeClient = clientBak;
                }
            }
        }
        return claudeClient;
    }
    

    public ModelAnswer groupChat(String model, String prompt, List<BotMessage> botMessages) {
        int retry = 0;
        ModelAnswer answer = null;
        while (retry < 3) {
            answer = innerGroupChat(model, prompt, botMessages);
            if (answer.isSuccess()) {
                return answer;
            }
            retry++;
        }
        return answer;
    }

    private ModelAnswer innerGroupChat(String model, String prompt, List<BotMessage> botMessages) {
        log.info("model:{}", model);
        try {
            if (model.startsWith("gpt") || model.contains("grok")) {
                return groupChatOpenai(model, prompt, botMessages);
            } else if (model.contains("deepseek")) {
                return groupChatDeepseek(model, prompt, botMessages);
            } else {
                return groupChatClaude(model, prompt, botMessages, true);
            }
        } catch (Exception e) {
            log.error("chat error", e);
            return ModelAnswer.builder()
                    .success(false)
                    .answer("系统错误，请重试")
                    .result("系统错误，请重试")
                    .build();
        }
    }

    public ModelAnswer soloChat(String model, String prompt, List<BotMessage> botMessages) {
        int retry = 0;
        ModelAnswer answer = null;
        while (retry < 3) {
            answer = innerSoloChat(model, prompt, botMessages);
            if (answer.isSuccess()) {
                return answer;
            }
            retry++;
        }
        return answer;
    }

    private ModelAnswer innerSoloChat(String model, String prompt, List<BotMessage> botMessages) {
        log.info("model:{}", model);
        try {
            if (model.startsWith("gpt") || model.contains("grok")) {
                return soloOpenaiChat(model, prompt, botMessages);
            } else if (model.contains("deepseek")) {
                return soloDeepseekChat(model, prompt, botMessages);
            } else {
                return soloClaudeChat(model, prompt, botMessages, true);
            }
        } catch (Exception e) {
            log.error("chat error", e);
            return ModelAnswer.builder()
                    .success(false)
                    .answer("系统错误，请重试")
                    .result("系统错误，请重试")
                    .build();
        }
    }

    public ModelAnswer soloClaudeWithoutFunction(String model, String prompt, List<BotMessage> botMessages) {
        int retry = 0;
        ModelAnswer answer = null;
        while (retry < 3) {
            answer = innerSoloClaudeWithoutFunction(model, prompt, botMessages);
            if (answer.isSuccess()) {
                return answer;
            }
            retry++;
        }
        return answer;
    }

    private ModelAnswer innerSoloClaudeWithoutFunction(String model, String prompt, List<BotMessage> botMessages) {
        log.info("model:{}", model);
        try {
            return soloClaudeChat(model, prompt, botMessages, false);
        } catch (Exception e) {
            log.error("chat error", e);
            return ModelAnswer.builder()
                    .success(false)
                    .answer("系统错误，请重试")
                    .result("系统错误，请重试")
                    .build();
        }
    }
    

    private ModelAnswer groupChatOpenai(String model, String prompt, List<BotMessage> botMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", prompt));
        String last = "assistant";
        Iterator<BotMessage> iterator = botMessages.iterator();
        while (iterator.hasNext()) {
            BotMessage item = iterator.next();
            if (last.equals(item.getRole())) {
                iterator.remove();
            } else {
                break;
            }
        }
        for (BotMessage message : botMessages) {
            String nickNameStr = message.getNickName() + ":";
            if (message.getRole().equals("assistant")) {
                nickNameStr = "";
            }
            ChatMessage item = null;
            if (message.getRole().equals(last)) {
                item = messages.get(messages.size() - 1);
                List<me.vacuity.ai.sdk.openai.entity.ChatMessageContent> contentList = (List<me.vacuity.ai.sdk.openai.entity.ChatMessageContent>) item.getContent();

                if (StringUtils.isEmpty(message.getType()) || BotConstant.LOCAL_MESSAGE_TYPE_TXT.equals(message.getType())) {
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent content = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent();
                    content.setType("text");
                    content.setText(nickNameStr + message.getContent());
                    contentList.add(content);
                } else {
                    MessageImg img = message.getImg();
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent.ImageUrl imageUrl = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent.ImageUrl();
                    String base64Image = "data:" + img.getMimeType() + ";base64," + img.getBase64String();
                    imageUrl.setUrl(base64Image);
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent fileContent = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent();
                    fileContent.setType("image_url");
                    fileContent.setImageUrl(imageUrl);
                    contentList.add(fileContent);
                }
                item.setContent(contentList);
                messages.set(messages.size() - 1, item);
            } else {
                last = message.getRole();

                List<me.vacuity.ai.sdk.openai.entity.ChatMessageContent> contentList = new ArrayList<>();
                if (StringUtils.isEmpty(message.getType()) || BotConstant.LOCAL_MESSAGE_TYPE_TXT.equals(message.getType())) {
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent content = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent();
                    content.setType("text");
                    content.setText(nickNameStr + message.getContent());
                    contentList.add(content);
                } else {
                    MessageImg img = message.getImg();
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent.ImageUrl imageUrl = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent.ImageUrl();
                    String base64Image = "data:" + img.getMimeType() + ";base64," + img.getBase64String();
                    imageUrl.setUrl(base64Image);
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent fileContent = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent();
                    fileContent.setType("image_url");
                    fileContent.setImageUrl(imageUrl);
                    contentList.add(fileContent);
                }
                item = ChatMessage.builder().role(last).content(contentList).build();
                messages.add(item);
            }
        }
        return processOpenai(model, messages);
    }

    private ModelAnswer groupChatDeepseek(String model, String prompt, List<BotMessage> botMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", prompt));
        String last = "assistant";
        Iterator<BotMessage> iterator = botMessages.iterator();
        while (iterator.hasNext()) {
            BotMessage item = iterator.next();
            if (last.equals(item.getRole())) {
                iterator.remove();
            } else {
                break;
            }
        }
        for (BotMessage message : botMessages) {
            if (BotConstant.LOCAL_MESSAGE_TYPE_IMG.equals(message.getType())){
                continue;
            }
            String nickNameStr = message.getNickName() + ":";
            if (message.getRole().equals("assistant")) {
                nickNameStr = "";
            }
            ChatMessage item = null;
            if (message.getRole().equals(last)) {
                item = messages.get(messages.size() - 1);
                item.setContent(item.getContent() + "\n" + nickNameStr + message.getContent());
                messages.set(messages.size() - 1, item);
            } else {
                last = message.getRole();
                item = ChatMessage.builder().role(last).content(nickNameStr + message.getContent()).build();
                messages.add(item);

            }
        }
        return processOpenai(model, messages);
    }

    private ModelAnswer groupChatClaude(String model, String prompt, List<BotMessage> botMessages, boolean funcFlag) {
        List<me.vacuity.ai.sdk.claude.entity.ChatMessage> messages = new ArrayList<>();
        String last = "assistant";
        Iterator<BotMessage> iterator = botMessages.iterator();
        while (iterator.hasNext()) {
            BotMessage item = iterator.next();
            if (last.equals(item.getRole())) {
                iterator.remove();
            } else {
                break;
            }
        }
        for (BotMessage message : botMessages) {
            me.vacuity.ai.sdk.claude.entity.ChatMessage item = null;
            String nickNameStr = message.getNickName() + ":";
            if (message.getRole().equals("assistant")) {
                nickNameStr = "";
            }
            if (message.getRole().equals(last)) {
                item = messages.get(messages.size() - 1);
                List<ChatMessageContent> contents = (List<ChatMessageContent>) item.getContent();
                if (StringUtils.isEmpty(message.getType()) || BotConstant.LOCAL_MESSAGE_TYPE_TXT.equals(message.getType())) {
                    ChatMessageContent content = ChatMessageContent.builder()
                            .type("text")
                            .text(nickNameStr + message.getContent())
                            .build();
                    contents.add(content);
                } else {
                    MessageImg img = message.getImg();
                    String type = "image";
                    ChatMessageContent.ContentSource contentSource = new ChatMessageContent.ContentSource();
                    contentSource.setType("base64");
                    contentSource.setMediaType(img.getMimeType());
                    contentSource.setData(img.getBase64String());
                    contents.add(ChatMessageContent.builder().type(type).source(contentSource).build());
                }
                item.setContent(contents);
                messages.set(messages.size() - 1, item);
            } else {
                last = message.getRole();

                List<ChatMessageContent> contents = new ArrayList<>();
                if (StringUtils.isEmpty(message.getType()) || BotConstant.LOCAL_MESSAGE_TYPE_TXT.equals(message.getType())) {
                    ChatMessageContent content = ChatMessageContent.builder()
                            .type("text")
                            .text(nickNameStr + message.getContent())
                            .build();
                    contents.add(content);
                } else {
                    MessageImg img = message.getImg();
                    String type = "image";
                    ChatMessageContent.ContentSource contentSource = new ChatMessageContent.ContentSource();
                    contentSource.setType("base64");
                    contentSource.setMediaType(img.getMimeType());
                    contentSource.setData(img.getBase64String());
                    contents.add(ChatMessageContent.builder().type(type).source(contentSource).build());
                }
                
                item = me.vacuity.ai.sdk.claude.entity.ChatMessage.builder().role(last).content(contents).build();
                messages.add(item);
            }
        }
        return processClaude(model, prompt, messages, funcFlag);
    }

    private ModelAnswer soloOpenaiChat(String model, String prompt, List<BotMessage> botMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", prompt));
        String last = "assistant";
        Iterator<BotMessage> iterator = botMessages.iterator();
        while (iterator.hasNext()) {
            BotMessage item = iterator.next();
            if (last.equals(item.getRole())) {
                iterator.remove();
            } else {
                break;
            }
        }
        for (BotMessage message : botMessages) {
            ChatMessage item = null;
            if (message.getRole().equals(last)) {
                item = messages.get(messages.size() - 1);

                List<me.vacuity.ai.sdk.openai.entity.ChatMessageContent> contentList = (List<me.vacuity.ai.sdk.openai.entity.ChatMessageContent>) item.getContent();

                if (StringUtils.isEmpty(message.getType()) || BotConstant.LOCAL_MESSAGE_TYPE_TXT.equals(message.getType())) {
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent content = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent();
                    content.setType("text");
                    content.setText(message.getContent());
                    contentList.add(content);
                } else {
                    MessageImg img = message.getImg();
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent.ImageUrl imageUrl = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent.ImageUrl();
                    String base64Image = "data:" + img.getMimeType() + ";base64," + img.getBase64String();
                    imageUrl.setUrl(base64Image);
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent fileContent = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent();
                    fileContent.setType("image_url");
                    fileContent.setImageUrl(imageUrl);
                    contentList.add(fileContent);
                }
                item.setContent(contentList);
                messages.set(messages.size() - 1, item);
            } else {
                last = message.getRole();
                List<me.vacuity.ai.sdk.openai.entity.ChatMessageContent> contentList = new ArrayList<>();
                if (StringUtils.isEmpty(message.getType()) || BotConstant.LOCAL_MESSAGE_TYPE_TXT.equals(message.getType())) {
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent content = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent();
                    content.setType("text");
                    content.setText(message.getContent());
                    contentList.add(content);
                } else {
                    MessageImg img = message.getImg();
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent.ImageUrl imageUrl = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent.ImageUrl();
                    String base64Image = "data:" + img.getMimeType() + ";base64," + img.getBase64String();
                    imageUrl.setUrl(base64Image);
                    me.vacuity.ai.sdk.openai.entity.ChatMessageContent fileContent = new me.vacuity.ai.sdk.openai.entity.ChatMessageContent();
                    fileContent.setType("image_url");
                    fileContent.setImageUrl(imageUrl);
                    contentList.add(fileContent);
                }
                item = ChatMessage.builder().role(last).content(contentList).build();
                messages.add(item);

            }
        }
        return processOpenai(model, messages);
    }

    private ModelAnswer soloDeepseekChat(String model, String prompt, List<BotMessage> botMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", prompt));
        String last = "assistant";
        Iterator<BotMessage> iterator = botMessages.iterator();
        while (iterator.hasNext()) {
            BotMessage item = iterator.next();
            if (last.equals(item.getRole())) {
                iterator.remove();
            } else {
                break;
            }
        }
        for (BotMessage message : botMessages) {
            if (BotConstant.LOCAL_MESSAGE_TYPE_IMG.equals(message.getType())){
                continue;
            }
                
            ChatMessage item = null;
            if (message.getRole().equals(last)) {
                item = messages.get(messages.size() - 1);
                item.setContent(item.getContent() + "\n" + message.getContent());
                messages.set(messages.size() - 1, item);
            } else {
                last = message.getRole();
                item = ChatMessage.builder().role(last).content(message.getContent()).build();
                messages.add(item);

            }
        }
        return processOpenai(model, messages);
    }

    private ModelAnswer soloClaudeChat(String model, String prompt, List<BotMessage> botMessages, boolean funcFlag) {
        List<me.vacuity.ai.sdk.claude.entity.ChatMessage> messages = new ArrayList<>();
        String last = "assistant";
        Iterator<BotMessage> iterator = botMessages.iterator();
        while (iterator.hasNext()) {
            BotMessage item = iterator.next();
            if (last.equals(item.getRole())) {
                iterator.remove();
            } else {
                break;
            }
        }
        for (BotMessage message : botMessages) {
            me.vacuity.ai.sdk.claude.entity.ChatMessage item = null;
            if (message.getRole().equals(last)) {
                item = messages.get(messages.size() - 1);
                List<ChatMessageContent> contents = (List<ChatMessageContent>) item.getContent();
                if (StringUtils.isEmpty(message.getType()) || BotConstant.LOCAL_MESSAGE_TYPE_TXT.equals(message.getType())) {
                    ChatMessageContent content = ChatMessageContent.builder()
                            .type("text")
                            .text(message.getContent())
                            .build();
                    contents.add(content);
                } else {
                    MessageImg img = message.getImg();
                    String type = "image";
                    ChatMessageContent.ContentSource contentSource = new ChatMessageContent.ContentSource();
                    contentSource.setType("base64");
                    contentSource.setMediaType(img.getMimeType());
                    contentSource.setData(img.getBase64String());
                    contents.add(ChatMessageContent.builder().type(type).source(contentSource).build());
                }
                item.setContent(contents);
                messages.set(messages.size() - 1, item);
            } else {
                last = message.getRole();

                List<ChatMessageContent> contents = new ArrayList<>();
                if (StringUtils.isEmpty(message.getType()) || BotConstant.LOCAL_MESSAGE_TYPE_TXT.equals(message.getType())) {
                    ChatMessageContent content = ChatMessageContent.builder()
                            .type("text")
                            .text(message.getContent())
                            .build();
                    contents.add(content);
                } else {
                    MessageImg img = message.getImg();
                    String type = "image";
                    ChatMessageContent.ContentSource contentSource = new ChatMessageContent.ContentSource();
                    contentSource.setType("base64");
                    contentSource.setMediaType(img.getMimeType());
                    contentSource.setData(img.getBase64String());
                    contents.add(ChatMessageContent.builder().type(type).source(contentSource).build());
                }

                item = me.vacuity.ai.sdk.claude.entity.ChatMessage.builder().role(last).content(contents).build();
                messages.add(item);
            }
        }
        
        return processClaude(model, prompt, messages, funcFlag);
    }

    private ModelAnswer processOpenai(String model, List<ChatMessage> messages) {
        OpenaiClient clientItem;
        if (model.contains("gpt")) {
            clientItem = getClient();
        } else if (model.contains("deepseek")){
            clientItem = getDeepseekClient();
        } else if (model.contains("grok")){
            clientItem = getGrokClient();
        } else {
            return ModelAnswer.builder()
                    .success(false)
                    .answer("系统错误，请重试")
                    .result("系统错误，请重试")
                    .build();
        }
        FunctionExecutor functionExecutor = getChatgptFuncExecutor();
        ChatTool tool = ChatTool.builder()
                .type("function")
                .function(functionExecutor.getFunctions().get(0))
                .build();
        List<ChatTool> tools = Arrays.asList(tool);
        if (model.contains("deepseek")) {
            tools = null;
        }
        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.6f)
                .presencePenalty(1f)
                .tools(tools)
                .build();
        ChatResponse chatResponse = clientItem.chat(request);
        ChatResponseMessage responseMessage = chatResponse.getChoices().get(0).getMessage();
        if (CollUtil.isNotEmpty(responseMessage.getToolCalls())) {
            ChatMessage assistantMsg = ChatMessage.builder()
                    .role("assistant")
                    .toolCalls(responseMessage.getToolCalls())
                    .build();
            messages.add(assistantMsg);
            for (ChatFunctionCall functionCall : responseMessage.getToolCalls()) {
                log.info("trying to execute function:{}", functionCall.getFunction().getName());
                Optional<ChatMessage> message = functionExecutor.executeAndConvertToMessageSafely(functionCall);
                if (message.isPresent()) {
                    log.info("Executed " + functionCall.getFunction().getName() + ".");
                    messages.add(message.get());
                    request.setMessages(messages);
                } else {
                    log.info("Something went wrong with the execution of " + functionCall.getFunction().getName() + "...");
                }
            }
            ChatResponse chatResponse2 = clientItem.chat(request);
            return ModelAnswer.builder()
                    .success(true)
                    .answer(chatResponse2.getSingleContent())
                    .result(chatResponse2.getSingleContent())
                    .build();
        } else {
            if (StringUtils.isNotEmpty(responseMessage.getReasoningContent())) {
                String res = "思考过程：\n" + responseMessage.getReasoningContent() + "\n\n";
                res = res + "最终回复：\n" + responseMessage.getContent();
                return ModelAnswer.builder()
                        .success(true)
                        .result(res)
                        .answer(responseMessage.getContent().toString())
                        .build();
            }
            return ModelAnswer.builder()
                    .success(true)
                    .answer(chatResponse.getSingleContent())
                    .result(chatResponse.getSingleContent())
                    .build();
        }
    }

    private ModelAnswer processClaude(String model, String prompt, List<me.vacuity.ai.sdk.claude.entity.ChatMessage> messages, boolean funcFlag) {
        ClaudeClient clientItem = getClaudeClient();
        me.vacuity.ai.sdk.claude.service.FunctionExecutor functionExecutor = getClaudeFuncExecutor();
        List<ChatFunction> functions = null;
        if (funcFlag) {
            functions = functionExecutor.getFunctions();
        }
        
        me.vacuity.ai.sdk.claude.request.ChatRequest request = me.vacuity.ai.sdk.claude.request.ChatRequest.builder()
                .model(model)
                .system(prompt)
                .messages(messages)
                .temperature(1f)
                .maxTokens(8000)
                .tools(functions)
                .build();
        try {
            me.vacuity.ai.sdk.claude.response.ChatResponse chatResponse = clientItem.chat(request);
            if (chatResponse.getStopReason().equals("tool_use")) {

                String answer = "";
                List<ChatMessageContent> content = chatResponse.getContent();
                List<me.vacuity.ai.sdk.claude.entity.ChatFunctionCall> calls = new ArrayList<>();

                me.vacuity.ai.sdk.claude.entity.ChatMessage assistantMsg = me.vacuity.ai.sdk.claude.entity.ChatMessage.builder()
                        .role("assistant")
                        .build();
                List<ChatMessageContent> assistantContents = new ArrayList<>();


                for (ChatMessageContent chatMessageContent : content) {
                    if (chatMessageContent.getType().equals("text")) {
                        answer += chatMessageContent.getText();
                        ChatMessageContent content1 = ChatMessageContent.builder()
                                .type("text")
                                .text(answer.toString())
                                .build();
                        assistantContents.add(content1);
                    } else {
                        me.vacuity.ai.sdk.claude.entity.ChatFunctionCall call = new me.vacuity.ai.sdk.claude.entity.ChatFunctionCall();
                        call.setId(chatMessageContent.getId());
                        call.setName(chatMessageContent.getName());
                        call.setArguments(chatMessageContent.getInput());
                        calls.add(call);

                        ChatMessageContent toolContent = ChatMessageContent.builder()
                                .type("tool_use")
                                .id(chatMessageContent.getId())
                                .name(chatMessageContent.getName())
                                .input(chatMessageContent.getInput())
                                .build();
                        assistantContents.add(toolContent);
                    }
                }
                assistantMsg.setContent(assistantContents);
                messages.add(assistantMsg);
                messages.add(functionExecutor.executeAndConvertToMessage(calls));
                request.setMessages(messages);
                me.vacuity.ai.sdk.claude.response.ChatResponse chatResponse2 = clientItem.chat(request);
                answer = answer + "\n" + chatResponse2.getContent().get(0).getText();
                return ModelAnswer.builder()
                        .success(true)
                        .answer(answer)
                        .result(answer)
                        .build();
            } else {
                return ModelAnswer.builder()
                        .success(true)
                        .answer(chatResponse.getContent().get(0).getText())
                        .result(chatResponse.getContent().get(0).getText())
                        .build();
            }
        } catch (VacSdkException e) {
            log.error("chat error:{}", JSON.toJSONString(e));
            return ModelAnswer.builder()
                    .success(false)
                    .answer("系统错误，请重试")
                    .result("系统错误，请重试")
                    .build();
            
        }
    }

    public ModelAnswer search(String question) {
        OpenaiClient clientItem = getClient();
        ChatMessage message = ChatMessage.builder()
                .role("user")
                .content(question)
                .build();
        ChatRequest request = ChatRequest.builder()
                .model("gpt-4o-search-preview")
                .messages(Arrays.asList(message))
                .build();
        int retry = 0;
        while (retry < 3) {
            try {
                ChatResponse chatResponse = clientItem.chat(request);
                return ModelAnswer.builder()
                        .success(true)
                        .answer(chatResponse.getSingleContent())
                        .result(chatResponse.getSingleContent())
                        .build();
            } catch (Exception e) {
                retry++;
            }
        }
        return ModelAnswer.builder()
                .success(false)
                .answer("系统错误，请重试")
                .result("系统错误，请重试")
                .build();
    }

    public FunctionExecutor getChatgptFuncExecutor() {
        FunctionExecutor functionExecutor = new FunctionExecutor(Collections.singletonList(me.vacuity.ai.sdk.openai.entity.ChatFunction.builder()
                .name("search_result_by_model")
                .description("search the result by a llm model with online search ")
                .executor(SearchRequest.class, w -> searchByGpt(w))
                .build()));
        return functionExecutor;
    }

    public me.vacuity.ai.sdk.claude.service.FunctionExecutor getClaudeFuncExecutor() {
        me.vacuity.ai.sdk.claude.service.FunctionExecutor functionExecutor = new me.vacuity.ai.sdk.claude.service.FunctionExecutor(Collections.singletonList(me. vacuity. ai. sdk. claude. entity. ChatFunction.builder()
                .name("search_result_by_model")
                .description("search the result by a llm model with online search ")
                .executor(SearchRequest.class, w -> searchByGpt(w))
                .build()));
        return functionExecutor;
    }

    public static class SearchRequest {

        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }

    public static class SearchResult {

        private Boolean success;

        private String answer;

        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }
    
    public SearchResult searchByGpt(SearchRequest request) {
        ModelAnswer answer = search(request.query);
        SearchResult result = new SearchResult();
        result.setSuccess(answer.isSuccess());
        result.setAnswer(answer.getAnswer());
        return result;
    }

}
