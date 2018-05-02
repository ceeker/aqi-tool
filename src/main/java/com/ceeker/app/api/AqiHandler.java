package com.ceeker.app.api;

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
    //数据开始的行下标
    private final int START_ROW;
    //数据开始的列下标
    private final int START_COL;
    //计算结果列下标的偏移量
    private final int RESULT_COL_OFFSET;
    private ConfigManager configManager;
    private final String DATA_FILES = "2017南油日均值.xls";
    private Collection<File> dataFiles;
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
     * 计算api
     */
    public void calculateAqi() {
        if (CollectionUtils.isEmpty(dataFiles)) {
            log.warn("dataFiles is empty,cancel calculateAqi!");
            return;
        }
        log.info("---------- start calculateAqi,{} files had be found---------", dataFiles.size());
        for (File dataFile : dataFiles) {
//            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("data/" + DATA_FILES);
            //会把文件名中的中文进行urlencode
//        String filePath = this.getClass().getClassLoader().getResource("data/" + Data_fils).getFile();
//            Workbook workbook = WorkbookFactory.create(inputStream)
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
                    for (int cowIndex = START_COL; cowIndex < colLength; cowIndex++) {
                        cell = row.getCell(cowIndex);
                        try {
                            if (cell != null) {
                                cell.setCellType(CellType.STRING);
                                String cellValueStr = cell.getStringCellValue();
                                double cellValue = 0;
                                if (!NumberUtils.isCreatable(cellValueStr)) {
                                    System.out.println(String.format("current row=%s,cow=%s,value=%s", rowIndex, cowIndex, cellValue));
                                } else {
                                    cellValue = Double.parseDouble(cellValueStr);
                                }
                                int index = cowIndex + RESULT_COL_OFFSET;
                                Cell newCell = row.createCell(index, CellType.NUMERIC);
                                double aqi = handleData(cowIndex, cellValue);
                                if (aqi > maxAqi) {
                                    maxAqi = aqi;
                                    maxAqiAirName = HEADER_MAP.get(cowIndex);
                                }
                                newCell.setCellValue(aqi);
                            }
                        } catch (Exception e) {
                            System.out.println(String.format("current row=%s,cow=%s", rowIndex, cowIndex));
                            e.printStackTrace();
                        }
                        //QAI结果
                        Cell aqiCell = row.createCell(colLength + RESULT_COL_OFFSET + 1, CellType.NUMERIC);
                        aqiCell.setCellValue(maxAqi);
                        //首要污染物
                        Cell primaryPollutantCell = row.createCell(colLength + RESULT_COL_OFFSET + 2, CellType.STRING);
                        primaryPollutantCell.setCellValue(maxAqiAirName);
                    }
                }
                //将修改好的数据保存
                OutputStream out = new FileOutputStream(getResultFile(DATA_FILES));
                workbook.write(out);
            } catch (Exception e) {
                log.error("error", e);
            }
        }
    }

    private void handleHeader(Row header) {
        //总列数
        int colLength = header.getLastCellNum();
        for (int cowIndex = START_COL; cowIndex < colLength; cowIndex++) {
            Cell cell = header.getCell(cowIndex);
            HEADER_MAP.put(cell.getColumnIndex(), cell.getStringCellValue());
            addHeaderCol(header, cell);
        }
    }

    private void addHeaderCol(Row header, Cell cell) {
        int columnIndex = cell.getColumnIndex();
        String cellValue = cell.getStringCellValue();
        if (StringUtils.isNotBlank(cellValue)) {
            int aqiNameIndex = columnIndex + RESULT_COL_OFFSET;
            String aqiName = "AQI_" + cellValue;
            Cell apiNameHeaderCell = header.createCell(aqiNameIndex, CellType.STRING);
            apiNameHeaderCell.setCellValue(aqiName);
        }

    }

    private double handleData(int index, double data) {
        double result = 0;
        String gasName = HEADER_MAP.get(index);
        JSONArray standardAqiArray = configManager.getAqiConfig().getJSONArray(gasName);
        if (null == standardAqiArray || standardAqiArray.isEmpty()) {
            System.err.println(String.format("gasName=%s config is null or empty", gasName));
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

    private File getResultFile(String sourceFile) {
        return new File("result/result_" + sourceFile);
    }

}
