package com.example.kjcan.activities

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.kjcan.R
import java.util.*

class ChangeOverChecklistActivity : AppCompatActivity() {

    // UI Components
    private lateinit var productStatusSpinner: Spinner
    private lateinit var productDetailContainer: LinearLayout
    private lateinit var passesTimeContainer: LinearLayout
    private lateinit var proofProcessContainer: LinearLayout
    private lateinit var outgoingMaterialContainer: LinearLayout
    private lateinit var changeoverResultContainer: LinearLayout

    // Data Maps
    private val productDetailMap = mutableMapOf<String, Boolean>()
    private val passesMap = mutableMapOf<String, Boolean>()
    private val passesTimeMap = mutableMapOf<String, Pair<String, String>>() // Pass -> (StartTime, EndTime)
    private val proofProcessMap = mutableMapOf<String, Boolean>()
    private val outgoingMaterialMap = mutableMapOf<String, Boolean>()
    private val changeoverResultMap = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.change_over_checklist)

        initializeUIComponents()
        initializeUIElements()
        setupPassesTimePickers()

        // Handle Submit Button Click
        findViewById<Button>(R.id.submitChangeOverButton).setOnClickListener {
            handleSubmit()
        }
    }

    /**
     * Initializes all UI components.
     */
    private fun initializeUIComponents() {
        productStatusSpinner = findViewById(R.id.productStatusSpinner)
        productDetailContainer = findViewById(R.id.productDetailContainer)
        passesTimeContainer = findViewById(R.id.passesTimeContainer)
        proofProcessContainer = findViewById(R.id.proofProcessContainer)
        outgoingMaterialContainer = findViewById(R.id.outgoingMaterialContainer)
        changeoverResultContainer = findViewById(R.id.changeoverResultContainer)
    }

    /**
     * Initializes and populates all UI elements using the UIElement structure.
     */
    private fun initializeUIElements() {
        val uiElements = listOf(
            UIElement(R.array.product_detail, productDetailContainer, productDetailMap),
            UIElement(R.array.proof_process, proofProcessContainer, proofProcessMap),
            UIElement(R.array.outgoing_material, outgoingMaterialContainer, outgoingMaterialMap),
            UIElement(R.array.changeover_result, changeoverResultContainer, changeoverResultMap)
        )

        uiElements.forEach { element ->
            populateCheckboxes(element.arrayId, element.container, element.map)
        }
    }

    /**
     * Sets up the passes time pickers with dynamic functionality.
     */
    private fun setupPassesTimePickers() {
        val passes = resources.getStringArray(R.array.passes)

        passes.forEach { pass ->
            val passRow = createPassRow(pass)
            passesTimeContainer.addView(passRow)
            // If there's no existing entry, initialize with defaults
            passesMap[pass] = passesMap[pass] ?: false
            passesTimeMap[pass] = passesTimeMap[pass] ?: ("" to "")
        }
    }

    /**
     * Creates a row for a single pass with its checkbox and time pickers.
     */
    private fun createPassRow(pass: String): LinearLayout {
        val passRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }

        val passCheckbox = CheckBox(this).apply {
            text = pass
            isChecked = passesMap[pass] ?: false
        }

        val startTimeButton = createTimePickerButton("Set Start Time") { time ->
            val endTime = passesTimeMap[pass]?.second ?: ""
            passesTimeMap[pass] = time to endTime
        }

        val endTimeButton = createTimePickerButton("Set End Time") { time ->
            val startTime = passesTimeMap[pass]?.first ?: ""
            passesTimeMap[pass] = startTime to time
        }

        val isChecked = passesMap[pass] ?: false
        startTimeButton.isEnabled = isChecked
        endTimeButton.isEnabled = isChecked

        passCheckbox.setOnCheckedChangeListener { _, checked ->
            passesMap[pass] = checked
            startTimeButton.isEnabled = checked
            endTimeButton.isEnabled = checked
        }

        passRow.addView(passCheckbox)
        passRow.addView(startTimeButton)
        passRow.addView(endTimeButton)

        return passRow
    }

    private fun createTimePickerButton(text: String, onTimeSelected: (String) -> Unit): Button {
        return Button(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                showTimePicker { time ->
                    this.text = time
                    onTimeSelected(time)
                }
            }
        }
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val currentTime = Calendar.getInstance()
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
        val minute = currentTime.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val isPM = selectedHour >= 12
            val hour12 = if (selectedHour % 12 == 0) 12 else selectedHour % 12
            val time = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, selectedMinute, if (isPM) "PM" else "AM")
            onTimeSelected(time)
        }, hour, minute, false).show()
    }

    /**
     * Populates checkboxes for the given array resource into the specified container.
     * Each checkbox's checked state is mapped to [map].
     */
    private fun populateCheckboxes(
        arrayResId: Int,
        container: LinearLayout,
        map: MutableMap<String, Boolean>
    ) {
        val items = resources.getStringArray(arrayResId)
        container.removeAllViews()

        items.forEach { item ->
            // Retrieve the current state from the map, defaulting to false if not present
            val initiallyChecked = map[item] ?: false

            val checkBox = CheckBox(this).apply {
                text = item
                isChecked = initiallyChecked
                setOnCheckedChangeListener { _, isChecked ->
                    map[item] = isChecked
                }
            }

            // If the map does not have this key yet, initialize it
            if (!map.containsKey(item)) {
                map[item] = initiallyChecked
            }

            container.addView(checkBox)
        }
    }

    /**
     * Collects data from the UI for exporting to Excel.
     */
    private fun collectChecklistData(): Map<String, Any> {
        val selectedStatus = productStatusSpinner.selectedItem.toString()
        return mapOf(
            "product_status" to selectedStatus,
            "product_detail" to productDetailMap,
            "passes" to passesMap,
            "passes_time" to passesTimeMap,
            "proof_process" to proofProcessMap,
            "outgoing_material" to outgoingMaterialMap,
            "changeover_result" to changeoverResultMap
        )
    }

    /**
     * Handles the submit button click event.
     */
    private fun handleSubmit() {
        if (!validateChecklistData()){
            return
        }
        showDoubleCheckDialog()
    }

    private fun showDoubleCheckDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Submission")
            .setMessage("Are you sure you want to submit the checklist? Please double-check all your inputs.")
            .setPositiveButton("Yes") { _, _ ->
                proceedWithSubmission()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun proceedWithSubmission() {
        val collectedData = collectChecklistData()
        Log.d("ChecklistData", "Collected Data: $collectedData")
        val changeOverExcel = ChangeOverExcel(this)
        val success = changeOverExcel.generateExcelFile(collectedData)

        showFeedbackDialog(success)
    }

    private fun validateChecklistData():Boolean{
        val isProductDetailValid = productDetailMap.containsValue(true)
        val isPassesValid = passesMap.containsValue(true)
        val isPassesTimeValid = passesTimeMap.any { it.value.first.isNotBlank() && it.value.second.isNotBlank() }
        val isProofProcessValid = proofProcessMap.containsValue(true)
        val isOutgoingMaterialValid = outgoingMaterialMap.containsValue(true)
        val isChangeoverResultValid = changeoverResultMap.containsValue(true)
        return when{
            !isProductDetailValid -> {
                showValidationError("Please select at least one product detail.")
                false
            }
            !isPassesValid -> {
                showValidationError("Please select at least one pass.")
                false
            }
            !isPassesTimeValid -> {
                showValidationError("Please set both start and end times for at least one pass.")
                false
            }
            !isProofProcessValid -> {
                showValidationError("Please select at least one proof process.")
                false
            }
            !isOutgoingMaterialValid -> {
                showValidationError("Please select at least one outgoing material.")
                false
            }
            !isChangeoverResultValid -> {
                showValidationError("Please select at least one changeover result.")
                false
            }
            else -> true
        }
    }

    private fun showValidationError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Validation Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showFeedbackDialog(success: Boolean) {
        val message = if (success) "Excel file created successfully!" else "Failed to create Excel file."
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK"){_, _ ->
                finish()
            }
            .show()
    }

    data class UIElement(val arrayId: Int, val container: LinearLayout, val map: MutableMap<String, Boolean>)
}
