package com.ceeker.app.aqi;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.usermodel.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Singleton
public class AqiHandler {
    /**
     * 数据开始的行下标
     */
    private final int START_ROW;
    /**
     * 数据开始的列下标
     */
    private final int START_COL;
    /**
     * 计算结果列下标的偏移量
     */
    private final int RESULT_COL_OFFSET;
    /**
     * 配置管理器
     */
    private ConfigManager configManager;
    /**
     * 要计算AQI的数据文件
     */
    private Collection<File> dataFiles;
    /**
     * 数据文件表头 key:col_index;value:col_value
     */
    private final static Map<Integer, String> HEADER_MAP = Maps.newHashMap();

    @Inject
    public AqiHandler(ConfigManager configManager) {
        this.configManager = configManager;
        this.START_ROW = configManager.getConfig(Consts.DATA_START_ROW);
        this.START_COL = configManager.getConfig(Consts.DATA_START_COL);
        this.RESULT_COL_OFFSET = configManager.getConfig(Consts.RESULT_COL_OFFSET);
        this.dataFiles = configManager.getDataFiles();
    }

    /**
     * 处理数据文件
     */
    public void handleDataFiles() {
        if (CollectionUtils.isEmpty(dataFiles)) {
            log.warn("dataFiles is empty,cancel handleDataFiles!");
            return;
        }
        log.info("---------- start handleDataFiles,{} files had be found---------", dataFiles.size());
        for (File dataFile : dataFiles) {
            //会把文件名中的中文进行urlencode
//            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("data/" + DATA_FILES);
//        String filePath = this.getClass().getClassLoader().getResource("data/" + Data_fils).getFile();
            try (Workbook workbook = WorkbookFactory.create(dataFile)) {
                //工作表对象
                Sheet sheet = workbook.getSheetAt(0);
                //总行数
                int rowLength = sheet.getLastRowNum() + 1;
                Row header = sheet.getRow(0);
                //总列数
                int colLength = header.getLastCellNum();
                handleHeader(header);
                //得到指定的行
                Row row;
                //得到指定的单元格
                Cell cell;
                for (int rowIndex = START_ROW; rowIndex < rowLength; rowIndex++) {
                    row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }
                    double maxAqi = 0;
                    String maxAqiAirName = "";
                    for (int colIndex = START_COL; colIndex < colLength; colIndex++) {
                        cell = row.getCell(colIndex);
                        try {
                            if (cell != null) {
                                cell.setCellType(CellType.STRING);
                                String cellValueStr = cell.getStringCellValue();
                                double cellValue = 0;
                                if (!NumberUtils.isCreatable(cellValueStr)) {
                                    log.warn("is not number,row={},cow={},value={}", rowIndex, colIndex, cellValue);
                                    continue;
                                }
                                cellValue = Double.parseDouble(cellValueStr);
                                int resultColindex = colIndex + RESULT_COL_OFFSET;
                                Cell aqiCell = row.createCell(resultColindex, CellType.NUMERIC);
                                double aqi = calculateAqi(colIndex, cellValue);
                                aqiCell.setCellValue(aqi);
                                if (aqi > maxAqi) {
                                    maxAqi = aqi;
                                    maxAqiAirName = HEADER_MAP.get(colIndex);
                                }
                            }
                        } catch (Exception e) {
                            log.error(String.format("error,current row=%s,cow=%s", rowIndex, colIndex), e);
                        }
                        //AQI结果
                        Cell aqiCell = row.createCell(colLength + RESULT_COL_OFFSET + 1, CellType.NUMERIC);
                        aqiCell.setCellValue(maxAqi);
                        //首要污染物
                        Cell primaryPollutantCell = row.createCell(colLength + RESULT_COL_OFFSET + 2, CellType.STRING);
                        primaryPollutantCell.setCellValue(maxAqiAirName);
                    }
                }
                //将修改好的数据保存
                OutputStream out = new FileOutputStream(getResultFile(dataFile.getName()));
                workbook.write(out);
            } catch (Exception e) {
                log.error("handleDataFiles error,file=" + dataFile, e);
            }
        }
        log.info("handleDataFiles finsh");
    }

    private void handleHeader(Row header) {
        //总列数
        int colLength = header.getLastCellNum();
        for (int cowIndex = START_COL; cowIndex < colLength; cowIndex++) {
            Cell cell = header.getCell(cowIndex);
            HEADER_MAP.put(cell.getColumnIndex(), cell.getStringCellValue());
            addAqiHeaderCol(header, cell);
        }
    }

    private void addAqiHeaderCol(Row header, Cell cell) {
        int columnIndex = cell.getColumnIndex();
        String cellValue = cell.getStringCellValue();
        if (StringUtils.isNotBlank(cellValue)) {
            int aqiNameIndex = columnIndex + RESULT_COL_OFFSET;
            String aqiName = "AQI_" + cellValue;
            Cell aqiNameHeaderCell = header.createCell(aqiNameIndex, CellType.STRING);
            aqiNameHeaderCell.setCellValue(aqiName);
        }
    }

    /**
     * 计算api
     *
     * @param index
     * @param data
     * @return
     */
    private double calculateAqi(int index, double data) {
        double result = 0;
        String gasName = HEADER_MAP.get(index);
        JSONArray standardAqiArray = configManager.getAqiConfig().getJSONArray(gasName);
        if (null == standardAqiArray || standardAqiArray.isEmpty()) {
            log.warn("-------gasName={} config is null or empty--------", gasName);
            return result;
        }
        for (int i = 0; i < standardAqiArray.size(); i++) {
            JSONObject standardIndex = (JSONObject) standardAqiArray.get(i);
            double hourAvg = standardIndex.getDoubleValue("hourAvg");
            if (data <= hourAvg) {
                if (i == 0) {
                    break;
                }
                JSONObject previous = (JSONObject) standardAqiArray.get(i - 1);
                result = calculateAqi(previous, standardIndex, data);
                break;
            }
        }
        return result;
    }

    private double calculateAqi(JSONObject previous, JSONObject next, double currentData) {
        double previousHourAvg = previous.getDoubleValue("hourAvg");
        double previousAqi = previous.getDoubleValue("aqi");
        double nextHourAvg = next.getDoubleValue("hourAvg");
        double nextAqi = next.getDoubleValue("aqi");
        return (nextAqi - previousAqi) / (nextHourAvg - previousHourAvg) * (currentData - previousHourAvg) + previousAqi;
    }

    private File getResultFile(String originalFileName) {
        return new File("result/" + configManager.getConfig(Consts.RESULT_FILE_PREFIX) + originalFileName);
    }

}
