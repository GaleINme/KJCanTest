package com.example.kjcan.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.kjcan.R
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ShiftSettingActivity : AppCompatActivity() {

    private lateinit var shiftSelector: Spinner
    private lateinit var startHourPicker: NumberPicker
    private lateinit var startMinutePicker: NumberPicker
    private lateinit var startAmPmPicker: NumberPicker
    private lateinit var endHourPicker: NumberPicker
    private lateinit var endMinutePicker: NumberPicker
    private lateinit var endAmPmPicker: NumberPicker
    private lateinit var saveButton: Button
    private lateinit var resetButton: Button

    private val database: DatabaseReference by lazy { FirebaseDatabase.getInstance().getReference("settings/shifts") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shift_setting_picker)

        initViews()
        setupShiftSelector()
        setupNumberPickers()

        saveButton.setOnClickListener { saveShiftTimings() }
        resetButton.setOnClickListener { resetToDefaults() }
        toolbarInit()
        loadShiftTimings(getSelectedShift())
    }

    private fun toolbarInit() {
        // Initialize and set up the Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Enable back/up navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
    }

    private fun initViews() {
        shiftSelector = findViewById(R.id.shiftSelector)
        startHourPicker = findViewById(R.id.startHourPicker)
        startMinutePicker = findViewById(R.id.startMinutePicker)
        startAmPmPicker = findViewById(R.id.startAmPmPicker)
        endHourPicker = findViewById(R.id.endHourPicker)
        endMinutePicker = findViewById(R.id.endMinutePicker)
        endAmPmPicker = findViewById(R.id.endAmPmPicker)
        saveButton = findViewById(R.id.saveButton)
        resetButton = findViewById(R.id.resetButton)
    }

    private fun setupShiftSelector() {
        val shiftNames = resources.getStringArray(R.array.shift_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, shiftNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        shiftSelector.adapter = adapter

        shiftSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedShift = parent?.getItemAtPosition(position).toString()
                loadShiftTimings(selectedShift)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Optional: Handle case when no shift is selected, if needed
                Toast.makeText(this@ShiftSettingActivity, "No shift selected", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun setupNumberPickers() {
        configurePicker(startHourPicker, 1..12)
        configurePicker(startMinutePicker, 0..59, format = "%02d")
        configurePicker(startAmPmPicker, listOf("AM", "PM"))

        configurePicker(endHourPicker, 1..12)
        configurePicker(endMinutePicker, 0..59, format = "%02d")
        configurePicker(endAmPmPicker, listOf("AM", "PM"))
    }

    private fun configurePicker(picker: NumberPicker, range: IntRange, format: String = "%d") {
        picker.minValue = range.first
        picker.maxValue = range.last
        picker.displayedValues = range.map { format.format(it) }.toTypedArray()
    }

    private fun configurePicker(picker: NumberPicker, values: List<String>) {
        picker.minValue = 0
        picker.maxValue = values.size - 1
        picker.displayedValues = values.toTypedArray()
    }

    private fun loadShiftTimings(shiftName: String) {
        database.child(shiftName).get().addOnSuccessListener { snapshot ->
            val start = parseShiftTime(snapshot.child("start").value.toString(), "07:00 AM")
            val end = parseShiftTime(snapshot.child("end").value.toString(), "07:00 PM")

            updatePickers(startHourPicker, start.first)
            updatePickers(startMinutePicker, start.second)
            updatePickers(startAmPmPicker, start.third)

            updatePickers(endHourPicker, end.first)
            updatePickers(endMinutePicker, end.second)
            updatePickers(endAmPmPicker, end.third)
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load shift timings.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseShiftTime(time: String, fallback: String): Triple<Int, Int, Int> {
        return try {
            val parts = time.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val amPm = if (parts[2] == "AM") 0 else 1
            Triple(hour, minute, amPm)
        } catch (e: Exception) {
            parseShiftTime(fallback, fallback)
        }
    }

    private fun saveShiftTimings() {
        val selectedShift = getSelectedShift()
        val timings = mapOf(
            "start" to formatShiftTime(startHourPicker, startMinutePicker, startAmPmPicker),
            "end" to formatShiftTime(endHourPicker, endMinutePicker, endAmPmPicker)
        )

        database.child(selectedShift).setValue(timings).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Shift timings updated successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to update timings.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetToDefaults() {
        loadShiftTimings(getSelectedShift())
    }

    private fun getSelectedShift(): String {
        return shiftSelector.selectedItem.toString()
    }

    private fun formatShiftTime(hourPicker: NumberPicker, minutePicker: NumberPicker, amPmPicker: NumberPicker): String {
        return "${hourPicker.value}:${minutePicker.value}:${if (amPmPicker.value == 0) "AM" else "PM"}"
    }

    private fun updatePickers(picker: NumberPicker, value: Int) {
        picker.value = value
    }

    // Handle the back button press
    override fun onSupportNavigateUp(): Boolean {
        finish() // Close this activity and return to the previous one
        return true
    }
}
