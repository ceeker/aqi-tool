package com.ceeker.app.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

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
    private Collection<File> dataFiles;

    protected ConfigManager() {
        config = loadConfig(Consts.CONFIG_FILE);
        log.debug("load config={}", config);
    }

    private JSONObject loadConfig(String filePath) {
        File file = getFile(filePath);
        JSONObject config = null;
        try {
            config = JSON.parseObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
            String aqiConfigJson = FileUtils.readFileToString(getFile(config.getString("aqiConfig")), StandardCharsets.UTF_8);
            config.put(Consts.API_CONFIG_KEY, JSON.parseObject(aqiConfigJson));
            String dataPath = config.getString(Consts.DATA_PATH);
            dataFiles = getDataFiles(dataPath);
        } catch (IOException e) {
            log.error("loadConfig error,configPath=" + file, e);
        }
        return config;
    }

    private File getFile(String filePath) {
        String file = this.getClass().getClassLoader().getResource(filePath).getFile();
        return FileUtils.getFile(file);
    }

    private Collection<File> getDataFiles(String dataPath) {
        Collection<File> dataFiles = Lists.newArrayList();
        File dataFilesDir = getFile(dataPath);
        if (dataFilesDir.exists() && dataFilesDir.isDirectory()) {
            String[] extensionName = {"xls", "xlsx"};
            dataFiles = FileUtils.listFiles(dataFilesDir, extensionName, true);
        }
        return dataFiles;
    }

    public JSONObject getAqiConfig() {
        return config.getJSONObject(Consts.API_CONFIG_KEY);
    }

    public <T> T getConfig(String key) {
        return (T) config.get(key);
    }

}
