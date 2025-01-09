package com.example.kjcan.activities

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.kjcan.R
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.app.TimePickerDialog
import androidx.appcompat.widget.Toolbar
import java.util.Calendar
import androidx.camera.core.ExperimentalGetImage
import com.example.kjcan.activities.AppUtils.generateFileName
import com.example.kjcan.activities.AppUtils.showToast
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.example.kjcan.activities.AppUtils.getCurrentShift
import com.example.kjcan.utils.ExcelSorter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@ExperimentalGetImage
class SystemDownActivity : AppCompatActivity() {

    private lateinit var plannedPanel: LinearLayout
    private lateinit var unplannedPanel: LinearLayout
    private lateinit var specialPanel: LinearLayout
    private lateinit var submitDowntimeButton: Button
    private lateinit var selectStartTimeButton: Button
    private lateinit var selectEndTimeButton: Button
    private lateinit var startTimeTextView: TextView
    private lateinit var endTimeTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private var startTime: String = "N/A"
    private var endTime: String = "N/A"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.system_down)
        sharedPreferences = AppUtils.getSharedPreferences(this, "LoginPrefs")
        // Initialize UI components
        initializeUI()
    }

    private fun initializeUI() {
        plannedPanel = findViewById(R.id.plannedPanel)
        unplannedPanel = findViewById(R.id.unplannedPanel)
        specialPanel = findViewById(R.id.specialPanel)
        submitDowntimeButton = findViewById(R.id.submitDowntimeButton)
        selectStartTimeButton = findViewById(R.id.selectStartTimeButton)
        selectEndTimeButton = findViewById(R.id.selectEndTimeButton)
        startTimeTextView = findViewById(R.id.startTimeTextView)
        endTimeTextView = findViewById(R.id.endTimeTextView)
        setupSpinnerListeners()

        findViewById<Button>(R.id.plannedDowntimeButton).setOnClickListener {
            showPanel(plannedPanel)
        }

        findViewById<Button>(R.id.unplannedDowntimeButton).setOnClickListener {
            showPanel(unplannedPanel)
        }

        findViewById<Button>(R.id.specialDowntimeButton).setOnClickListener {
            showPanel(specialPanel)
        }

        selectStartTimeButton.setOnClickListener {
            showTimePicker(isStartTime = true)
        }

        // Configure the End Time Button
        selectEndTimeButton.setOnClickListener {
            showTimePicker(isStartTime = false)
        }

        submitDowntimeButton.setOnClickListener {
            submitDowntime()
        }

        toolbarInit()
        val shift = sharedPreferences.getString("userShift", null) ?: "Unassigned"
        AppUtils.copyTemplateFileIfNotExists(this,shift)
    }

    private fun toolbarInit() {
        // Initialize and set up the Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Enable back/up navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
    }

    // Handle the back button press
    override fun onSupportNavigateUp(): Boolean {
        finish() // Close this activity and return to the previous one
        return true
    }

    private fun setupSpinnerListeners() {
        setupSpinner(R.id.plannedDowntimeSpinner, R.array.planned_downtime_array)
        setupSpinner(R.id.unplannedDowntimeSpinner, R.array.unplanned_downtime_array)
    }

    private fun setupSpinner(spinnerId: Int, arrayId: Int) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = ArrayAdapter.createFromResource(
            this, arrayId, android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
    }

    private fun showPanel(panelToShow: LinearLayout) {
        listOf(plannedPanel, unplannedPanel, specialPanel).forEach { panel ->
            panel.visibility = if (panel == panelToShow) View.VISIBLE else View.GONE
        }
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = AppUtils.formatTime(selectedHour, selectedMinute)

            // Assign
            if (isStartTime) {
                startTime = formattedTime
                startTimeTextView.text = buildString {
                    append("Start Time: ")
                    append(formattedTime)
                }
            } else {
                endTime = formattedTime
                endTimeTextView.text = buildString {
                    append("End Time: ")
                    append(formattedTime)
                }
            }

            // Validate if both set
            if (startTime != "N/A" && endTime != "N/A") {
                val valid = AppUtils.isStartEndTimeValid(this, startTime, endTime)
                if (!valid) {
                    // Revert the new time
                    clearInvalidSelection(isStartTime)
                    return@TimePickerDialog
                }

                // If valid, you can compute & show duration
                val duration = AppUtils.calculateDuration(startTime, endTime)
                Log.d("TimePicker", "Calculated Event Duration: $duration minutes")
                // Optionally: durationTextView.text = "Duration: $duration minutes"
            }
        }, hour, minute, false).show()
    }

    /**
     * Resets the invalid time selection to "N/A".
     */
    private fun clearInvalidSelection(isStartTime: Boolean) {
        if (isStartTime) {
            startTime = "N/A"
            startTimeTextView.text = buildString {
                append("Start Time: ")
                append("N/A")
            }
        } else {
            endTime = "N/A"
            endTimeTextView.text = buildString {
                append("End Time: ")
                append("N/A")
            }
        }
    }


    @ExperimentalGetImage
    private fun submitDowntime() {
        val downtimeType: String
        val category: String
        val remarks: String

        when {
            plannedPanel.visibility == View.VISIBLE -> {
                downtimeType = "Planned"
                category = validateSpinner(R.id.plannedDowntimeSpinner)
                remarks = findViewById<EditText>(R.id.plannedRemarksInput).text.toString()

                if (category.isEmpty() || startTime == "N/A" || endTime == "N/A") {
                    showToast(this, "Please select a category for planned downtime!")
                    return
                }
            }
            unplannedPanel.visibility == View.VISIBLE -> {
                downtimeType = "Unplanned"
                category = validateSpinner(R.id.unplannedDowntimeSpinner)
                remarks = findViewById<EditText>(R.id.unplannedRemarksInput).text.toString()

                if (category.isEmpty() || remarks.isEmpty() || startTime == "N/A" || endTime == "N/A") {
                    showToast(this, "Please complete all fields for unplanned downtime!")
                    return
                }
            }
            specialPanel.visibility == View.VISIBLE -> {
                downtimeType = "Special"
                category = findViewById<EditText>(R.id.specialReasonInput).text.toString()
                remarks = findViewById<EditText>(R.id.specialRemarksInput).text.toString()

                if (category.isEmpty() || startTime == "N/A" || endTime == "N/A") {
                    showToast(this, "Please complete all fields for special downtime!")
                    return
                }
            }
            else -> {
                showToast(this, "Please select a downtime type and fill in the details!")
                return
            }
        }

        val durationInMinutes = AppUtils.calculateDuration(startTime, endTime)
        saveDataToExcel(
            downtimeType, category, remarks,
            startTime, endTime, durationInMinutes
        )


        // Launch a coroutine in the lifecycleScope tied to the Activity's lifecycle
        lifecycleScope.launch {
            try {
                // Step 1: Sort the Excel file by Start Time
                val excelSorter = ExcelSorter(this@SystemDownActivity, getCurrentShift(this@SystemDownActivity))
                excelSorter.sortExcelByStartTime("ProductionData")
                Log.d("ExcelSorter", "Excel file sorted successfully.")

                // Step 2: Transfer sorted data to FormattedProductionDowntime
                val excelManager = ExcelManager(this@SystemDownActivity, getCurrentShift(this@SystemDownActivity))
                excelManager.transferData("ProductionData", "FormattedProductionDowntime")
                Log.d("ExcelManager", "Data transferred to FormattedProductionDowntime successfully.")

                // Step 3: Update UI on the main thread after background operations are complete
                withContext(Dispatchers.Main) {
                    Log.d("Submission", "Downtime data submitted and sorted successfully.")
                    showToast(this@SystemDownActivity, "Downtime data submitted successfully")
                    finish()
                }
            } catch (e: Exception) {
                Log.e("SystemDownActivity", "Error submitting downtime form: ${e.localizedMessage}", e)
                withContext(Dispatchers.Main) {
                    showToast(this@SystemDownActivity, "Failed to submit downtime data: ${e.message}")
                }
            }
        }

        val resultIntent = Intent().apply {
            putExtra("downtimeType", downtimeType)
            putExtra("category", category)
            putExtra("remarks", remarks)
            putExtra("startTime", startTime)
            putExtra("endTime", endTime)
            putExtra("duration", durationInMinutes)
        }

        setResult(RESULT_OK, resultIntent)
        // Note: `finish()` is already called inside the coroutine
    }

    private fun validateSpinner(spinnerId: Int): String {
        val spinner = findViewById<Spinner>(spinnerId)
        return spinner.selectedItem?.toString() ?: ""
    }


    @ExperimentalGetImage
    private fun saveDataToExcel(
        downtimeType: String,
        category: String,
        remarks: String,
        startTime: String,
        endTime: String,
        durationInMinutes: Double
    ) {
        val shift = sharedPreferences.getString("userShift", null) ?: "Unassigned"
        val fileName = generateFileName(shift)
        val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), fileName)

        Log.d("saveDataToExcel", "Saving to file: $filePath with duration: $durationInMinutes")

        try {
            val workbook: Workbook
            val sheetName = "ProductionData"
            val sheet: org.apache.poi.ss.usermodel.Sheet

            if (filePath.exists()) {
                val fis = FileInputStream(filePath)
                workbook = WorkbookFactory.create(fis)
                sheet = workbook.getSheet(sheetName) ?: workbook.createSheet(sheetName)
                fis.close()
            } else {
                workbook = XSSFWorkbook()
                sheet = workbook.createSheet(sheetName)

                val headers = listOf(
                    "Start Time", "End Time", "Event Duration (Minutes)", "Event Type", "Downtime Type",
                    "Category", "Remarks", "Job ID", "Quantity", "Thickness",
                    "Height", "Width", "Actual Produced Quantity", "Motor Speed"
                )
                val headerRow = sheet.createRow(0)
                headers.forEachIndexed { index, header ->
                    headerRow.createCell(index).setCellValue(header)
                }

                // Set the third column (index 2) to numeric format
                val numericStyle = workbook.createCellStyle()
                numericStyle.dataFormat = workbook.creationHelper.createDataFormat().getFormat("0.00")
                sheet.setDefaultColumnStyle(2, numericStyle) // Column C (index 2)
            }

            val rowIndex = sheet.physicalNumberOfRows
            val row = sheet.createRow(rowIndex)

            row.createCell(0).setCellValue(startTime) // Start Time
            row.createCell(1).setCellValue(endTime)   // End Time
            row.createCell(2).setCellValue(durationInMinutes)
            row.createCell(3).setCellValue("Downtime") // Event Type
            row.createCell(4).setCellValue(downtimeType) // Downtime Type
            row.createCell(5).setCellValue(category)    // Category
            row.createCell(6).setCellValue(remarks)     // Remarks

            // Leave other columns empty
            for (i in 7..13) {
                row.createCell(i).setCellValue("") // Empty placeholders
            }

            val fos = FileOutputStream(filePath)
            workbook.write(fos)
            workbook.close()
            fos.close()

            showToast(this, "Downtime data saved successfully to $fileName")
            Log.d("saveDataToExcel", "Data saved successfully.")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(this, "Error saving file: ${e.localizedMessage}")
            Log.e("saveDataToExcel", "Error saving file", e)
        }
    }
}
