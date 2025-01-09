        package com.example.kjcan.activities

        import android.Manifest
        import android.content.pm.PackageManager
        import android.os.Bundle
        import android.os.Environment
        import android.util.Log
        import android.view.View
        import android.widget.*
        import androidx.appcompat.app.AppCompatActivity
        import androidx.camera.core.*
        import androidx.camera.lifecycle.ProcessCameraProvider
        import androidx.camera.view.PreviewView
        import androidx.core.content.ContextCompat
        import com.example.kjcan.R
        import com.google.mlkit.vision.barcode.BarcodeScannerOptions
        import com.google.mlkit.vision.barcode.BarcodeScanning
        import com.google.mlkit.vision.common.InputImage
        import org.apache.poi.xssf.usermodel.XSSFWorkbook
        import org.apache.poi.ss.usermodel.WorkbookFactory
        import java.io.File
        import java.io.FileInputStream
        import java.io.FileOutputStream
        import java.util.*
        import java.util.concurrent.ExecutorService
        import java.util.concurrent.Executors
        import android.app.TimePickerDialog
        import androidx.activity.OnBackPressedCallback
        import androidx.appcompat.widget.Toolbar
        import androidx.lifecycle.lifecycleScope
        import com.example.kjcan.activities.AppUtils.calculateDuration
        import com.example.kjcan.activities.AppUtils.showToast
        import com.google.mlkit.vision.barcode.BarcodeScanner
        import com.example.kjcan.utils.ExcelSorter
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.launch
        import kotlinx.coroutines.withContext


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
        )

        fun EventData.toList(): List<String> {
            return listOf(
                startTime, endTime, eventDuration, eventType,
                downtimeType, category, remarks,
                jobId, quantity, thickness, height, width,
                actualProduced, motorSpeed
            )
        }

        @ExperimentalGetImage
        class ProductionActivity : AppCompatActivity() {

            private lateinit var ui: ProductionUI
            private lateinit var cameraExecutor: ExecutorService
            private var barcodeScanner: BarcodeScanner? = null

            private var isJobIdFieldActive = true
            private var isProcessingFrame = false

            private var cameraProvider: ProcessCameraProvider? = null

            private var eventData: EventData = EventData()

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.production)

                Log.d("Lifecycle", "onCreate called: Initializing UI and camera executor.")
                ui = ProductionUI(this)
                barcodeScanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().build())
                cameraExecutor = Executors.newSingleThreadExecutor()

                setupBackButtonHandler()
                AppUtils.copyTemplateFileIfNotExists(this, getCurrentShift())
                setupListeners()
            }

            private fun toolbarInit() {
                // Initialize and set up the Toolbar
                val toolbar = findViewById<Toolbar>(R.id.toolbar)
                setSupportActionBar(toolbar)

                // Enable back/up navigation
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
            }

            //Set up listeners for various UI components.
            private fun setupListeners() {
                ui.jobIdScanButton.setOnClickListener {
                    Log.d("UI", "Job ID Scan Button clicked.")
                    activateJobIdScanning()
                }
                ui.quantityScanButton.setOnClickListener {
                    Log.d("UI", "Quantity Scan Button clicked.")
                    activateQuantityScanning()
                }
                ui.submitProductionButton.setOnClickListener {
                    Log.d("UI", "Submit Production Button clicked.")
                    submitProductionForm()
                }
                ui.selectStartTimeButton.setOnClickListener {
                    Log.d("UI", "Select Start Time Button clicked.")
                    showTimePicker(true)
                }
                ui.selectEndTimeButton.setOnClickListener {
                    Log.d("UI", "Select End Time Button clicked.")
                    showTimePicker(false)
                }
                toolbarInit()
            }


            //Activates Job ID scanning functionality.
            private fun activateJobIdScanning() {
                isJobIdFieldActive = true
                Log.d("Scanner", "Activating Job ID scanning.")
                requestCameraPermission()
            }

            //Activates Quantity scanning functionality.
            private fun activateQuantityScanning() {
                isJobIdFieldActive = false
                Log.d("Scanner", "Activating Quantity scanning.")
                requestCameraPermission()
            }

            //Requests camera permissions.
            private fun requestCameraPermission() {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permissions", "Requesting camera permissions.")
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
                } else {
                    Log.d("Permissions", "Camera permission granted.")
                    startCameraPreview()
                }
            }

            //Starts the camera preview for barcode scanning.
            private fun startCameraPreview() {
                if (cameraProvider != null) {
                    Log.d("Camera", "Camera preview is already active.")
                    return
                }

                Log.d("Camera", "Starting camera preview.")
                ui.showBarcodeSection()
                initializeCamera()
            }

            private fun stopCameraPreview() {
                Log.d("Camera", "Stopping camera preview and releasing resources.")
                try {
                    isProcessingFrame = false
                    cameraProvider?.unbindAll()
                    cameraProvider = null
                    // Keep barcodeScanner open unless the activity is being destroyed
                } catch (e: Exception) {
                    Log.e("Camera", "Error stopping camera: ${e.localizedMessage}")
                }
            }

            override fun onPause() {
                super.onPause()
                Log.d("Lifecycle", "onPause called: Stopping camera preview.")
                stopCameraPreview()
            }

            override fun onStop() {
                super.onStop()
                Log.d("Lifecycle", "onStop called: Stopping camera preview.")
                stopCameraPreview()
            }

            // Handle the back button press
            override fun onSupportNavigateUp(): Boolean {
                finish() // Close this activity and return to the previous one
                return true
            }

            private fun initializeCamera() {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
                cameraProviderFuture.addListener({
                    try {
                        cameraProvider = cameraProviderFuture.get() // Store camera provider reference
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = ui.previewView.surfaceProvider
                        }
                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .apply { setAnalyzer(cameraExecutor, ::processImageProxy) }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        Log.d("Camera", "Binding camera lifecycle.")
                        cameraProvider?.unbindAll() // Ensure no previous bindings
                        cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                    } catch (e: Exception) {
                        Log.e("Camera", "Failed to initialize camera: ${e.localizedMessage}")
                        showToast(this, "Failed to initialize camera: ${e.localizedMessage}")
                    }
                }, ContextCompat.getMainExecutor(this))
            }

            //Processes each image frame from the camera to detect barcodes.
            private fun processImageProxy(imageProxy: ImageProxy) {
                Log.d("Scanner", "Processing image frame.")
                if (isProcessingFrame) {
                    Log.d("Scanner", "Skipping frame; already processing.")
                    imageProxy.close()
                    return
                }

                isProcessingFrame = true

                val mediaImage = imageProxy.image
                if (mediaImage == null || barcodeScanner == null) {
                    Log.d("Scanner", "Media image or scanner is null.")
                    imageProxy.close()
                    isProcessingFrame = false
                    return
                }

                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                barcodeScanner?.process(inputImage)
                    ?.addOnSuccessListener { barcodes ->
                        val scannedValue = barcodes.firstOrNull()?.displayValue
                        if (!scannedValue.isNullOrBlank()) handleScannedValue(scannedValue)
                    }
                    ?.addOnFailureListener {
                        Log.e("BarcodeError", it.localizedMessage ?: "Unknown error")
                    }
                    ?.addOnCompleteListener {
                        isProcessingFrame = false
                        imageProxy.close()
                    }
            }

            private fun handleScannedValue(value: String) {
                Log.d("Scanner", "Scanned value: $value")

                if (isJobIdFieldActive) {
                    // Assign the scanned value to the Job ID field only if Job ID scanning is active
                    ui.jobIdField.setText(value)
                    showToast(this, "Scan Complete")
                    stopCameraPreview()
                    ui.showFormSection()
                } else {
                    // Quantity scanning is active
                    if (isNumeric(value)) {
                        // Assign the scanned value to the Quantity field if it's numeric
                        ui.quantityAcquiredField.setText(value)
                        showToast(this, "Scan Complete")

                        // Stop camera preview only after Quantity scanning is complete
                        stopCameraPreview()
                        ui.showFormSection()
                    } else {
                        // Show a toast message if the scanned value is not numeric
                        showToast(this, "Invalid quantity scanned. Please scan a numeric value.")
                        Log.d("Validation", "Non-numeric value scanned for quantity: $value")

                        stopCameraPreview()
                    }
                    // Reset the processing flag for the next scan
                    isProcessingFrame = false
                }
            }

            private fun isNumeric(str: String): Boolean {
                return str.toDoubleOrNull() != null
            }

            private fun showTimePicker(isStartTime: Boolean) {
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                    val formattedTime = AppUtils.formatTime(selectedHour, selectedMinute)

                    // Assign user selection
                    if (isStartTime) {
                        eventData.startTime = formattedTime
                        ui.startTimeTextView.text = buildString {
                            append("Start Time: ")
                            append(formattedTime)
                        }
                    } else {
                        eventData.endTime = formattedTime
                        ui.endTimeTextView.text = buildString {
                            append("End Time: ")
                            append(formattedTime)
                        }
                    }

                    // If both times set, validate them
                    if (eventData.startTime != "N/A" && eventData.endTime != "N/A") {
                        val valid = AppUtils.isStartEndTimeValid(this, eventData.startTime, eventData.endTime)
                        if (!valid) {
                            // Clear or revert the newly chosen time
                            clearInvalidSelection(isStartTime)
                            return@TimePickerDialog
                        }

                        // If valid, proceed with your logic
                        val duration = calculateDuration(eventData.startTime, eventData.endTime)
                        eventData.eventDuration = duration.toString()
                        // Update any UI fields if needed
                    }

                }, hour, minute, false).show()
            }

            private fun clearInvalidSelection(isStartTime: Boolean) {
                if (isStartTime) {
                    eventData.startTime = "N/A"
                    ui.startTimeTextView.text = buildString {
                        append("Start Time: ")
                        append("N/A")
                    }
                } else {
                    eventData.endTime = "N/A"
                    ui.endTimeTextView.text = buildString {
                        append("End Time: ")
                        append("N/A")
                    }
                }
            }

            private fun submitProductionForm() {
                Log.d("Submission", "Submitting production form.")

                // Validation: Check if required fields are empty
                if (ui.areRequiredFieldsEmpty() || eventData.startTime == "N/A" || eventData.endTime == "N/A") {
                    Log.d("Validation", "Required fields are empty.")
                    showToast(this, "Please fill in all required fields!")
                    return
                }

                Log.d("Submission", "Submitting production form.")

                // Save production data locally
                saveProductionData()

                // Save data to Excel
                saveToExcel(eventData.toList())


                // Launch a coroutine in the lifecycleScope tied to the Activity's lifecycle
                lifecycleScope.launch {
                    try {
                        // Step 1: Sort the Excel file by Start Time
                        val excelSorter = ExcelSorter(this@ProductionActivity, getCurrentShift())
                        excelSorter.sortExcelByStartTime("ProductionData")
                        Log.d("ExcelSorter", "Excel file sorted successfully.")

                        // Step 2: Transfer sorted data to FormattedProductionDowntime
                        val excelManager = ExcelManager(this@ProductionActivity, getCurrentShift())
                        excelManager.transferData("ProductionData", "FormattedProductionDowntime")
                        Log.d("ExcelManager", "Data transferred to FormattedProductionDowntime successfully.")

                        // Step 3: Update UI on the main thread after background operations are complete
                        withContext(Dispatchers.Main) {
                            Log.d("Submission", "Production form submitted and sorted successfully.")
                            showToast(this@ProductionActivity, "Production data submitted successfully")
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e("ProductionActivity", "Error submitting production form: ${e.localizedMessage}", e)
                        withContext(Dispatchers.Main) {
                            showToast(this@ProductionActivity, "Failed to submit production data: ${e.message}")
                        }
                    }
                }
            }


            private fun saveProductionData() {
                Log.d("Data", "Saving production data.")
                eventData.apply {
                    startTime = eventData.startTime
                    endTime = eventData.endTime
                    eventDuration = eventData.eventDuration
                    eventType = "Production".toString()
                    jobId = ui.jobIdField.text.toString().uppercase()
                    quantity = ui.quantityAcquiredField.text.toString()
                    thickness = ui.thicknessField.text.toString()
                    height = ui.heightField.text.toString()
                    width = ui.widthField.text.toString()
                    actualProduced = ui.actualProducedField.text.toString()
                    motorSpeed = ui.motorSpeedField.text.toString()
                    remarks = ui.remarksField.text.toString()
                }
            }

            private fun saveToExcel(data: List<String>) {
                val fileName = AppUtils.generateFileName(getCurrentShift())
                val filePath = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    fileName
                )

                try {
                    val workbook: XSSFWorkbook
                    val sheetName = "ProductionData"
                    val sheet: org.apache.poi.ss.usermodel.Sheet

                    // Load or create workbook and sheet
                    if (filePath.exists()) {
                        val fis = FileInputStream(filePath)
                        workbook = WorkbookFactory.create(fis) as XSSFWorkbook
                        sheet = workbook.getSheet(sheetName) ?: workbook.createSheet(sheetName)
                        fis.close()
                    } else {
                        workbook = XSSFWorkbook()
                        sheet = workbook.createSheet(sheetName)

                        // Add header row
                        val headers = listOf(
                            "Start Time", "End Time", "Event Duration (Minutes)", "Event Type",
                            "Downtime Type", "Category", "Remarks",
                            "Job ID", "Quantity", "Thickness", "Height", "Width",
                            "Actual Produced Quantity", "Motor Speed"
                        )
                        val headerRow = sheet.createRow(0)
                        headers.forEachIndexed { index, header ->
                            headerRow.createCell(index).setCellValue(header)
                        }
                    }

                    // Append new data
                    val rowIndex = sheet.physicalNumberOfRows
                    val row = sheet.createRow(rowIndex)

                    data.forEachIndexed { index, value ->
                        val cell = row.createCell(index)
                        if (index == 2) { // Event Duration Column
                            val durationInMinutes = value
                            cell.setCellValue(durationInMinutes.toDouble())
                        } else if (index in listOf(8, 9, 10, 11, 12, 13)) { // Numeric columns
                            cell.setCellValue(value.toDoubleOrNull() ?: 0.0)
                        } else { // Text columns
                            cell.setCellValue(value)
                        }
                    }

                    // Save the workbook
                    val fos = FileOutputStream(filePath)
                    workbook.write(fos)
                    workbook.close()
                    fos.close()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onDestroy() {
                super.onDestroy()
                Log.d("Lifecycle", "onDestroy called: Cleaning up resources.")
                stopCameraPreview() // Stop the camera
                if (!cameraExecutor.isShutdown) {
                    cameraExecutor.shutdown() // Shut down executor
                }
            }

            private fun setupBackButtonHandler() {
                onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // Check if the barcode section is visible
                        if (ui.barcodeSection.visibility == View.VISIBLE) {
                            Log.d("Navigation", "Back pressed in camera preview mode. Returning to form.")
                            stopCameraPreview() // Stop the camera preview
                            ui.showFormSection() // Show the form section
                        } else {
                            Log.d("Navigation", "Back pressed. Exiting ProductionActivity.")
                            isEnabled = false // Disable this callback
                            onBackPressedDispatcher.onBackPressed() // Default system behavior
                        }
                    }
                })
            }

            private fun getCurrentShift(): String {
                val sharedPreferences = AppUtils.getSharedPreferences(this, "LoginPrefs")
                return sharedPreferences.getString("userShift", "Unassigned") ?: "Unassigned"
            }

        }

        @ExperimentalGetImage
        class ProductionUI(activity: ProductionActivity) {
            val jobIdField: EditText = activity.findViewById(R.id.jobIdField)
            val quantityAcquiredField: EditText = activity.findViewById(R.id.quantityAcquiredField)
            val thicknessField: EditText = activity.findViewById(R.id.thicknessField)
            val heightField: EditText = activity.findViewById(R.id.heightField)
            val widthField: EditText = activity.findViewById(R.id.widthField)
            val actualProducedField: EditText = activity.findViewById(R.id.actualProducedField)
            val motorSpeedField: EditText = activity.findViewById(R.id.motorSpeedField)
            val remarksField: EditText = activity.findViewById(R.id.remarksField)
            val jobIdScanButton: Button = activity.findViewById(R.id.jobIdScanButton)
            val quantityScanButton: Button = activity.findViewById(R.id.quantityScanButton)
            val submitProductionButton: Button = activity.findViewById(R.id.submitProductionButton)
            val selectStartTimeButton: Button = activity.findViewById(R.id.selectStartTimeButton)
            val selectEndTimeButton: Button = activity.findViewById(R.id.selectEndTimeButton)
            val barcodeSection: LinearLayout = activity.findViewById(R.id.barcodeSection)
            val nonBarcodeSection: LinearLayout = activity.findViewById(R.id.nonBarcodeSection)
            val previewView: PreviewView = activity.findViewById(R.id.cameraPreview)
            val startTimeTextView: TextView = activity.findViewById(R.id.startTimeTextView)
            val endTimeTextView: TextView = activity.findViewById(R.id.endTimeTextView)

            fun showBarcodeSection() {
                barcodeSection.visibility = View.VISIBLE
                nonBarcodeSection.visibility = View.GONE
            }

            fun showFormSection() {
                barcodeSection.visibility = View.GONE
                nonBarcodeSection.visibility = View.VISIBLE
            }

            fun areRequiredFieldsEmpty(): Boolean {
                return listOf(
                    jobIdField, quantityAcquiredField, thicknessField,
                    heightField, widthField, actualProducedField, motorSpeedField
                ).any { it.text.isBlank() }
            }
        }

