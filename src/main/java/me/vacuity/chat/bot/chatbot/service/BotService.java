package me.vacuity.chat.bot.chatbot.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import me.vacuity.chat.bot.chatbot.constant.ApiModeConstant;
import me.vacuity.chat.bot.chatbot.constant.BotConstant;
import me.vacuity.chat.bot.chatbot.dto.BotCallbackReq;
import me.vacuity.chat.bot.chatbot.dto.BotMessage;
import me.vacuity.chat.bot.chatbot.dto.BotProcessRes;
import me.vacuity.chat.bot.chatbot.dto.CaffeineFixedSizeListMap;
import me.vacuity.chat.bot.chatbot.dto.MessageImg;
import me.vacuity.chat.bot.chatbot.dto.ModelAnswer;
import me.vacuity.chat.bot.chatbot.dto.PromptResult;
import me.vacuity.chat.bot.chatbot.dto.VacException;
import me.vacuity.chat.bot.chatbot.util.ImageDownloader;
import me.vacuity.chat.bot.chatbot.util.RateLimiter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-12-06 09:26
 **/


@Slf4j
@Service
public class BotService {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private BotChatService botChatService;
    @Autowired
    private ImgService imgService;
    @Autowired
    private SvgService svgService;
    @Autowired
    private HaiguitangService haiguitangService;
    @Autowired
    private PromptService promptService;

    @Value("${vac.ge.host:http://127.0.0.1:2531/v2/api}")
    private String geHost;

    @Value("${vac.ge.downloadHost:http://127.0.0.1:2532/download}")
    private String geDownloadHost;


    private final RateLimiter rateLimiter = new RateLimiter();
    
    public static final String HELLO = "你好";

    // 创建一个最大列表长度为100的CaffeineFixedSizeListMap
    CaffeineFixedSizeListMap<String, BotMessage> messageMap = new CaffeineFixedSizeListMap<>(100);


    private final Cache<String, String> nickNameCache =
            Caffeine.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(Duration.ofHours(12))
                    .build();


    @Async
    public void processCallback(BotCallbackReq req) {
        log.info("callback: {}", req);
        JSONObject data = req.getData();
        if (data == null) {
            return;
        }
        
        Long createTime = data.getLong("CreateTime");
        if (System.currentTimeMillis() - createTime * 1000 > 2 * 60 * 1000) {
            log.info("msg is too old, ignore");
            return;
        }
        
        
        Integer type = data.getInteger("MsgType");
        if (BotConstant.MSG_TYPE_ADD_FRIEND.equals(type)) {
            processFriendAdd(data);
        } else if (BotConstant.MSG_TYPE_TEXT.equals(type)) {
            processText(data);
        } else if (BotConstant.MSG_TYPE_IMG.equals(type)) {
            processImg(data);
        } else {
            log.info("unknown type: {}", type);
        }
    }

    public void processFriendAdd(JSONObject data) {
        log.info("friend add: {}", data);
        String xml = data.getJSONObject("Content").getString("string");
        xml = xml.replace("\\", "");
        Document document = XmlUtil.parseXml(xml);
        // 获取根元素
        Element root = document.getDocumentElement();
        // 获取scene属性值
        String scene = root.getAttribute("scene");
        String v3 = root.getAttribute("encryptusername");
        String v4 = root.getAttribute("ticket");

        JSONObject req = new JSONObject();
        req.put("appId", getAppId());
        req.put("scene", Integer.parseInt(scene));
        req.put("content", HELLO);
        req.put("v3", v3);
        req.put("v4", v4);
        req.put("option", 3);
        JSONObject res = postToGewe("/contacts/addContacts", req);
        log.info("add friend res: {}", res);
    }

