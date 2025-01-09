package com.example.kjcan.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.kjcan.activities.AppUtils
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class EventData(
    var startTime: String = "N/A",
    var endTime: String = "N/A",
    var eventDuration: String = "N/A",
    var eventType: String = "N/A",
    var downtimeType: String = "N/A",
    var category: String = "N/A",
    var remarks: String = "",
    var jobId: String = "",
    var quantity: String = "",
    var thickness: String = "",
    var height: String = "",
    var width: String = "",
    var actualProduced: String = "",
    var motorSpeed: String = ""
) {
    fun toList(): List<String> {
        return listOf(
            startTime, endTime, eventDuration, eventType,
            downtimeType, category, remarks,
            jobId, quantity, thickness, height, width,
            actualProduced, motorSpeed
        )
    }
}

class ExcelSorter(private val context: Context, private val shift: String) {

    private val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    /**
     * Sorts the Excel file by the "Start Time" column.
     * @param sheetName Name of the sheet to sort.
     * @param startTimeColumnIndex The zero-based index of the "Start Time" column.
     */
    fun sortExcelByStartTime(sheetName: String = "ProductionData", startTimeColumnIndex: Int = 0) {
        val fileName = AppUtils.generateFileName(shift)
        val filePath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            fileName
        )

        if (!filePath.exists()) {
            Log.e("ExcelSorter", "Excel file does not exist: ${filePath.absolutePath}")
            return
        }

        try {
            // Open the workbook
            FileInputStream(filePath).use { fis ->
                val workbook = WorkbookFactory.create(fis) as XSSFWorkbook
                val sheet = workbook.getSheet(sheetName) ?: run {
                    Log.e("ExcelSorter", "Sheet '$sheetName' does not exist.")
                    workbook.close()
                    return
                }

                // Read the header row
                val headerRow = sheet.getRow(0) ?: run {
                    Log.e("ExcelSorter", "Header row is missing.")
                    workbook.close()
                    return
                }

                // Read all data rows into a list of EventData
                val eventDataList = mutableListOf<EventData>()
                val rowIterator = sheet.iterator()
                if (rowIterator.hasNext()) {
                    rowIterator.next() // Skip header
                }

                while (rowIterator.hasNext()) {
                    val row = rowIterator.next()
                    val eventData = rowToEventData(row, startTimeColumnIndex)
                    if (eventData != null) {
                        eventDataList.add(eventData)
                    } else {
                        Log.w("ExcelSorter", "Skipping row ${row.rowNum} due to parsing issues.")
                    }
                }

                Log.d("ExcelSorter", "Total rows read for sorting: ${eventDataList.size}")

                // Sort the list based on startTime
                eventDataList.sortWith { a, b ->
                    val timeA = parseTime(a.startTime)
                    val timeB = parseTime(b.startTime)
                    when {
                        timeA == null && timeB == null -> 0
                        timeA == null -> -1
                        timeB == null -> 1
                        else -> timeA.compareTo(timeB)
                    }
                }

                Log.d("ExcelSorter", "Data sorted based on Start Time.")

                // Clear existing data rows (retain header)
                val lastRowNum = sheet.lastRowNum
                for (i in lastRowNum downTo 1) {
                    val row = sheet.getRow(i)
                    if (row != null) {
                        sheet.removeRow(row)
                    }
                }

                Log.d("ExcelSorter", "Existing data rows cleared.")

                // Write sorted data back to the sheet
                for ((index, eventData) in eventDataList.withIndex()) {
                    val newRow = sheet.createRow(index + 1) // +1 to retain header
                    eventDataToRow(eventData, newRow)
                }

                Log.d("ExcelSorter", "Sorted data written back to the sheet.")

                // Save the workbook
                FileOutputStream(filePath).use { fos ->
                    workbook.write(fos)
                }
                workbook.close()

                Log.d("ExcelSorter", "Excel file sorted by Start Time successfully.")
            }
        } catch (e: Exception) {
            Log.e("ExcelSorter", "Error sorting Excel file: ${e.localizedMessage}")
            e.printStackTrace()
        }
    }

    /**
     * Converts a Row to an EventData object.
     * @param row The Excel row.
     * @param startTimeColumnIndex The index of the Start Time column.
     * @return EventData object or null if parsing fails.
     */
    private fun rowToEventData(row: Row, startTimeColumnIndex: Int): EventData? {
        return try {
            // Adjust the column indices based on your Excel sheet structure
            EventData(
                startTime = getCellValue(row.getCell(startTimeColumnIndex)),
                endTime = getCellValue(row.getCell(startTimeColumnIndex + 1)), // Example: End Time is next column
                eventDuration = getCellValue(row.getCell(startTimeColumnIndex + 2)),
                eventType = getCellValue(row.getCell(startTimeColumnIndex + 3)),
                downtimeType = getCellValue(row.getCell(startTimeColumnIndex + 4)),
                category = getCellValue(row.getCell(startTimeColumnIndex + 5)),
                remarks = getCellValue(row.getCell(startTimeColumnIndex + 6)),
                jobId = getCellValue(row.getCell(startTimeColumnIndex + 7)),
                quantity = getCellValue(row.getCell(startTimeColumnIndex + 8)),
                thickness = getCellValue(row.getCell(startTimeColumnIndex + 9)),
                height = getCellValue(row.getCell(startTimeColumnIndex + 10)),
                width = getCellValue(row.getCell(startTimeColumnIndex + 11)),
                actualProduced = getCellValue(row.getCell(startTimeColumnIndex + 12)),
                motorSpeed = getCellValue(row.getCell(startTimeColumnIndex + 13))
            )
        } catch (e: Exception) {
            Log.e("ExcelSorter", "Error parsing row ${row.rowNum}: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Writes an EventData object to a Row.
     * @param eventData The data to write.
     * @param row The Excel row.
     */
    private fun eventDataToRow(eventData: EventData, row: Row) {
        val dataList = eventData.toList()
        for ((index, value) in dataList.withIndex()) {
            val cell = row.createCell(index, CellType.STRING)
            cell.setCellValue(value)
        }
    }

    /**
     * Retrieves the string value of a cell.
     * @param cell The Excel cell.
     * @return The cell's string value.
     */
    private fun getCellValue(cell: Cell?): String {
        return when (cell?.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    dateFormat.format(cell.dateCellValue)
                } else {
                    // If numeric but not a date, format without decimal
                    if (cell.numericCellValue % 1 == 0.0) {
                        cell.numericCellValue.toInt().toString()
                    } else {
                        cell.numericCellValue.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.cellFormula
            else -> ""
        }
    }

    /**
     * Parses a time string into minutes since midnight.
     * @param timeStr The time string.
     * @return Minutes since midnight or null if parsing fails.
     */
    private fun parseTime(timeStr: String): Int? {
        return try {
            val date = dateFormat.parse(timeStr)
            val calendar = Calendar.getInstance()
            if (date != null) {
                calendar.time = date
                calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ExcelSorter", "Error parsing time '$timeStr': ${e.localizedMessage}")
            null
        }
    }
}
