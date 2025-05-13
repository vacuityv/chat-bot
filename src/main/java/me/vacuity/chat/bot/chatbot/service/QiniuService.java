package me.vacuity.chat.bot.chatbot.service;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import lombok.extern.slf4j.Slf4j;
import me.vacuity.chat.bot.chatbot.dto.VacException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * @description: qiniu storage
 * @author: vacuity
 * @create: 2023-04-15 13:32
 **/


@Slf4j
@Service
public class QiniuService {

    @Value("${vac.qiniu.accessKey: 111}")
    private String accessKey;
    @Value("${vac.qiniu.secretKey: 111}")
    private String secretKey;
    @Value("${vac.qiniu.bucket: 111}")
    private String bucket;
    @Value("${vac.qiniu.domain: 111}")
    private String domain;


    public String upload(String fileKey, InputStream inputStream) {

        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.huadong());
        cfg.resumableUploadAPIVersion = Configuration.ResumableUploadAPIVersion.V2;// 指定分片上传版本
        //...其他参数参考类注释
        UploadManager uploadManager = new UploadManager(cfg);
        //...生成上传凭证，然后准备上传
        //默认不指定key的情况下，以文件内容的hash值作为文件名
        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucket);
        try {
            Response response = uploadManager.put(inputStream, fileKey, upToken, null, null);
            //解析上传成功的结果
            DefaultPutRet putRet = JSON.parseObject(response.bodyString(), DefaultPutRet.class);
            String finalUrl = domain + "/" + putRet.key;
            return finalUrl;
        } catch (QiniuException ex) {
            log.error("qiniu upload error:{}", ex.getLocalizedMessage(), ex);
            throw new VacException("-1", "qiniu upload error");
        }
    }

    public String uploadToQiniu(String fileKey, String oriUrl) {
        // read img from url
        InputStream inputStream = HttpRequest.get(oriUrl).execute().bodyStream();
        String url = upload(fileKey, inputStream);
        return url;
    }

    public void delete(String fileKey) {
        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.huadong());
        Auth auth = Auth.create(accessKey, secretKey);
        BucketManager bucketManager = new BucketManager(auth, cfg);
        try {
            bucketManager.delete(bucket, fileKey);
        } catch (QiniuException ex) {
            if (StringUtils.isEmpty(ex.getLocalizedMessage()) || !ex.getLocalizedMessage().contains("no such file or directory")) {
                log.error("qiniu-delete-error:{}", ex.getLocalizedMessage());
                throw new VacException("-1", "qiniu delete error");
            }
        }
    }
}