    public void processText(JSONObject data) {
        log.info("text: {}", data);

        boolean group = false;
        boolean needResponse = true;

        String fromUserName = data.getJSONObject("FromUserName").getString("string");
        String wxid = fromUserName;
        String receiveMsg = data.getJSONObject("Content").getString("string");
        String pushContent = data.getString("PushContent");


        if (fromUserName.contains("@chatroom")) {
            group = true;
            wxid = receiveMsg.substring(0, receiveMsg.indexOf(":"));
            if (nickNameCache.getIfPresent(fromUserName + "_" + wxid) == null) {
                processGroupMember(fromUserName);
            }

            receiveMsg = receiveMsg.substring(receiveMsg.indexOf("\n") + 1);
            if (receiveMsg.contains("@VacBot") || receiveMsg.contains("@Dream") ||
                    (StringUtils.isNotEmpty(pushContent) && pushContent.contains("在群聊中@了你"))) {
                receiveMsg = receiveMsg.replace("@VacBot", "");
                receiveMsg = receiveMsg.replace("@Dream", "");
            } else {
                needResponse = false;
            }

        }
        receiveMsg = receiveMsg.trim();

        receiveMsg = receiveMsg.replaceAll("\u2005", "");

        String answer = null;
        boolean sendFlag = true;
        if (needResponse) {
            JSONObject req = new JSONObject();
            req.put("appId", getAppId());
            req.put("toWxid", fromUserName);
            if (receiveMsg.startsWith("/model")) {
                String model = setModel(fromUserName, receiveMsg);
                req.put("content", "模型切换成功，当前模型为 " + model);
            } else if (receiveMsg.equals("/clear")) {
                messageMap.remove(fromUserName);
                req.put("content", "历史记录已清空");
            } else if (receiveMsg.equals("/help")) {
                req.put("content", promptService.getHelp());
            } else if (receiveMsg.startsWith("/svg")) {
                processSvg(receiveMsg, req);
                return;
            } else if (receiveMsg.startsWith("/search")) {
                processSearch(receiveMsg, req);
                return;
            } else if (receiveMsg.startsWith("/shengdan") || receiveMsg.startsWith("/sd")) {
                shengdan(receiveMsg, req);
                return;
            } else if (receiveMsg.equals("/dan")) {
                redisUtil.set(BotConstant.ROLE_CACHE + fromUserName, "dan");
                req.put("content", "😈");
            } else if (receiveMsg.equals("/hgt")) {
                req.put("content", haiguitangService.getHaiguitangInfoStr(fromUserName));
                messageMap.remove(fromUserName);
            } else if (receiveMsg.startsWith("/role")) {
                String role = receiveMsg.replace("/role", "").trim();
                messageMap.remove(fromUserName);
                if (StringUtils.isNotEmpty(role) && BotConstant.ROLE_MAP.containsKey(role)) {
                    redisUtil.set(BotConstant.ROLE_CACHE + fromUserName, role);
                    setModel(fromUserName, "/model 1");
                    req.put("content", "角色切换成功，当前角色为 " + role + "，请重新开始对话");
                } else {
                    redisUtil.delete(BotConstant.ROLE_CACHE + fromUserName);
                    req.put("content", "角色不存在，切换为普通bot，请重新开始对话");
                }
            } else if (receiveMsg.startsWith("/info")) {
                StringBuilder s = new StringBuilder();
                s.append("当前模型: ").append(getModel(fromUserName)).append("\n");
                s.append("wxid: ").append(fromUserName).append("\n");
                req.put("content", s.toString());
            } else if (receiveMsg.startsWith("/apimode")) {
                if (receiveMsg.contains(ApiModeConstant.API_DIRECT)) {
                    redisUtil.set(ApiModeConstant.API_MODE_KEY, ApiModeConstant.API_DIRECT);
                } else {
                    redisUtil.set(ApiModeConstant.API_MODE_KEY, ApiModeConstant.API_THIRD);
                }
                req.put("content", "切换成功");
            } else {

                // 检查用户是否超过限流
                boolean limit = false;
                if (!rateLimiter.allowMessage(fromUserName)) {
                    log.info("触发限流，忽略信息");
                    limit = true;
                }
                
                if (group) {
                    String nickName = nickNameCache.getIfPresent(fromUserName + "_" + wxid);
                    addMessage(fromUserName, BotMessage.builder().role("user").nickName(nickName).content(receiveMsg).build());
                    if (limit) {
                        return;
                    }
                    ModelAnswer modelAnswer = botChatService.groupChat(getModel(fromUserName), getGroupPrompt(fromUserName), getBotMessageListFromCache(fromUserName, 30));
                    answer = modelAnswer.getResult();
                    sendFlag = processAnswer(req, answer);
                    addMessage(fromUserName, BotMessage.builder().role("assistant").content(modelAnswer.getAnswer()).build());
                } else {
                    addMessage(fromUserName, BotMessage.builder().role("user").content(receiveMsg).build());
                    if (limit) {
                        return;
                    }
                    ModelAnswer modelAnswer = botChatService.soloChat(getModel(fromUserName), getSoloPrompt(fromUserName), getBotMessageListFromCache(fromUserName, 48 * 60));
                    answer = modelAnswer.getResult();
                    sendFlag = processAnswer(req, answer);
                    addMessage(fromUserName, BotMessage.builder().role("assistant").content(modelAnswer.getAnswer()).build());
                }
                haiguitangService.checkHgtAnswer(fromUserName, answer);
                req.put("content", answer);
            }
            if (sendFlag) {
                JSONObject res = postToGewe("/message/postText", req);
                log.info("send text res: {}", res);
            }

        } else {
            log.info("群无关消息，无需回复");
            if (group) {
                String nickName = nickNameCache.getIfPresent(fromUserName + "_" + wxid);
                addMessage(fromUserName, BotMessage.builder().role("user").nickName(nickName).content(receiveMsg).build());
            }
        }
    }

