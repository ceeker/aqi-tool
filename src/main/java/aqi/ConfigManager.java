package aqi;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * 配置管理
 *
 * @author ceeker
 * @create 2018/4/22 11:02
 **/
@Data
public class ConfigManager{
    private JSONObject config = null;
    public static final String CONFIG_PATH = "config/meta.json";

    public ConfigManager() {
        loadConfig(CONFIG_PATH);
        System.out.println("load config succ,config=" + config);
    }

    private void loadConfig(String configPath) {
        String file = ConfigManager.class.getClassLoader().getResource(CONFIG_PATH).getFile();
        String aqiStandardConfig = null;
        try {
            aqiStandardConfig = FileUtils.readFileToString(new File(file), "utf-8");
            this.config = JSON.parseObject(aqiStandardConfig);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
