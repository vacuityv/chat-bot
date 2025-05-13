package me.vacuity.chat.bot.chatbot.controller;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import me.vacuity.chat.bot.chatbot.dto.BotCallbackReq;
import me.vacuity.chat.bot.chatbot.dto.VacRes;
import me.vacuity.chat.bot.chatbot.service.BotService;
import me.vacuity.chat.bot.chatbot.service.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-12-05 18:05
 **/


@Slf4j
@Controller
@RequestMapping("/bot")
public class BotController {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private BotService botService;

    @Value("${vac.ge.vac:123456}")
    private String vac;
    @Value("${vac.ge.host:123456}")
    private String geHost;


    @RequestMapping("/getQr")
    @ResponseBody
    public VacRes getQr(HttpServletRequest request) {
        String token = request.getHeader("vac-token");
        if (!StringUtils.equals(token, "20241205")) {
            return VacRes.fail("no");
        }
        String geToken = botService.getToken();
        String appId = botService.getAppId();
        JSONObject data = new JSONObject();
        data.put("appId", appId);
        
        String res = HttpRequest.post(geHost + "/login/getLoginQrCode")
                .header("X-GEWE-TOKEN", geToken)
                .body(data.toJSONString())
                .execute().body();
        JSONObject resObj = JSON.parseObject(res);
        if (resObj.getInteger("ret") == 200) {
            JSONObject resData = resObj.getJSONObject("data");
            appId = resData.getString("appId");
            botService.setAppId(appId);
            botService.setUuid(resData.getString("uuid"));
            return VacRes.success(resData);
        } else {
            return VacRes.fail(resObj.getString("msg"));
        }
    }

    @RequestMapping("/checkLogin")
    @ResponseBody
    public VacRes checkLogin(HttpServletRequest request, @RequestBody JSONObject req) {
        String token = request.getHeader("vac-token");
        if (!StringUtils.equals(token, "20241205")) {
            return VacRes.fail("no");
        }
        
        return VacRes.success(checkLogin(req.getString("captchCode")));
    }

    @RequestMapping("/callback")
    @ResponseBody
    public String callback(HttpServletRequest request, @RequestBody Optional<JSONObject> req) {
        if (req != null) {
            BotCallbackReq callbackReq = req.get().toJavaObject(BotCallbackReq.class);
            botService.processCallback(callbackReq);
        }
        return "success";
    }

    @RequestMapping("/info/{wxid}")
    @ResponseBody
    public JSONObject info(HttpServletRequest request, @PathVariable("wxid") String wxid) {
        return botService.getInfo(wxid);
    }

    @RequestMapping("/sendCallbackUrl")
    @ResponseBody
    public VacRes sendCallbackUrl(HttpServletRequest request) {
        String token = request.getHeader("vac-token");
        if (!StringUtils.equals(token, "20241205")) {
            return VacRes.fail("no");
        }
        String geToken = botService.getToken();
        JSONObject data = new JSONObject();
        data.put("token", geToken);
        data.put("callbackUrl", "https://aipolish.online/vac-tool-api/bot/callback");

        String res = HttpRequest.post(geHost + "/tools/setCallback")
                .header("X-GEWE-TOKEN", geToken)
                .body(data.toJSONString())
                .execute().body();
        JSONObject resObj = JSON.parseObject(res);
        return VacRes.success(resObj);
    }
    
    private JSONObject checkLogin(String captchCode) {
        String geToken = botService.getToken();
        String appId = botService.getAppId();
        JSONObject data = new JSONObject();
        data.put("appId", appId);
        data.put("uuid", botService.getUuid());
        data.put("captchCode", captchCode);

        String res = HttpRequest.post(geHost + "/login/checkLogin")
                .header("X-GEWE-TOKEN", geToken)
                .body(data.toJSONString())
                .execute().body();
        log.info("checkLogin: {}", res);
        JSONObject resObj = JSON.parseObject(res);
        if (resObj.getInteger("ret") == 200) {
            JSONObject resData = resObj.getJSONObject("data");
            if (resData.containsKey("loginInfo")) {
                JSONObject loginInfo = resData.getJSONObject("loginInfo");
                if (loginInfo != null && loginInfo.containsKey("wxid")) {
                    String wxid = loginInfo.getString("wxid");
                    botService.setWxid(wxid);
                }
            }
        }
        return resObj;
    }

    
}
