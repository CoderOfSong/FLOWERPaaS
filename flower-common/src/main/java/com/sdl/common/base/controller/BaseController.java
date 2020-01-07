package com.sdl.common.base.controller;

import com.sdl.common.amqp.MessageSender;
import com.sdl.common.base.exception.BusinessException;
import com.sdl.common.utils.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.ApplicationObjectSupport;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @program flowerPaaS
 * @description: 通用控制层
 * @author: songdeling
 * @create: 2019/12/24 11:00
 */
@RestController
@Slf4j
public abstract class BaseController extends ApplicationObjectSupport {

    /**
     * 端口号
     */
    @Value("${server.port}")
    protected String port;

    /**
     * 应用名称（默认：flower-unknown）
     */
    @Value("${spring.application.name}")
    protected String applicationName;

    /**
     * 版本，从gateway服务发起，用于灰度（如：http://localhost:8400/demo/consumer/gray?version=1.0）
     */
    @Value("${spring.cloud.nacos.discovery.metadata.version:1.0}")
    protected String version;

    /**
     * 认证服务地址（默认：http://localhost:8888/oauth/token）
     */
    @Value("${security.oauth2.client.access-token-uri:http://localhost:8888/oauth/token}")
    protected String authServer;

    /**
     * 文件上传路径（默认：/tmp）
     */
    @Value("${file.upload.path:/tmp}")
    protected String fileUploadPath;

    /**
     * RabbitMQ 消息发送者
     */
    @Resource
    protected MessageSender messageSender;

    /**
     * 根据名称获取bean对象
     *
     * @param name 名称
     */
    protected Object getBean(String name) {
        return this.getApplicationContext().getBean(name);
    }

    protected boolean isAjaxRequest(HttpServletRequest request) {
        if (!(request.getHeader("Accept").contains("application/json")
                || (request.getHeader("X-Requested-With") != null
                && request.getHeader("X-Requested-With").contains("XMLHttpRequest"))
                || "XMLHttpRequest".equalsIgnoreCase(request.getParameter("X_REQUESTED_WITH")))) {
            return false;
        }
        return true;
    }

    /**
     * 获取request
     */
    protected HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

    /**
     * 获取response
     */
    protected HttpServletResponse getResponse() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
    }

    /**
     * 获取session
     */
    protected HttpSession getSession() {
        return getRequest().getSession();
    }

    /**
     * 根据key获取request中属性值
     *
     * @param key 键
     */
    protected Object getRequestAttribute(String key) {
        return this.getRequest().getAttribute(key);
    }

    /**
     * 给request设置属性值
     *
     * @param key   键
     * @param value 值
     */
    protected void setRequestAttribute(String key, Object value) {
        this.getRequest().setAttribute(key, value);
    }

    /**
     * 文件上传（支持多个文件上传）
     */
    protected List<String> fileUpload(HttpServletRequest request) {

        List<String> list = new ArrayList<>();

        // 转换成多部分request
        MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest) request;
        // 取得request中的所有文件名
        Iterator<String> iterator = multiRequest.getFileNames();
        while (iterator.hasNext()) {
            // 取得上传文件
            MultipartFile file = multiRequest.getFile(iterator.next());
            if (null != file) {
                // 获取当前上传文件的文件名称
                String originalFileName = file.getOriginalFilename();
                // 如果名称不为空说明该文件存在，否则说明该文件不存在
                if (!"".equals(originalFileName)) {
                    long timestamp = DateUtil.getTimestamp();
                    String currentDate = DateUtil.formatLocalDateTime(DateUtil.timestampToLocalDateTime(timestamp), "yyyy/MM/dd");
                    // 获取文件后缀
                    String fileSuffix = originalFileName.substring(originalFileName.lastIndexOf("."));
                    // 重命名上传的文件名（格式：时间戳.后缀）
                    String fileName = timestamp + fileSuffix;
                    File dest = new File(fileUploadPath + "/upload/" + applicationName + "/" + currentDate, fileName);
                    // 判断文件父目录是否存在
                    if (!dest.getParentFile().exists()) {
                        dest.getParentFile().mkdirs();
                    }
                    try {
                        // 保存文件成功后记录上传后的名称
                        file.transferTo(dest);
                        list.add(fileName);
                    } catch (IOException e) {
                        throw new BusinessException(e.getMessage());
                    }
                }
            }
        }
        return list.isEmpty() ? null : list;
    }

    /**
     * 文件下载
     *
     * @param fileName 通过 upload 方法上传（文件名为时间戳）
     * @param response 响应对象
     */
    protected boolean fileDownload(String fileName, HttpServletResponse response) {

        if (StringUtils.isEmpty(fileName)) {
            return false;
        }

        // 获取文件名称（上传的时间戳）
        long filePrefix = Long.valueOf(fileName.substring(0, fileName.lastIndexOf(".")));
        String uploadDate = DateUtil.formatLocalDateTime(DateUtil.timestampToLocalDateTime(filePrefix), "yyyy/MM/dd");

        //设置文件路径
        String filePath = fileUploadPath + "/upload/" + applicationName + "/" + uploadDate;
        File file = new File(filePath, fileName);

        // 判断文件父目录是否存在
        if (file.exists()) {
            response.reset();
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/octet-stream");
            //response.setHeader("Content-Type", "application/octet-stream");
            // 设置文件名
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            byte[] buffer = new byte[1024];
            FileInputStream fis = null;
            BufferedInputStream bis = null;

            try {
                fis = new FileInputStream(file);
                bis = new BufferedInputStream(fis);
                OutputStream os = response.getOutputStream();
                int i = bis.read(buffer);

                while (i != -1) {
                    os.write(buffer, 0, i);
                    i = bis.read(buffer);
                }

                return true;
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                return false;
            } finally {
                if (null != bis) {
                    try {
                        bis.close();
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }
                if (null != fis) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }

        }
        return false;
    }
}
