package me.vacuity.chat.bot.chatbot.service;

import lombok.extern.slf4j.Slf4j;
import me.vacuity.chat.bot.chatbot.dto.BotProcessRes;
import me.vacuity.chat.bot.chatbot.util.SeqUtil;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.keys.BooleanKey;
import org.apache.batik.util.XMLResourceDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @description:
 * @author: vacuity
 * @create: 2024-12-13 16:29
 **/


@Slf4j
@Service
public class SvgService {

    @Autowired
    private QiniuService qiniuService;

    private float parseValueToPixels(String value) {
        value = value.trim();
        if (value.endsWith("px")) {
            return Float.parseFloat(value.substring(0, value.length() - 2));
        } else if (value.endsWith("pt")) {
            return Float.parseFloat(value.substring(0, value.length() - 2)) * 1.33333f;
        } else if (value.endsWith("mm")) {
            return Float.parseFloat(value.substring(0, value.length() - 2)) * 3.779528f;
        } else if (value.endsWith("cm")) {
            return Float.parseFloat(value.substring(0, value.length() - 2)) * 37.79528f;
        } else if (value.endsWith("in")) {
            return Float.parseFloat(value.substring(0, value.length() - 2)) * 96;
        } else {
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    public InputStream convertSVGToPNG(String svgCode) throws Exception {

        float scale = 2.0f;

        // 设置默认字体
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // 在SVG代码中添加默认字体样式
        String modifiedSvgCode = svgCode;
        if (!svgCode.contains("@font-face") && !svgCode.contains("font-family")) {
            // 在 <svg> 标签后插入默认字体样式
            modifiedSvgCode = svgCode.replaceFirst("<svg",
                    "<svg style=\"font-family: 'WenQuanYi Micro Hei', 'Microsoft YaHei', 'SimSun', sans-serif;\"");
        }
        
        // 创建 SVG 文档
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
        SVGDocument document = factory.createSVGDocument(null, new StringReader(svgCode));

        // 获取SVG根元素
        SVGSVGElement rootElement = document.getRootElement();

        // 获取viewBox属性或width/height属性
        float width = 0;
        float height = 0;

        String viewBox = rootElement.getAttributeNS(null, "viewBox");
        if (viewBox != null && !viewBox.isEmpty()) {
            String[] parts = viewBox.split("\\s+");
            if (parts.length == 4) {
                width = Float.parseFloat(parts[2]);
                height = Float.parseFloat(parts[3]);
            }
        } else {
            String widthStr = rootElement.getAttributeNS(null, "width");
            String heightStr = rootElement.getAttributeNS(null, "height");
            if (widthStr != null && !widthStr.isEmpty()) {
                width = parseValueToPixels(widthStr);
            }
            if (heightStr != null && !heightStr.isEmpty()) {
                height = parseValueToPixels(heightStr);
            }
        }

        // 如果无法获取尺寸，设置默认值
        if (width <= 0) width = 800;
        if (height <= 0) height = 600;
        
        // 创建 SVG 输入
        TranscoderInput input = new TranscoderInput(new StringReader(svgCode));

        // 创建 ByteArrayOutputStream 用于内存输出
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(pngOutputStream);

        // 创建 PNG 转码器
        PNGTranscoder transcoder = new PNGTranscoder();

        transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);

        // 设置放大后的尺寸
        transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width * scale);
        transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height * scale);
        
        // 执行转码
        transcoder.transcode(input, output);

        // 将 ByteArrayOutputStream 转换为 InputStream
        ByteArrayInputStream inputStream = new ByteArrayInputStream(pngOutputStream.toByteArray());

        // 关闭 ByteArrayOutputStream
        pngOutputStream.close();

        return inputStream;
    }

    public BotProcessRes generateSvg(String answer) {
        try {
            String svgCode = extractSvgCode(answer);
            log.info("svgCode: {}", svgCode);
            InputStream inputStream = convertSVGToPNG(svgCode);
            String fileKey = "bot/svg/" + SeqUtil.getNextId() + ".png";
            String url = qiniuService.upload(fileKey, inputStream);
            return BotProcessRes.builder().success(true).data(url).build();
        } catch (Exception e) {
            log.info("generateSvg error", e);
            return BotProcessRes.builder().success(false).data("生成图片出错，请重试").build();
        }
    }

    public String extractSvgCode(String input) {
        // 优先匹配 ```svg ``` 包裹的内容
        String regex1 = "```svg\\s*(.*?)\\s*```";
        Pattern pattern1 = Pattern.compile(regex1, Pattern.DOTALL);
        Matcher matcher1 = pattern1.matcher(input);
        if (matcher1.find()) {
            return matcher1.group(1);
        }

        // 如果没有找到 ```svg ```，匹配 <svg>...</svg>
        String regex2 = "<svg\\s*(.*?)</svg>";
        Pattern pattern2 = Pattern.compile(regex2, Pattern.DOTALL);
        Matcher matcher2 = pattern2.matcher(input);

        if (matcher2.find()) {
            return "<svg " + matcher2.group(1) + "</svg>";
        }

        return null; // 没有找到匹配的 svg 代码
    }


}