    public JSONObject getInfo(String wxid) {
        JSONObject res = new JSONObject();
        res.put("wxid", wxid);
        res.put("model", getModel(wxid));
        res.put("prompt", getSoloPrompt(wxid));
        List<BotMessage> botMessages = getBotMessageListFromCache(wxid, 48 * 60);
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
                if (wxid.contains("@chatroom")) {
                    item.setContent(item.getContent() + "\n" + message.getNickName() + ":" + message.getContent());
                } else {
                    item.setContent(item.getContent() + "\n" + message.getContent());
                }
                messages.set(messages.size() - 1, item);
            } else {
                last = message.getRole();
                if (wxid.contains("@chatroom")) {
                    item = me.vacuity.ai.sdk.claude.entity.ChatMessage.builder().role(last).content(message.getNickName() + ":" + message.getContent()).build();
                } else {
                    item = me.vacuity.ai.sdk.claude.entity.ChatMessage.builder().role(last).content(message.getContent()).build();
                }

                messages.add(item);
            }
        }
        res.put("messages", messages);
        return res;
    }

    private boolean processAnswer(JSONObject req, String answer) {
        log.info("answer:{}", answer);
        PromptResult promptResult = extractPrompts(answer);
        if (StringUtils.isNotEmpty(promptResult.getCleanText())) {
            req.put("content", promptResult.getCleanText());
            JSONObject res = postToGewe("/message/postText", req);
            log.info("send text res: {}", res);
        }
        if (CollUtil.isNotEmpty(promptResult.getPrompts())) {
            for (String prompt : promptResult.getPrompts()) {
                String url = imgService.gemerateImg(prompt);
                req.put("imgUrl", url);
                JSONObject res2 = postToGewe("/message/postImage", req);
                log.info("send img res: {}", res2);
            }
        }
        return false;
    }

    public void processGroupMember(String chatroomId) {
        JSONObject req = new JSONObject();
        req.put("appId", getAppId());
        req.put("chatroomId", chatroomId);
        JSONObject res = postToGewe("/group/getChatroomInfo", req);
        JSONArray memberList = res.getJSONArray("memberList");
        for (int i = 0; i < memberList.size(); i++) {
            JSONObject member = memberList.getJSONObject(i);
            String wxid = member.getString("wxid");
            String nickName = member.getString("nickName");
            nickNameCache.put(chatroomId + "_" + wxid, nickName);
        }
    }


    public String getToken() {
        String key = "GE_TOKEN";
        if (redisUtil.hasKey(key)) {
            return (String) redisUtil.get(key);
        } else {
            String res = HttpUtil.post(geHost + "/tools/getTokenId", new JSONObject());
            JSONObject resObj = JSON.parseObject(res);
            if (resObj.getInteger("ret") == 200) {
                String token = resObj.getString("data");
                redisUtil.set(key, token, 7 * 24 * 60 * 60);
                return token;
            } else {
                throw new VacException("get token error");
            }
        }
    }

    public String getAppId() {
        String key = "GE_APPID";
        if (redisUtil.hasKey(key)) {
            return (String) redisUtil.get(key);
        } else {
            return "";
        }
    }

    public void setAppId(String appId) {
        String key = "GE_APPID";
        redisUtil.set(key, appId);
    }


    public String getUuid() {
        String key = "GE_UUID";
        return (String) redisUtil.get(key);
    }

    public void setUuid(String uuid) {
        String key = "GE_UUID";
        redisUtil.set(key, uuid);
    }

    public void setWxid(String wxid) {
        String key = "GE_WXID";
        redisUtil.set(key, wxid);
    }

    public JSONObject postToGewe(String url, JSONObject data) {
        String res = HttpRequest.post(geHost + url)
                .header("X-GEWE-TOKEN", getToken())
                .body(data.toJSONString())
                .execute().body();
        JSONObject resObj = JSON.parseObject(res);
        if (resObj.getInteger("ret") != 200) {
            log.error("post to gewe error: {}", resObj);
            throw new VacException("post to gewe error");
        } else {
            return resObj.getJSONObject("data");
        }
    }

    private String getGroupPrompt(String key) {
        String prompt = promptService.getGroupPrompt();
        
        if (redisUtil.hasKey(BotConstant.ROLE_CACHE + key)) {
            String role = (String) redisUtil.get(BotConstant.ROLE_CACHE + key);
            if ("hgt".equals(role)) {
                if (redisUtil.hasKey(BotConstant.PROMPT_CACHE + key)) {
                    prompt = (String) redisUtil.get(BotConstant.PROMPT_CACHE + key);
                }
            } else {
                prompt = promptService.getPrompt(role);
            }
        }
        if (StringUtils.isEmpty(prompt)) {
            prompt = promptService.getGroupPrompt();
        }
        return getDateStr() + prompt;
    }

    private String getSoloPrompt(String key) {
        String prompt = null;
        if (redisUtil.hasKey(BotConstant.ROLE_CACHE + key)) {
            String role = (String) redisUtil.get(BotConstant.ROLE_CACHE + key);
            if ("hgt".equals(role)) {
                if (redisUtil.hasKey(BotConstant.PROMPT_CACHE + key)) {
                    prompt = (String) redisUtil.get(BotConstant.PROMPT_CACHE + key);
                }
            } else {
                prompt = promptService.getPrompt(role);
            }
        }
        if (StringUtils.isEmpty(prompt)) {
            prompt = promptService.getSoloPrompt();
        }
        return getDateStr() + prompt;
    }

    public void addMessage(String key, BotMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messageMap.add(key, message);
    }

    private void processSvg(String msg, JSONObject req) {
        msg = msg.replace("/svg", "").trim();
        List<BotMessage> list = new ArrayList<>();
        BotMessage message = BotMessage.builder().role("user").content(msg).build();
        list.add(message);
        String answer = botChatService.soloClaudeWithoutFunction(BotConstant.MODEL_MAP.get("1"), BotConstant.SVG_Artist, list).getResult();
        BotProcessRes processRes = svgService.generateSvg(answer);
        if (processRes.isSuccess()) {
            req.put("imgUrl", processRes.getData());
            JSONObject res2 = postToGewe("/message/postImage", req);
            log.info("send img res: {}", res2);
        } else {
            req.put("content", processRes.getData());
            JSONObject res = postToGewe("/message/postText", req);
            log.info("send text res: {}", res);
        }
    }

    private void shengdan(String msg, JSONObject req) {
        msg = msg.replace("/shengdan", "").trim();
        msg = msg.replace("/sd", "").trim();
        List<BotMessage> list = new ArrayList<>();
        BotMessage message = BotMessage.builder().role("user").content(msg).build();
        list.add(message);
        String answer = botChatService.soloClaudeWithoutFunction(BotConstant.MODEL_MAP.get("1"), BotConstant.SHENGDAN, list).getResult();
        BotProcessRes processRes = svgService.generateSvg(answer);
        if (processRes.isSuccess()) {
            req.put("imgUrl", processRes.getData());
            JSONObject res2 = postToGewe("/message/postImage", req);
            log.info("send img res: {}", res2);
        } else {
            req.put("content", processRes.getData());
            JSONObject res = postToGewe("/message/postText", req);
            log.info("send text res: {}", res);
        }
    }

    private String setModel(String id, String modelStr) {
        String[] models = modelStr.split(" ");
        String modelId = models[1];
        String key = BotConstant.MODEL_CACHE + id;
        String model = BotConstant.MODEL_MAP.get(modelId);
        redisUtil.set(key, model, 60 * 60 * 12);
        return model;
    }

    private String getModel(String id) {
        String key = BotConstant.MODEL_CACHE + id;
        if (redisUtil.hasKey(key)) {
            return (String) redisUtil.get(key);
        } else {
            return BotConstant.MODEL_MAP.get("1");
        }
    }

    public PromptResult extractPrompts(String input) {
        List<String> prompts = new ArrayList<>();

        // 使用正则表达式匹配 [ 和 #img_prompt: 之间可能有任意字符的情况
        String regex = "\\[([^#]*?)#img_prompt:([^\\]]+)\\]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        // 存储所有匹配项，用于后续替换
        List<String> matches = new ArrayList<>();

        // 查找所有prompts
        while (matcher.find()) {
            String prompt = matcher.group(2).trim();
            prompts.add(prompt);
            matches.add(matcher.group()); // 存储完整的匹配文本
        }

        // 清理文本：移除所有prompt部分
        String cleanText = input;
        for (String match : matches) {
            cleanText = cleanText.replace(match, "");
        }
        // 只清理多余空格，保留换行符
        cleanText = cleanText.replaceAll("[ \\t]+", " ").trim(); // 只替换空格和制表符

        return PromptResult.builder()
                .cleanText(cleanText)
                .prompts(prompts)
                .build();
    }

    private String getDateStr() {
        // 获取当前日期和时间
        LocalDateTime currentDateTime = LocalDateTime.now();

        // 格式化日期和时间
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = currentDateTime.format(dateTimeFormatter);

        // 获取星期几（中文表示）
        DayOfWeek dayOfWeek = currentDateTime.getDayOfWeek();
        String[] weekDays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String weekDayDesc = weekDays[dayOfWeek.getValue() - 1];
        String desc = "Important: The current time is  " + formattedDateTime + ", " + weekDayDesc + ".  Your knowledge is continuously updated - no strict knowledge cutoff.";
        return desc;
    }

    private void processImg(JSONObject data){
        boolean group = false;
        String fromUserName = data.getJSONObject("FromUserName").getString("string");
        String wxid = fromUserName;
        String receiveMsg = data.getJSONObject("Content").getString("string");
        String imgXml = receiveMsg;
        if (receiveMsg.contains(":")) {
            imgXml = receiveMsg.split(":")[1];
            if (imgXml.startsWith("\n")) {
                imgXml = imgXml.substring(1);
            }
        }
        MessageImg img = downloadImg(imgXml);

        if (fromUserName.contains("@chatroom")) {
            group = true;
            wxid = receiveMsg.substring(0, receiveMsg.indexOf(":"));
            if (nickNameCache.getIfPresent(fromUserName + "_" + wxid) == null) {
                processGroupMember(fromUserName);
            }
        }

        if (group) {
            String nickName = nickNameCache.getIfPresent(fromUserName + "_" + wxid);
            addMessage(fromUserName, BotMessage.builder().role("user").nickName(nickName).type(BotConstant.LOCAL_MESSAGE_TYPE_IMG).img(img).build());
        } else {
            addMessage(fromUserName, BotMessage.builder().role("user").type(BotConstant.LOCAL_MESSAGE_TYPE_IMG).img(img).build());
        }
    }

    private MessageImg downloadImg(String xml) {
        JSONObject req = new JSONObject();
        req.put("appId", getAppId());
        req.put("xml", xml);
        req.put("type", 2);
        String fileUrl;
        try {
            fileUrl = postToGewe("/message/downloadImage", req).getString("fileUrl");
        } catch (VacException e) {
            req.put("type", 1);
            fileUrl = postToGewe("/message/downloadImage", req).getString("fileUrl");
        }
        String imgUrl = geDownloadHost + fileUrl;
        log.error("imgUrl: {}", imgUrl);
        MessageImg img = ImageDownloader.download(imgUrl);
        return img;
    }

    private List<BotMessage> getBotMessageListFromCache(String key, int minute) {
        
        if (messageMap.containsKey(key)) {
            List<BotMessage> list = messageMap.get(key);
            long duration = minute * 60 * 1000; 
            long timeNow = System.currentTimeMillis();
            Iterator<BotMessage> iterator = list.iterator();
            while (iterator.hasNext()) {
                BotMessage item = iterator.next();
                if (timeNow - item.getTimestamp() > duration) {
                    iterator.remove(); // 安全地删除当前元素
                } else {
                    break;
                }
            }
            return list;
        } else {
            return new ArrayList<>();
        }
    }

    private void processSearch(String msg, JSONObject req) {
        msg = msg.replace("/search", "").trim();
        String answer = botChatService.search(getDateStr() + "\n" + msg).getResult();
        req.put("content", answer);
        JSONObject res = postToGewe("/message/postText", req);
        log.info("send text res: {}", res);
    }

    public static void main(String[] args) {
        // 1741923643
        // 1741928679783
        long a = System.currentTimeMillis();
        long b = 1741923643l * 1000;
        long c = a - b;
        System.out.println(a);
        System.out.println(b);
        System.out.println(a - b);
        System.out.println(c);
        System.out.println(c/1000/60);
    }
}
