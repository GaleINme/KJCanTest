package com.example.kjcan.activities

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class ProductionData(
    val startTime: String,              // Column A
    val endTime: String,                // Column B
    val eventDuration: Double,          // Column C
    val eventType: String,              // Column D
    val downtimeType: String,           // Column E
    val category: String,               // Column F
    val remarks: String,                // Column G
    val jobId: String,                  // Column H
    val quantity: Double,               // Column I
    val thickness: Double,              // Column J
    val height: Double,                 // Column K
    val width: Double,                  // Column L
    val actualProduced: Double,         // Column M
    val motorSpeed: Double              // Column N
)

class ExcelManager(
    private val context: Context,
    private val shift: String
) {

    private lateinit var sourceFilePath: String
    private lateinit var destinationFilePath: String
    private var sourceWorkbook: Workbook? = null
    private var destinationWorkbook: Workbook? = null
    private val lastRowWrittenMap = mutableMapOf<String, Int>()

    init {
        setFilePaths(AppUtils.generateFileName(shift), AppUtils.generateFileName2(shift))
    }

    private fun setFilePaths(sourceFileName: String, destinationFileName: String) {
        sourceFilePath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            sourceFileName
        ).absolutePath

        destinationFilePath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            destinationFileName
        ).absolutePath
    }

    /**
     * Suspend function to transfer data.
     * Must be called from within a coroutine.
     */
    suspend fun transferData(sourceSheetName: String, destinationSheetName: String) {
        withContext(Dispatchers.IO) {
            try {
                loadWorkbooks()
                val productionDataList = readDataFromSource(sourceSheetName)
                if (productionDataList.isNotEmpty()) {
                    appendDataToFormattedDestination(destinationSheetName, productionDataList)
                    saveWorkbooks()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Data transfer completed successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "No data found in the source sheet.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ExcelManager", "Error transferring data: ${e.localizedMessage}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error transferring data: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                closeWorkbooks()
            }
        }
    }

    private fun loadWorkbooks() {
        try {
            sourceWorkbook = FileInputStream(File(sourceFilePath)).use { fis ->
                WorkbookFactory.create(fis)
            }

            destinationWorkbook = if (File(destinationFilePath).exists()) {
                FileInputStream(File(destinationFilePath)).use { fis ->
                    WorkbookFactory.create(fis)
                }
            } else {
                XSSFWorkbook()
            }
        } catch (e: Exception) {
            Log.e("ExcelManager", "Error loading workbooks: ${e.localizedMessage}", e)
            throw e
        }
    }

    private fun closeWorkbooks() {
        try {
            sourceWorkbook?.close()
            destinationWorkbook?.close()
        } catch (e: Exception) {
            Log.e("ExcelManager", "Error closing workbooks: ${e.localizedMessage}", e)
        }
    }

    private fun readDataFromSource(sheetName: String): List<ProductionData> {
        val productionDataList = mutableListOf<ProductionData>()
        try {
            val sheet = sourceWorkbook?.getSheet(sheetName)
                ?: throw IllegalArgumentException("Source sheet not found: $sheetName")
            sheet.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) { // Skip header row
                    productionDataList.add(
                        ProductionData(
                            startTime = row.getCell(0)?.stringCellValue ?: "",
                            endTime = row.getCell(1)?.stringCellValue ?: "",
                            eventDuration = getCellNumericValue(row.getCell(2)),
                            eventType = row.getCell(3)?.stringCellValue ?: "",
                            downtimeType = row.getCell(4)?.stringCellValue ?: "",
                            category = row.getCell(5)?.stringCellValue ?: "",
                            remarks = row.getCell(6)?.stringCellValue ?: "",
                            jobId = row.getCell(7)?.stringCellValue ?: "",
                            quantity = getCellNumericValue(row.getCell(8)),
                            thickness = getCellNumericValue(row.getCell(9)),
                            height = getCellNumericValue(row.getCell(10)),
                            width = getCellNumericValue(row.getCell(11)),
                            actualProduced = getCellNumericValue(row.getCell(12)),
                            motorSpeed = getCellNumericValue(row.getCell(13))
                        )
                    )
                }
            }
            Log.d("ExcelManager", "Production data: $productionDataList")
        } catch (e: Exception) {
            Log.e("ExcelManager", "Error reading data from source: ${e.localizedMessage}", e)
            throw e
        }
        return productionDataList
    }

    private fun getCellNumericValue(cell: Cell?): Double {
        return when (cell?.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun appendDataToFormattedDestination(sheetName: String, data: List<ProductionData>) {
        try {
            val sheet = destinationWorkbook?.getSheet(sheetName)
                ?: destinationWorkbook?.createSheet(sheetName)
                ?: throw IllegalArgumentException("Unable to create or access sheet: $sheetName")

            val destinationStartRow = 8 // Row 9 in Excel is index 8
            var maxRowIndex = ExcelStateManager.getMaxRowIndex(sheetName)

            // Get the last row written for this sheet from the map or initialize it
            val lastRowWritten =
                lastRowWrittenMap.getOrPut(sheetName) { destinationStartRow - 1 }

            // Start writing from the row after the last written row
            var currentRowIndex = lastRowWritten + 1

            data.forEach { productionData ->
                // Dynamically update `maxRowIndex` before cloning rows
                if (currentRowIndex > maxRowIndex) {
                    maxRowIndex++
                    insertNewRowWithFormatting(
                        sheet,
                        23, // Reference row index (assuming row 24 has desired formatting)
                        currentRowIndex
                    )
                    ExcelStateManager.updateMaxRowIndex(
                        sheetName,
                        maxRowIndex
                    ) // Update global state
                }

                // Write data to the row
                val row = sheet.getRow(currentRowIndex) ?: sheet.createRow(currentRowIndex)
                writeFormattedDataToRow(row, productionData)

                Log.d("ExcelManager", "Data written to row ${currentRowIndex + 1}.")
                currentRowIndex++
            }

            // Update the last row written for this sheet in the map
            lastRowWrittenMap[sheetName] = currentRowIndex - 1

        } catch (e: Exception) {
            Log.e("ExcelManager", "Error appending formatted data: ${e.localizedMessage}", e)
            throw e
        }
    }

    private fun insertNewRowWithFormatting(
        sheet: Sheet,
        referenceRowIndex: Int,
        targetRowIndex: Int
    ) {
        val referenceRow = sheet.getRow(referenceRowIndex)
            ?: throw IllegalStateException("Reference row not found at index: $referenceRowIndex")

        // Shift rows down to make space for the new row
        sheet.shiftRows(targetRowIndex, sheet.lastRowNum, 1)

        // Create the new row
        val targetRow = sheet.createRow(targetRowIndex)
        targetRow.height = referenceRow.height // Copy row height

        // Clone each cell's formatting and style
        for (cellIndex in referenceRow.firstCellNum until referenceRow.lastCellNum) {
            val referenceCell = referenceRow.getCell(cellIndex)
            if (referenceCell != null) {
                val targetCell = targetRow.createCell(cellIndex)

                // Copy cell style
                val newCellStyle = sheet.workbook.createCellStyle()
                newCellStyle.cloneStyleFrom(referenceCell.cellStyle)
                targetCell.cellStyle = newCellStyle

                // Copy font
                val fontIndex = referenceCell.cellStyle.fontIndex
                val font = sheet.workbook.getFontAt(fontIndex)
                val newFont = sheet.workbook.createFont().apply {
                    fontName = font.fontName
                    fontHeightInPoints = font.fontHeightInPoints
                    bold = font.bold
                    italic = font.italic
                    underline = font.underline
                    strikeout = font.strikeout
                }
                newCellStyle.setFont(newFont)

                // Clear the cell content
                targetCell.setCellValue("")
            }
        }

        Log.d(
            "ExcelManager",
            "Inserted new row at ${targetRowIndex + 1} with formatting cloned from row ${referenceRowIndex + 1}."
        )
    }

    private fun writeFormattedDataToRow(row: Row, data: ProductionData) {
        Log.d("ExcelManager", "Writing $data to row ${row.rowNum + 1}.")
        // Column A: Start time ~ End time
        updateCell(row, 0, "${data.startTime} ~ ${data.endTime}")

        when (data.eventType) {
            "Production" -> {
                // Column B: Duration
                updateCell(row, 1, data.eventDuration)

                // Column V ~ X: Production details
                val productionDetails =
                    "Job ID: ${data.jobId}, Dimensions: ${data.thickness} x ${data.height} x ${data.width}, Qty: ${data.quantity}, Speed: ${data.motorSpeed}" +
                            if (data.remarks.isNotEmpty()) ", Details: ${data.remarks}" else ""
                updateCell(row, 21, productionDetails)

                // Ensure other unused columns are empty
                for (col in 11 until 21) { // Columns L to U
                    updateCell(row, col, "")
                }
            }

            "Downtime" -> {
                when (data.downtimeType) {
                    "Planned" -> {
                        // Column C ~ K: Duration for specific categories
                        val categoryKey = extractCategoryKey(data.category)
                        val categoryMap = mapOf(
                            "MB" to 2, "MS" to 3, "CO" to 4, "CC" to 5,
                            "CP" to 6, "TB" to 7, "CIC" to 8, "LSD" to 9, "WU" to 10
                        )
                        // First, clear all category duration columns to avoid duplicates
                        for (col in 2..10) { // Columns B to J
                            updateCell(row, col, "")
                        }

                        categoryMap[categoryKey]?.let { colIndex ->
                            updateCell(row, colIndex, data.eventDuration)
                        }

                        // Column V ~ X: Remarks
                        if (data.remarks.isNotEmpty()) {
                            updateCell(row, 21, "Details: ${data.remarks}")
                        } else {
                            // Clear remarks if empty
                            updateCell(row, 21, "")
                        }

                        // Ensure other unused columns are empty
                        for (col in 11 until 21) { // Columns L to U
                            updateCell(row, col, "")
                        }
                    }

                    "Unplanned" -> {
                        // Column L ~ N: Category and remarks
                        val unplannedDetails = "Category: ${data.category}" +
                                if (data.remarks.isNotEmpty()) ", Details: ${data.remarks}" else ""
                        updateCell(row, 11, unplannedDetails)

                        // Column O: Duration
                        updateCell(row, 14, data.eventDuration)

                        // Ensure other unused columns are empty
                        for (col in 0 until 11) { // Columns A to K
                            if (col != 0) { // Skip Column A which is already set
                                updateCell(row, col, "")
                            }
                        }
                        for (col in 15 until 21) { // Columns P to U
                            updateCell(row, col, "")
                        }
                    }

                    "Special" -> {
                        // Column R ~ T: Reason and remarks
                        val specialDetails = "Reason: ${data.category}" +
                                if (data.remarks.isNotEmpty()) ", Details: ${data.remarks}" else ""
                        updateCell(row, 17, specialDetails)

                        // Column U: Duration
                        updateCell(row, 20, data.eventDuration)

                        // Ensure other unused columns are empty
                        for (col in 0 until 17) { // Columns A to Q
                            if (col != 0) { // Skip Column A which is already set
                                updateCell(row, col, "")
                            }
                        }
                        for (col in 18 until 20) { // Columns S to T
                            updateCell(row, col, "")
                        }
                    }
                }
            }

            else -> {
                Log.e("ExcelManager", "Unhandled event type: '${data.eventType}'")
                // Optionally, clear all columns except A
                for (col in 1 until 21) { // Columns B to U
                    updateCell(row, col, "")
                }
            }
        }
    }

    private fun extractCategoryKey(category: String): String {
        Log.d("ExcelManager", "Extracting category key from: $category")
        // Split the category string by ":" and take the first part (the key)
        return category.split(":").firstOrNull()?.trim() ?: ""
    }

    private fun updateCell(row: Row, columnIndex: Int, value: Any?) {
        val cell = row.getCell(columnIndex) ?: row.createCell(columnIndex)
        when (value) {
            is String -> cell.setCellValue(value)
            is Double -> cell.setCellValue(value)
            is Int -> cell.setCellValue(value.toDouble())
            else -> cell.setCellValue(value?.toString() ?: "")
        }
        Log.d(
            "ExcelManager",
            "Updated cell at row ${row.rowNum + 1}, column $columnIndex with value: $value"
        )
    }

    private fun saveWorkbooks() {
        try {
            // Save source workbook if needed
            sourceWorkbook?.let { workbook ->
                FileOutputStream(File(sourceFilePath)).use { fos ->
                    workbook.write(fos)
                }
                Log.d("ExcelManager", "Source workbook saved successfully.")
            }

            // Save destination workbook
            destinationWorkbook?.let { workbook ->
                FileOutputStream(File(destinationFilePath)).use { fos ->
                    workbook.write(fos)
                }
                Log.d("ExcelManager", "Destination workbook saved successfully to $destinationFilePath.")
            }
        } catch (e: Exception) {
            Log.e("ExcelManager", "Error saving workbooks: ${e.localizedMessage}", e)
            throw e
        }
    }
}

object ExcelStateManager {
    val maxRowIndexMap = mutableMapOf<String, Int>()

    fun getMaxRowIndex(sheetName: String): Int {
        return maxRowIndexMap[sheetName] ?: 47 // Default to row 48 (index 47)
    }

    fun updateMaxRowIndex(sheetName: String, newMaxRowIndex: Int) {
        maxRowIndexMap[sheetName] = newMaxRowIndex
    }

    fun resetMaxRowIndex(sheetName: String) {
        maxRowIndexMap[sheetName] = 47 // Reset to initial max
    }
}
