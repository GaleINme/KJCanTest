package com.example.kjcan.activities

import android.content.Context
import android.os.Environment
import android.util.Log
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

@Suppress("UNCHECKED_CAST")
class ChangeOverExcel(private val context: Context) {

    private val templateFileName = "template2.xlsm"
    private val directoryPath: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    fun generateExcelFile(data: Map<String, Any>): Boolean {
        val outputFileName = generateFileName()
        val outputFile = File(directoryPath, "$outputFileName.xlsm")

        if (outputFile.exists()) {
            Log.d("ChangeOverExcel", "File already exists: $outputFileName.xlsm")
            return true
        }

        return try {
            val inputStream = context.assets.open(templateFileName)
            val workbook = WorkbookFactory.create(inputStream) as XSSFWorkbook
            inputStream.close()

            val sheet1 = workbook.getSheet("Sheet1") ?: return logError("Sheet1 not found")
            val sheet2 = workbook.getSheet("Sheet2") ?: return logError("Sheet2 not found")

            // Populate values in Sheet1
            setCellIfPresent(sheet1, 6, 2, AppUtils.getCurrentDate()) // C7
            setCellIfPresent(sheet1, 6, 8, getTeamFromPreferences()) // J7

            // Write product status
            val productStatus = data["product_status"] as? String
            setCellIfPresent(sheet1, 9, 2, productStatus) // C10

            // Populate linked cells in Sheet2
            populateLinkedCells(sheet2, data["product_detail"] as? Map<String, Boolean> ?: emptyMap(), 12, 0)
            populateLinkedCells(sheet2, data["passes"] as? Map<String, Boolean> ?: emptyMap(), 19, 1)

            // Write start/end times in Sheet1
            val passesTimeMap = data["passes_time"] as? Map<String, Pair<String, String>> ?: emptyMap()
            var index = 0
            for ((_, times) in passesTimeMap) {
                val row = sheet1.getRow(19 + index) ?: sheet1.createRow(19 + index)
                row.getCell(2)?.setCellValue(times.first)
                row.getCell(4)?.setCellValue(times.second)
                index++
            }

            // Proof Process
            val proofProcessMap = data["proof_process"] as? Map<String, Boolean> ?: emptyMap()
            proofProcessMap.forEach { (key, value) ->
                when (key) {
                    "Draft Printing Process" -> setCellIfPresent(sheet2, 26, 3, if (value) "1" else "")
                    "Customer Proof" -> setCellIfPresent(sheet2, 27, 3, if (value) "1" else "")
                    "Progressive Proof" -> setCellIfPresent(sheet2, 28, 3, if (value) "1" else "")
                    "Printing Process" -> setCellIfPresent(sheet2, 26, 6, if (value) "1" else "")
                    "Customer Sample Can" -> setCellIfPresent(sheet2, 27, 6, if (value) "1" else "")
                    "Lacquering Process" -> setCellIfPresent(sheet2, 26, 9, if (value) "1" else "")
                    "Digital Proof" -> setCellIfPresent(sheet2, 27, 9, if (value) "1" else "")
                }
            }

// Outgoing Material
            val outgoingMaterialMap = data["outgoing_material"] as? Map<String, Boolean> ?: emptyMap()
            outgoingMaterialMap.forEach { (key, value) ->
                when (key) {
                    "Ink" -> setCellIfPresent(sheet2, 33, 3, if (value) "1" else "")
                    "Lacquer" -> setCellIfPresent(sheet2, 34, 3, if (value) "1" else "")
                    "Varnish" -> setCellIfPresent(sheet2, 35, 3, if (value) "1" else "")
                    "Printing Plate" -> setCellIfPresent(sheet2, 36, 3, if (value) "1" else "")
                    "Printing Process" -> setCellIfPresent(sheet2, 33, 6, if (value) "1" else "")
                    "Draft Printing Process" -> setCellIfPresent(sheet2, 34, 6, if (value) "1" else "")
                    "Lacquering Process" -> setCellIfPresent(sheet2, 35, 6, if (value) "1" else "")
                    "LSD Tinplate" -> setCellIfPresent(sheet2, 36, 6, if (value) "1" else "")
                    "Digital Proof" -> setCellIfPresent(sheet2, 33, 9, if (value) "1" else "")
                    "Progressive Proof" -> setCellIfPresent(sheet2, 34, 9, if (value) "1" else "")
                    "Customer Proof" -> setCellIfPresent(sheet2, 35, 9, if (value) "1" else "")
                    "Miss Printed Sheet" -> setCellIfPresent(sheet2, 36, 9, if (value) "1" else "")
                }
            }

// Changeover Result
            val changeoverResultMap = data["changeover_result"] as? Map<String, Boolean> ?: emptyMap()
            changeoverResultMap.forEach { (key, value) ->
                when (key) {
                    "Success" -> setCellIfPresent(sheet2, 42, 2, if (value) "1" else "")
                    "Fail" -> setCellIfPresent(sheet2, 42, 7, if (value) "1" else "")
                }
            }

            // Save the file
            val fos = FileOutputStream(outputFile)
            workbook.write(fos)
            workbook.close()
            fos.close()

            Log.d("ChangeOverExcel", "File created successfully: $outputFileName")
            true
        } catch (e: Exception) {
            Log.e("ChangeOverExcel", "Error creating Excel file: ${e.localizedMessage}")
            false
        }
    }

    private fun logError(message: String): Boolean {
        Log.e("ChangeOverExcel", message)
        return false
    }

    private fun setCellIfPresent(sheet: org.apache.poi.ss.usermodel.Sheet, rowIdx: Int, colIdx: Int, value: String?) {
        value?.let {
            val row = sheet.getRow(rowIdx) ?: sheet.createRow(rowIdx)
            val cell = row.getCell(colIdx) ?: row.createCell(colIdx)
            cell.setCellValue(it)
        }
    }

    private fun populateLinkedCells(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        dataMap: Map<String, Boolean>,
        startRow: Int,
        column: Int
    ) {
        var currentRow = startRow
        dataMap.forEach { (key, value) ->
            val row = sheet.getRow(currentRow) ?: sheet.createRow(currentRow)
            val cell = row.getCell(column) ?: row.createCell(column)
            cell.setCellValue(value)
            currentRow++
        }
    }

    private fun getTeamFromPreferences(): String {
        val sharedPreferences = AppUtils.getSharedPreferences(context, "LoginPrefs")
        return sharedPreferences.getString("leaderTeam", null) ?: "Unknown Team"
    }

    private fun generateFileName(): String {
        val currentDate = AppUtils.getCurrentDate("yyyy-MM-dd")
        val prefix = "$currentDate ${AppUtils.getCurrentShift(context)} Change Over Checklist Job"
        val existingFiles = directoryPath.listFiles { _, name ->
            name.startsWith(prefix) && name.endsWith(".xlsm")
        }
        val numberOfJob = (existingFiles?.size ?: 0) + 1
        return "$prefix $numberOfJob"
    }
}

