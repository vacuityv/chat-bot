package me.vacuity.chat.bot.chatbot.util;

import me.vacuity.chat.bot.chatbot.dto.MessageImg;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class ImageDownloader {

    public static MessageImg download(String imageUrl) {
        try {

            // 创建连接
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            // 下载图片
            BufferedImage originalImage = ImageIO.read(connection.getInputStream());
            if (originalImage == null) {
                throw new IOException("Failed to read image from URL");
            }

            // 创建一个新的RGB格式的BufferedImage
            BufferedImage newImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );

            // 创建Graphics2D对象并设置背景色
            Graphics2D g2d = newImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, newImage.getWidth(), newImage.getHeight());

            // 将原图绘制到新图上
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose();

            // 转换为JPEG格式
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // 使用"jpg"而不是"JPEG"
            boolean success = ImageIO.write(newImage, "jpg", baos);


            if (!success) {
                throw new IOException("Failed to convert image to JPEG format");
            }

            baos.flush();
            byte[] imageBytes = baos.toByteArray();

            // 检查图片字节数组
            if (imageBytes == null || imageBytes.length == 0) {
                throw new IOException("Image bytes are empty");
            }


            // 获取MIME类型
            String mimeType = "image/jpeg";

            // 转换为Base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 关闭资源
            baos.close();

            return MessageImg.builder()
                    .mimeType(mimeType)
                    .base64String(base64Image)
                    .build();

        } catch (Exception e) {
            return null;
        }
    }


    public static void main(String[] args) {
        String url = "http://47.122.61.221:2532/download/20250207/wx_w7FvdAmN-DiZA53gvSi7C/a1c1bdfa-3e3e-466a-9dd9-1f353c7e7967.png";
        MessageImg messageImg = download(url);
        System.out.println(messageImg.getMimeType());
    }
}