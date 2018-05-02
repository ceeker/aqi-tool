package com.ceeker.app.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 配置管理
 *
 * @author ceeker
 * @create 2018/4/22 11:02
 **/
@Data
@Slf4j
@Singleton
public class ConfigManager {

    private final JSONObject config;

    protected ConfigManager() {
        config = loadConfig(Consts.CONFIG_FILE);
        log.debug("load config={}", config);
    }

    private JSONObject loadConfig(String fileName) {
        File file = getFile(fileName);
        JSONObject config = null;
        try {
            String configJson = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            config = JSON.parseObject(configJson);
            String aqiConfigJson = FileUtils.readFileToString(getFile(config.getString("aqiConfig")), StandardCharsets.UTF_8);
            config.put(Consts.API_CONFIG_KEY, JSON.parseObject(aqiConfigJson));
        } catch (IOException e) {
            log.error("loadConfig error,configPath=" + file, e);
        }
        return config;
    }

    private File getFile(String fileName) {
        String file = this.getClass().getClassLoader().getResource("config/" + fileName).getFile();
        return FileUtils.getFile(file);
    }

    public JSONObject getAqiConfig() {
        return config.getJSONObject(Consts.API_CONFIG_KEY);
    }

    public <T> T getConfig(String key) {
        return (T) config.get(key);
    }

}
