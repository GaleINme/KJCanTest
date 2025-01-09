package com.example.kjcan.activities

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.camera.core.ExperimentalGetImage
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kjcan.R
import com.example.kjcan.activities.AppUtils.getCurrentShift
import com.example.kjcan.utils.ExcelSorter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar

@ExperimentalGetImage
class ExcelViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_excel_viewer)

        toolbarInit()
        if (hasStoragePermission()) {
            loadExcelData()
        } else {
            requestStoragePermission()
        }
    }

    // Handle the back button press
    override fun onSupportNavigateUp(): Boolean {
        finish() // Close this activity and return to the previous one
        return true
    }

    private fun toolbarInit() {
        // Initialize and set up the Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Enable back/up navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
            Toast.makeText(
                this,
                "Grant 'Allow All Files Access' permission in settings",
                Toast.LENGTH_LONG
            ).show()
        } else {
            val requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        loadExcelData()
                    } else {
                        AppUtils.showToast(this@ExcelViewerActivity,"Storage permission is required to read Excel files")
                    }
                }
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun loadExcelData() {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val filePath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            AppUtils.generateFileName(getCurrentShift(this))
        )

        if (filePath.exists()) {
            CoroutineScope(Dispatchers.IO).launch {
                val (headers, rows) = readExcelFile(filePath)
                if (rows.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val adapter = ExcelDataAdapter(headers, rows, lifecycleScope) // Pass lifecycleScope
                        recyclerView.adapter = adapter
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        AppUtils.showToast(this@ExcelViewerActivity,"Excel file is empty or unreadable")
                    }
                }
            }
        } else {
            AppUtils.showToast(this@ExcelViewerActivity,"Excel file not found!")
        }
    }

    private suspend fun readExcelFile(filePath: File): Pair<List<String>, MutableList<MutableMap<String, String>>> =
        withContext(Dispatchers.IO) {

            val data = mutableListOf<MutableMap<String, String>>()
            val headers = mutableListOf<String>()

            try {
                val fis = FileInputStream(filePath)
                val workbook = WorkbookFactory.create(fis)
                val sheet = workbook.getSheetAt(0)

                // Read header row
                val headerRow = sheet.getRow(0)
                headerRow.forEach { cell ->
                    headers.add(cell.toString())
                }

                // Read data rows
                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val rowData = mutableMapOf<String, String>()
                    headers.forEachIndexed { index, header ->
                        val cell = row.getCell(index)
                        rowData[header] = when (cell?.cellType) {
                            CellType.NUMERIC -> cell.numericCellValue.toString() // Ensure numeric values are read correctly
                            CellType.STRING -> cell.stringCellValue
                            else -> "" // Default to empty string for missing or unsupported cell types
                        }
                    }
                    data.add(rowData)
                }

                workbook.close()
                fis.close()
            } catch (e: Exception) {
                Log.e("ExcelViewerActivity", "Error reading Excel file", e)
                AppUtils.showToast(this@ExcelViewerActivity,"Error reading Excel file: ${e.localizedMessage}")
            }

            Pair(headers,data)
        }

    // Adapter for RecyclerView
    @ExperimentalGetImage
    class ExcelDataAdapter(
        private val header: List<String>,
        private val data: MutableList<MutableMap<String, String>>, // Each row is a map of column headers to values
        private val coroutineScope: CoroutineScope // Add CoroutineScope as a parameter
    ) : RecyclerView.Adapter<ExcelDataAdapter.EventViewHolder>() {

        // Track expanded rows
        private val expandedRows = mutableSetOf<Int>()

        inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val mainPanel: LinearLayout = view.findViewById(R.id.mainPanel)
            val expandablePanel: LinearLayout = view.findViewById(R.id.expandablePanel)
            val startTime: TextView = view.findViewById(R.id.startTime)
            val endTime: TextView = view.findViewById(R.id.endTime)
            val eventType: TextView = view.findViewById(R.id.eventType)

            // Production-specific fields
            val pEventDuration: TextView = view.findViewById(R.id.pEventDuration)
            val jobId: TextView = view.findViewById(R.id.jobId)
            val quantity: TextView = view.findViewById(R.id.quantity)
            val thickness: TextView = view.findViewById(R.id.thickness)
            val height: TextView = view.findViewById(R.id.height)
            val width: TextView = view.findViewById(R.id.width)
            val actualProduced: TextView = view.findViewById(R.id.actualProduced)
            val motorSpeed: TextView = view.findViewById(R.id.motorSpeed)
            val pRemarks: TextView = view.findViewById(R.id.Premarks)

            // Downtime-specific fields
            val dEventDuration: TextView = view.findViewById(R.id.dEventDuration)
            val downtimeType: TextView = view.findViewById(R.id.downtimeType)
            val category: TextView = view.findViewById(R.id.category)
            val dRemarks: TextView = view.findViewById(R.id.Dremarks)

            val editButton: Button = view.findViewById(R.id.editButton)
            val deleteButton: Button = view.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_row, parent, false)
            return EventViewHolder(view)
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            val rowData = data[position]

            // Populate main panel
            holder.startTime.text = rowData["Start Time"]
            holder.endTime.text = rowData["End Time"]
            if (rowData["Event Type"] == "Production"){
                holder.eventType.text = rowData["Event Type"]
            } else if (rowData["Downtime Type"]=="Planned"){
                holder.eventType.text = extractCategoryKey(rowData["Category"].toString())
            } else {
                holder.eventType.text = rowData["Event Type"]
            }

            holder.editButton.setOnClickListener {
                showEditDialog(holder.itemView.context, rowData, position)
            }

            // Handle Delete Button
            holder.deleteButton.setOnClickListener {
                AlertDialog.Builder(holder.itemView.context).apply {
                    setTitle("Delete Record")
                    setMessage("Are you sure you want to delete this record?")
                    setPositiveButton("Yes") { _, _ ->
                        deleteRecord(holder.itemView.context, position)
                    }
                    setNegativeButton("Cancel", null)
                    show()
                }
            }

            // Handle expand/collapse logic
            val isExpanded = expandedRows.contains(position)
            holder.expandablePanel.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.mainPanel.setOnClickListener {
                if (isExpanded) {
                    expandedRows.remove(position)
                    notifyItemChanged(position)
                } else {
                    expandedRows.add(position)
                    notifyItemChanged(position)
                }
            }

            // Show details based on event type
            when (rowData["Event Type"]) {
                "Production" -> {
                    showProductionDetails(holder, rowData)
                }
                "Downtime" -> {
                    showDowntimeDetails(holder, rowData)
                }
            }
        }

        private fun extractCategoryKey(category: String): String {
            Log.d("ExcelManager", "Extracting category key from: $category")
            // Split the category string by ":" and take the first part (the key)
            return category.split(":").firstOrNull()?.trim() ?: ""
        }

        private fun showProductionDetails(holder: EventViewHolder, rowData: Map<String, String>) {
            setTextAndVisibility(holder.pEventDuration, "Duration: ${rowData["Event Duration (Minutes)"]} min")
            setTextAndVisibility(holder.jobId, "Job ID: ${rowData["Job ID"]}")
            setTextAndVisibility(holder.quantity, "Quantity: ${rowData["Quantity"]} sht")
            setTextAndVisibility(holder.thickness, "Thickness: ${rowData["Thickness"]} mm")
            setTextAndVisibility(holder.height, "Height: ${rowData["Height"]} mm")
            setTextAndVisibility(holder.width, "Width: ${rowData["Width"]} mm")
            setTextAndVisibility(holder.actualProduced, "Actual Produced: ${rowData["Actual Produced Quantity"]} sht")
            setTextAndVisibility(holder.motorSpeed, "Motor Speed: ${rowData["Motor Speed"]} rpm")
            setTextAndVisibility(holder.pRemarks, "Remarks: ${rowData["Remarks"]}")

            // Hide downtime fields
            hideFields(holder.dEventDuration, holder.downtimeType, holder.category, holder.dRemarks)
        }

        private fun showDowntimeDetails(holder: EventViewHolder, rowData: Map<String, String>) {
            setTextAndVisibility(holder.dEventDuration, "Duration: ${rowData["Event Duration (Minutes)"]} min")
            setTextAndVisibility(holder.downtimeType, "Downtime Type: ${rowData["Downtime Type"]}")
            if (rowData["Downtime Type"] == "Special") {
                // Show Reason for Special Downtime
                setTextAndVisibility(holder.category, "Reason: ${rowData["Category"]}")
            } else {
                // Show Category for other types
                setTextAndVisibility(holder.category, "Category: ${rowData["Category"]}")
            }
            setTextAndVisibility(holder.dRemarks, "Remarks: ${rowData["Remarks"]}")

            // Hide production fields
            hideFields(
                holder.pEventDuration,
                holder.jobId,
                holder.quantity,
                holder.thickness,
                holder.height,
                holder.width,
                holder.actualProduced,
                holder.motorSpeed,
                holder.pRemarks
            )
        }

        private fun setTextAndVisibility(textView: TextView, text: String) {
            textView.text = text
            textView.visibility = View.VISIBLE
        }

        private fun hideFields(vararg views: View) {
            views.forEach { it.visibility = View.GONE }
        }

        private fun showEditDialog(context: Context, rowData: MutableMap<String, String>, position: Int) {
            Log.d("ExcelViewer", "Showing edit dialog for position $position")
            val dialogBuilder = AlertDialog.Builder(context)
            val root = (context as? Activity)?.findViewById<ViewGroup>(android.R.id.content)
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit, root, false)
            val container = dialogView.findViewById<LinearLayout>(R.id.editFieldsContainer)

            // Duration TextView -- use "Event Duration (Minutes)" to stay consistent
            val durationTextView = createLabel(context, "Duration: ${rowData["Event Duration (Minutes)"]} min")

            // 2) Create Start Time + End Time fields **without** immediately adding them to the container
            // because we need them as variables so we can pass updateDuration to each.
            val startTimeLabel = createLabel(context, "Start Time")
            val startTimeField = createTimeField(context, "Start Time", rowData, "Start Time", durationTextView)

            val endTimeLabel = createLabel(context, "End Time")
            val endTimeField = createTimeField(context, "End Time", rowData, "End Time", durationTextView)

            // 3) Add everything to the container in your desired order.
            container.addView(startTimeLabel)
            container.addView(startTimeField)

            container.addView(endTimeLabel)
            container.addView(endTimeField)

            // Add Duration TextView
            container.addView(durationTextView)

            // Then continue with your normal code for Production vs Downtime...
            dialogBuilder.setTitle(
                if (rowData["Event Type"] == "Production") "Edit Production Details"
                else "Edit Downtime Details"
            )

            when (rowData["Event Type"]) {
                "Production" -> {
                    val fields = listOf(
                        "Job ID",
                        "Quantity",
                        "Thickness",
                        "Height",
                        "Width",
                        "Actual Produced Quantity",
                        "Motor Speed",
                        "Remarks"
                    )
                    addFieldsToContainer(context, container, fields, rowData)

                    dialogBuilder.setPositiveButton("Save") { _, _ ->
                        fields.forEach { field ->
                            rowData[field] = container.findViewWithTag<EditText>(field)?.text.toString()
                        }
                        saveEditedData(context, position, rowData)
                    }
                }
                "Downtime" -> {
                    var downtimeTypeSpinner: Spinner? = null // Declare the spinner variable outside
                    downtimeTypeSpinner = createSpinner(context, listOf("Planned", "Unplanned", "Special"), rowData["Downtime Type"]) { selectedType ->
                        rowData["Downtime Type"] = selectedType
                        container.removeAllViews()
                        container.addView(createLabel(context, "Start Time"))
                        container.addView(createTimeField(context, "Start Time", rowData, "Start Time", durationTextView))
                        container.addView(createLabel(context, "End Time"))
                        container.addView(createTimeField(context, "End Time", rowData, "End Time", durationTextView))
                        container.addView(durationTextView)
                        container.addView(createLabel(context, "Downtime Type"))
                        container.addView(downtimeTypeSpinner)
                        updateDowntimeFields(context, selectedType, container, rowData)
                    }

                    container.addView(createLabel(context, "Downtime Type"))
                    container.addView(downtimeTypeSpinner)
                    updateDowntimeFields(context, rowData["Downtime Type"] ?: "Planned", container, rowData)

                    dialogBuilder.setPositiveButton("Save") { _, _ ->
                        rowData["Remarks"] = container.findViewWithTag<EditText>("Remarks")?.text.toString()
                        if (rowData["Downtime Type"] == "Special") {
                            rowData["Reason"] = container.findViewWithTag<EditText>("Reason")?.text.toString()
                        } else {
                            rowData["Category"] = container.findViewWithTag<Spinner>("Category")?.selectedItem.toString()
                        }
                        saveEditedData(context, position, rowData)
                    }
                }
            }

            dialogBuilder.setNegativeButton("Cancel", null)
            dialogBuilder.setView(dialogView)
            dialogBuilder.show()
        }

        private fun createTimeField(
            context: Context,
            label: String,
            rowData: MutableMap<String, String>,
            key: String,
            durationTextView: TextView
        ): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(16, 8, 16, 8) }
            }

            val clockIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_clock)
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    setMargins(8, 8, 8, 8)
                }
            }

            val timeTextView = TextView(context).apply {
                text = rowData[key] ?: "Tap to select $label"
                background = ContextCompat.getDrawable(context, R.drawable.editable_field_background)
                setPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { setMargins(16, 0, 0, 0) }
            }

            // Helper to re-calc duration in UI
            fun updateDuration() {
                val updatedDuration = AppUtils.calculateDuration(
                    rowData["Start Time"] ?: "00:00 AM",
                    rowData["End Time"] ?: "00:00 AM"
                )
                rowData["Event Duration (Minutes)"] = updatedDuration.toString()
                durationTextView.text = buildString {
                    append("Duration: ")
                    append(updatedDuration)
                    append(" min")
                }
            }

            timeTextView.setOnClickListener {
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                    val formattedTime = AppUtils.formatTime(selectedHour, selectedMinute)

                    // Temporarily hold the old value, in case we need to revert
                    val oldValue = rowData[key]

                    // Tentatively set new time
                    rowData[key] = formattedTime

                    // Validate
                    val valid = AppUtils.isStartEndTimeValid(
                        context,
                        rowData["Start Time"] ?: "00:00 AM",
                        rowData["End Time"] ?: "00:00 AM"
                    )

                    if (!valid) {
                        // Revert on invalid
                        rowData[key] = oldValue ?: ""
                    } else {
                        // Update UI if valid
                        timeTextView.text = formattedTime
                        updateDuration()
                    }
                }, hour, minute, false).show()
            }

            layout.addView(clockIcon)
            layout.addView(timeTextView)
            return layout
        }


        // Dynamically update downtime fields
        private fun updateDowntimeFields(
            context: Context,
            type: String,
            container: LinearLayout,
            rowData: MutableMap<String, String>
        ) {
            // Preserve current values before clearing the container
            val currentRemarks = container.findViewWithTag<EditText>("Remarks")?.text?.toString()
            val currentReason = container.findViewWithTag<EditText>("Reason")?.text?.toString()
            val currentCategory = container.findViewWithTag<Spinner>("Category")?.selectedItem?.toString()

            // Update rowData with preserved values
            if (!currentRemarks.isNullOrBlank()) rowData["Remarks"] = currentRemarks
            if (!currentReason.isNullOrBlank()) rowData["Reason"] = currentReason
            if (!currentCategory.isNullOrBlank()) rowData["Category"] = currentCategory

            // Clear container for updated views
            container.removeAllViews()

            // Add Start Time and End Time fields
            val placeholderView = TextView(context) // just to hold a place for changing duration
            container.addView(createLabel(context, "Start Time"))
            container.addView(createTimeField(context, "Start Time", rowData, "Start Time", placeholderView))

            container.addView(createLabel(context, "End Time"))
            container.addView(createTimeField(context, "End Time", rowData, "End Time", placeholderView))

            // Add Duration field
            val durationTextView = createLabel(context, "Duration: ${rowData["Event Duration (Minutes)"]} min")
            container.addView(durationTextView)

            // Add Downtime Type spinner
            container.addView(createLabel(context, "Downtime Type"))
            val downtimeTypeSpinner = createSpinner(
                context,
                listOf("Planned", "Unplanned", "Special"),
                rowData["Downtime Type"]
            ) { selected ->
                rowData["Downtime Type"] = selected
                updateDowntimeFields(context, selected, container, rowData) // Recursively update fields
            }
            downtimeTypeSpinner.tag = "DowntimeType"
            container.addView(downtimeTypeSpinner)

            // Add specific fields based on Downtime Type
            when (type) {
                "Special" -> {
                    // Add Reason field for Special Downtime
                    container.addView(createLabel(context, "Reason"))
                    container.addView(createEditText(context, "Reason", rowData["Reason"] ?: "").apply {
                        tag = "Reason"
                    })
                }
                else -> {
                    // Add Category field for Planned/Unplanned Downtime
                    container.addView(createLabel(context, "Category"))
                    container.addView(
                        createSpinner(
                            context,
                            getDowntimeCategories(context, type),
                            rowData["Category"]
                        ) { selectedCategory ->
                            rowData["Category"] = selectedCategory
                        }.apply {
                            tag = "Category"
                        }
                    )
                }
            }

            // Add Remarks field
            container.addView(createLabel(context, "Remarks"))
            container.addView(createEditText(context, "Remarks", rowData["Remarks"] ?: "").apply {
                tag = "Remarks"
            })
        }

        // Get downtime categories
        private fun getDowntimeCategories(context: Context, type: String?): List<String> {
            return when (type) {
                "Planned" -> context.resources.getStringArray(R.array.planned_downtime_array).toList()
                "Unplanned" -> context.resources.getStringArray(R.array.unplanned_downtime_array).toList()
                else -> emptyList()
            }
        }

        private fun addFieldsToContainer(
            context: Context,
            container: LinearLayout,
            fields: List<String>,
            rowData: Map<String, String>
        ) {
            val numericFields = listOf(
                "Quantity",
                "Thickness",
                "Height",
                "Width",
                "Actual Produced Quantity",
                "Motor Speed"
            )
            val multilineFields = listOf("Remarks", "Reason") // Fields that should support multiline input

            fields.forEach { field ->
                // Add Label
                val label = createLabel(context, "$field:")
                container.addView(label)

                // Add EditText
                val isNumeric = numericFields.contains(field)
                val isMultiline = multilineFields.contains(field)
                val editText = createEditText(
                    context = context,
                    hint = field,
                    value = rowData[field] ?: "",
                    isMultiline = isMultiline,
                    isNumeric = isNumeric
                )
                editText.tag = field // Tag to identify the field later
                container.addView(editText)
            }
        }

        private fun createSpinner(
            context: Context,
            options: List<String>,
            selectedOption: String? = null,
            onItemSelected: ((String) -> Unit)? = null
        ): Spinner {
            var isFirstSelection = true

            return Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, options).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(16, 8, 16, 8) }

                selectedOption?.let { option ->
                    val index = options.indexOf(option)
                    if (index >= 0) setSelection(index)
                }

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (isFirstSelection) {
                            isFirstSelection = false // Skip the initial call caused by setSelection
                            return
                        }
                        onItemSelected?.invoke(options[position])
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }

        private fun createLabel(context: Context, text: String): TextView {
            return TextView(context).apply {
                this.text = text
                this.textSize = 16f
                this.setPadding(16, 8, 16, 4) // Compact padding
                this.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 0) // Minimal bottom margin
                }
            }
        }

        private fun createEditText(
            context: Context,
            hint: String,
            value: String,
            isMultiline: Boolean = false,
            isNumeric: Boolean = false
        ): EditText {
            return EditText(context).apply {
                this.hint = hint
                this.setText(value)
                this.setPadding(16, 8, 0, 16) // Compact padding
                this.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16) // Bottom margin for spacing
                }

                if (isMultiline) {
                    this.minLines = 3
                    this.maxLines = 5
                    this.isSingleLine = false
                    this.ellipsize = null
                    this.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                } else if (isNumeric) {
                    this.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    this.isSingleLine = true
                } else {
                    this.maxLines = 1
                    this.isSingleLine = true
                    this.ellipsize = android.text.TextUtils.TruncateAt.END
                    this.inputType = android.text.InputType.TYPE_CLASS_TEXT
                }
            }
        }

        private fun deleteRecord(context: Context, position: Int) {
            try {
                // Capture the deleted data for matching in the formatted file
                val deletedData = data[position]

                // Remove the record from in-memory data
                data.removeAt(position)

                // File path and sheet name for the raw Excel file
                val filePath = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    AppUtils.generateFileName(getCurrentShift(context))
                )
                val sheetName = "ProductionData"

                val fis = FileInputStream(filePath)
                val workbook = WorkbookFactory.create(fis)
                val sheet = workbook.getSheet(sheetName)

                // Clear all rows except the header
                for (i in sheet.lastRowNum downTo 1) {
                    val row = sheet.getRow(i)
                    if (row != null) sheet.removeRow(row)
                }

                // Write back the updated data rows
                for ((rowIndex, rowData) in data.withIndex()) {
                    val row = sheet.createRow(rowIndex + 1) // Start after header row
                    rowData.forEach { (key, value) ->
                        val columnIndex = header.indexOf(key)
                        if (columnIndex != -1) {
                            val cell = row.createCell(columnIndex)
                            // Determine data type based on key name
                            when (key) {
                                "Event Duration (Minutes)",
                                "Quantity",
                                "Thickness",
                                "Height",
                                "Width",
                                "Actual Produced Quantity",
                                "Motor Speed" -> {
                                    // Try to parse as Double or Int
                                    val numericValue = value.toDoubleOrNull()
                                    if (numericValue != null && numericValue != 0.0) {
                                        cell.setCellValue(numericValue)
                                    } else {
                                        cell.setBlank() // Set blank if value is 0 or parsing fails
                                        Log.d("TypeCheck", "Set cell blank for key: $key, Value: $value")
                                    }
                                }
                                else -> {
                                    // Default to String for all other keys
                                    if (value == "0" || value.isEmpty()) {
                                        cell.setBlank() // Set blank for string "0" or empty strings
                                    } else {
                                        cell.setCellValue(value)
                                    }
                                }
                            }
                        }
                    }
                }

                fis.close()

                val fos = FileOutputStream(filePath)
                workbook.write(fos)
                fos.close()
                workbook.close()

                // Remove the corresponding row from the formatted Excel file
                deleteFromFormattedExcelFile(context, deletedData)

                // Notify RecyclerView adapter
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, data.size)

                AppUtils.showToast(context, "Record deleted successfully!")
            } catch (e: Exception) {
                Log.e("ExcelDataAdapter", "Error deleting data", e)
                AppUtils.showToast(context, "Failed to delete data: ${e.message}")
            }
        }

        fun deleteFromFormattedExcelFile(context: Context, deletedData: Map<String, String>) {
            coroutineScope.launch {
                val filePath = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    AppUtils.generateFileName2(getCurrentShift(context))
                )

                try {
                    // Open the workbook and access the sheet
                    val fis = FileInputStream(filePath)
                    val workbook = WorkbookFactory.create(fis) as XSSFWorkbook
                    val sheet = workbook.getSheet("FormattedProductionDowntime")
                    fis.close()

                    // Build match key based on "Start Time ~ End Time"
                    val startTime = deletedData["Start Time"] ?: ""
                    val endTime = deletedData["End Time"] ?: ""
                    val matchKey = "$startTime ~ $endTime"
                    var rowToDeleteIndex = -1

                    // Determine the last row with data
                    val maxRowIndex = ExcelStateManager.getMaxRowIndex("FormattedProductionDowntime") - 1

                    // Find the row to modify or delete
                    for (rowIndex in 8..maxRowIndex) { // Assuming data starts from row 9 (index 8)
                        val row = sheet.getRow(rowIndex) ?: continue
                        val cellValue = row.getCell(0)?.stringCellValue ?: ""
                        if (cellValue == matchKey) {
                            rowToDeleteIndex = rowIndex
                            break
                        }
                    }

                    if (rowToDeleteIndex != -1) {
                        if (rowToDeleteIndex in 8..47) { // Rows 9 to 48 (indices 8 to 47)
                            // Set rows 9 to 48 to nothing
                            for (rowIndex in 8..47) {
                                val row = sheet.getRow(rowIndex)
                                row?.forEach { cell ->
                                    cell.setBlank()
                                }
                            }
                        } else if (rowToDeleteIndex > 48) { // Rows beyond 48
                            // Delete the specific row
                            val rowToDelete = sheet.getRow(rowToDeleteIndex)
                            sheet.removeRow(rowToDelete)
                        }
                    }

                    // Save changes back to the file
                    val fos = FileOutputStream(filePath)
                    workbook.write(fos)
                    workbook.close()

                    // Call the suspend function within the coroutine
                    val excelManager = ExcelManager(context, getCurrentShift(context))
                    excelManager.transferData("ProductionData", "FormattedProductionDowntime")

                    Log.d("FormattedExcel", "Formatted file updated successfully after row removal.")
                    withContext(Dispatchers.Main) {
                        AppUtils.showToast(context, "Record deleted and formatted data updated successfully!")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("FormattedExcel", "Error deleting row from file: ${e.localizedMessage}")
                    withContext(Dispatchers.Main) {
                        AppUtils.showToast(context, "Error deleting record: ${e.localizedMessage}")
                    }
                }
            }
        }

        private fun saveEditedData(context: Context, position: Int, updatedData: MutableMap<String, String>) {
            try {
                val doubleFields = listOf(
                    "Event Duration (Minutes)",
                    "Quantity",
                    "Thickness",
                    "Height",
                    "Width",
                    "Actual Produced Quantity",
                    "Motor Speed"
                )

                doubleFields.forEach { field ->
                    if (updatedData[field].isNullOrEmpty()) {
                        updatedData[field] = ""
                    } else {
                        updatedData[field] = updatedData[field]?.toDoubleOrNull()?.toString() ?: ""
                    }
                }

                if (updatedData["Downtime Type"] == "Special") {
                    updatedData["Category"] = updatedData["Reason"] ?: "" // Save Reason in Category
                    updatedData.remove("Reason") // Remove Reason key to avoid redundancy
                }

                updatedData.forEach { (key, value) ->
                    data[position][key] = value // Update the in-memory data
                }

                val filePath = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    AppUtils.generateFileName(getCurrentShift(context))
                )
                val sheetName = "ProductionData"
                val rowIndex = position + 1 // Adjust for the header row

                val fis = FileInputStream(filePath)
                val workbook = WorkbookFactory.create(fis)
                val sheet = workbook.getSheet(sheetName)
                fis.close()

                // Update the specific row with edited data
                val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
                updatedData.forEach { (key, value) ->
                    val columnIndex = header.indexOf(key)
                    if (columnIndex != -1) {
                        val cell = row.getCell(columnIndex) ?: row.createCell(columnIndex)
                        if (doubleFields.contains(key) && value.isNotEmpty()) {
                            cell.setCellValue(value.toDouble())
                        } else {
                            cell.setCellValue(value) // Set as string for other fields or blank
                        }
                    }
                }

                // Save the updated workbook back to the source file
                val fos = FileOutputStream(filePath)
                workbook.write(fos)
                fos.close()
                workbook.close()

                // Now, perform sorting and data transfer using the passed coroutine scope
                coroutineScope.launch {
                    try {
                        // Step 1: Sort the Excel file by Start Time
                        val excelSorter = ExcelSorter(context, getCurrentShift(context))
                        excelSorter.sortExcelByStartTime("ProductionData", startTimeColumnIndex = 0) // Adjust index if needed
                        Log.d("ExcelSorter", "Excel file sorted successfully.")

                        // Step 2: Transfer sorted data to FormattedProductionDowntime
                        val excelManager = ExcelManager(context, getCurrentShift(context))
                        excelManager.transferData("ProductionData", "FormattedProductionDowntime")
                        Log.d("ExcelManager", "Data transfer completed successfully.")

                        // Update UI on the main thread after background operations are complete
                        withContext(Dispatchers.Main) {
                            notifyItemChanged(position)
                            AppUtils.showToast(context, "Data updated and sorted successfully!")
                        }
                    } catch (e: Exception) {
                        Log.e("ExcelDataAdapter", "Error during sorting and transfer: ${e.localizedMessage}")
                        withContext(Dispatchers.Main) {
                            AppUtils.showToast(context, "Error updating data: ${e.localizedMessage}")
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ExcelDataAdapter", "Error saving data", e)
                AppUtils.showToast(context, "Failed to save data: ${e.message}")
            }
        }

        override fun getItemCount(): Int = data.size
    }
}
