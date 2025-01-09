package com.example.kjcan.activities

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import android.widget.Toast
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider

object AppUtils {

    // Show Toast Messages
    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Hash Password with Salt using SHA-256
    fun hashPassword(password: String, salt: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest((password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // Generate Random Salt
    fun generateSalt(length: Int = 16): String {
        val charset = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length).map { charset.random() }.joinToString("")
    }

    // Format Date
    fun getCurrentDate(format: String = "yyyy-MM-dd"): String {
        return SimpleDateFormat(format, Locale.getDefault()).format(Date())
    }

    // Format Time
    fun formatTime(hourOfDay: Int, minute: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        calendar.set(Calendar.MINUTE, minute)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return timeFormat.format(calendar.time)
    }

    /**
     * Calculates the duration in minutes between startTime and endTime.
     * Returns 0.0 in case of an error.
     */
    fun calculateDuration(startTime: String, endTime: String): Double {
        return try {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val start = timeFormat.parse(startTime)
            val end = timeFormat.parse(endTime)
            if (start != null && end != null) {
                val durationMillis = if (end.time < start.time) {
                    end.time + (24 * 60 * 60 * 1000) - start.time // Handle crossing midnight
                } else {
                    end.time - start.time
                }
                val duration = durationMillis / (1000 * 60).toDouble() // Convert to minutes
                Log.d("calculateDuration", "Duration calculated: $duration minutes")
                duration
            } else {
                Log.e("calculateDuration", "Failed to parse start or end time.")
                0.0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("calculateDuration", "Exception in calculating duration: ${e.localizedMessage}")
            0.0
        }
    }

    /**
     * Validates that:
     *  1) Start Time != End Time
     *  2) (based on current logic) End Time >= Start Time, or crossing midnight
     *  3) Duration <= 12 hours (720 minutes)
     *
     * Returns true if valid; false otherwise (and shows toast).
     */
    fun isStartEndTimeValid(context: Context, startTime: String, endTime: String): Boolean {
        val duration = calculateDuration(startTime, endTime)

        // duration == 0 => same time
        // (with crossing-midnight logic, negative won't happen, but <= 0 is a good safety check)
        if (duration <= 0.0) {
            showToast(context, "End time cannot be the same or earlier than Start time.")
            return false
        }
        // 12 hours => 720 minutes
        if (duration > 720) {
            showToast(context, "Duration cannot exceed 12 hours.")
            return false
        }
        return true
    }

    // Shared Preferences Utility
    fun getSharedPreferences(context: Context, name: String = "AppPrefs"): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun generateFileName(shift: String): String {
        val currentDate = getShiftStartDate(shift)
        return "${currentDate}_${shift}_Raw_Daily_Production_Record_UV_Line.xlsx"
    }

    fun generateFileName2(shift: String): String {
        val currentDate = getShiftStartDate(shift)
        return "${currentDate}_${shift}_Formatted_Daily_Production_Record_UV_Line.xlsx"
    }

    fun getShiftStartDate(shift: String): String {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // For night shift, adjust the date if the time is past midnight
        if (shift.equals("Night", ignoreCase = true) && currentHour < 7) {
            calendar.add(Calendar.DATE, -1) // Go back one day
        }

        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    fun getCurrentShift(context:Context): String {
        val sharedPreferences = getSharedPreferences(context, "LoginPrefs")
        return sharedPreferences.getString("userShift", null) ?: "Unassigned"
    }

    fun copyTemplateFileIfNotExists(context: Context,shift: String) {
        // Dynamically generate the filename using ProductionActivity.generateFileName2()
        val fileName = generateFileName2(shift)
        val outputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            fileName
        )

        if (outputFile.exists()) {
            Log.d("Template", "File already exists: $fileName")
            return
        }

        try {
            val templateFileName = "template.xlsx" // Template file name in assets
            val inputStream = context.assets.open(templateFileName)
            val workbook = WorkbookFactory.create(inputStream) as XSSFWorkbook
            inputStream.close()

            // Rename the first sheet to "FormattedProductionDowntime"
            val sheet = workbook.getSheetAt(0) // Get the first sheet
            workbook.setSheetName(0, "FormattedProductionDowntime")
            Log.d("Template", "Renamed sheet to: FormattedProductionDowntime")

            // Add current date to cell T3
            val row = sheet.getRow(2) ?: sheet.createRow(2) // Row index is 2 for T3 (0-based indexing)
            val cell = row.getCell(19) ?: row.createCell(19) // Column index is 19 for T (0-based indexing)
            val currentDate = getCurrentDate()
            cell.setCellValue(currentDate)
            Log.d("Template", "Added current date to cell T3: $currentDate")

            // Save the renamed workbook to the generated filename
            val fos = FileOutputStream(outputFile)
            workbook.write(fos)
            workbook.close()
            fos.close()

            Log.d("Template", "Template file created successfully: $fileName")
        } catch (e: kotlin.Exception) {
            e.printStackTrace()
            Log.e("Template", "Error creating template file: ${e.localizedMessage}")
        }
    }



    fun shareExcelFile(context: Context, fileName: String) {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            fileName
        )

        if (!file.exists()) {
            Log.e("ShareFile", "File not found: $fileName")
            return
        }

        try {
            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",  // Use 'context.packageName'
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Excel File"))
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ShareFile", "Error sharing file: ${e.localizedMessage}")
        }
    }
}