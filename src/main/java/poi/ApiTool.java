package poi;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class ApiTool {
    private static Map<Integer, String> headerMap = new HashMap<Integer, String>();
    public static final File RESULT = new File("result.xlsx");
    public static ConfigManager configManager = new ConfigManager();

    public void read() {
        String filePath = ApiTool.class.getClassLoader().getResource("data/today.xlsx").getFile();
        try (Workbook workbook = WorkbookFactory.create(new File(filePath));) {
            //工作表对象
            Sheet sheet = workbook.getSheetAt(0);
            //总行数
            int rowLength = sheet.getLastRowNum() + 1;
            Row header = sheet.getRow(0);
            handlerHeader(header);
            //总列数
            int colLength = header.getLastCellNum();
            //得到指定的单元格
            Row row = null;
            Cell cell = null;
            for (int rowIndex = 1; rowIndex < rowLength; rowIndex++) {
                row = sheet.getRow(rowIndex);
                double maxAqi = 0;
                String maxAqiAirName = "";
                for (int cowIndex = 5; cowIndex < colLength; cowIndex++) {
                    cell = row.getCell(cowIndex);
                    try {
                        cell.setCellType(CellType.STRING);
                        if (cell != null) {
                            String cellValueStr = cell.getStringCellValue();
                            double cellValue = 0;
                            if (!NumberUtils.isNumber(cellValueStr)) {
                                System.out.println(String.format("current row=%s,cow=%s,value=%s", rowIndex, cowIndex, cellValue));
                            } else {
                                cellValue = Double.parseDouble(cellValueStr);
                            }
                            int index = cowIndex + 8;
                            Cell newCell = row.createCell(index, CellType.NUMERIC);
                            double api = handleData(cowIndex, cellValue);
                            if (api > maxAqi) {
                                maxAqi = api;
                                maxAqiAirName = headerMap.get(cowIndex);
                            }
                            newCell.setCellValue(api);
                        }
                    } catch (Exception e) {
                        System.out.println(String.format("current row=%s,cow=%s", rowIndex, cowIndex));
                        e.printStackTrace();
                    }
                    Cell maxAqiCell = row.createCell(colLength + 8 + 1, CellType.NUMERIC);
                    maxAqiCell.setCellValue(maxAqi);

                    Cell maxAqiAirNameCell = row.createCell(colLength + 8 + 2, CellType.STRING);
                    maxAqiAirNameCell.setCellValue(maxAqiAirName);
                }
            }
            //将修改好的数据保存
            OutputStream out = new FileOutputStream(RESULT);
            workbook.write(out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlerHeader(Row header) {
        for (Cell cell : header) {
            headerMap.put(cell.getColumnIndex(), cell.getStringCellValue());
        }
    }

    public static void main(String[] args) {
        new ApiTool().read();
    }

    public double handleData(int index, double data) {
        double result = 0;
        String gasName = headerMap.get(index);
        JSONArray standardAqiArray = configManager.getConfig().getJSONArray(gasName);
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

}
