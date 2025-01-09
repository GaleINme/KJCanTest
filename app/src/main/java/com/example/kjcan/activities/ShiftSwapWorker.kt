package com.example.kjcan.activities

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class ShiftSwapWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val userDatabase = FirebaseDatabase.getInstance().getReference("users")
    private val settingsDatabase = FirebaseDatabase.getInstance().getReference("settings/shifts")

    override fun doWork(): Result {
        if (!isScheduledExecution()) {
            Log.d(TAG, "Skipped unscheduled execution.")
            return Result.success()
        }

        fetchShiftTimingsAndSwap()
        return Result.success()
    }

    private fun isScheduledExecution(): Boolean {
        val now = Calendar.getInstance()
        val isMondayMorning = now.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY && now.get(Calendar.HOUR_OF_DAY) == 7
        return isMondayMorning
    }

    private fun fetchShiftTimingsAndSwap() {
        settingsDatabase.get().addOnSuccessListener { snapshot ->
            val shiftMap = snapshot.children.associate { shift ->
                shift.key to shift.child("start").value.toString()
            }

            if (shiftMap.isEmpty()) {
                Log.e(TAG, "No shift timings found.")
                return@addOnSuccessListener
            }

            swapShiftsInDatabase(shiftMap)
        }.addOnFailureListener {
            Log.e(TAG, "Error fetching shift timings: ${it.message}")
        }
    }

    private fun swapShiftsInDatabase(shiftMap: Map<String?, String>) {
        userDatabase.get().addOnSuccessListener { snapshot ->
            val updates = mutableMapOf<String, Any>()

            for (child in snapshot.children) {
                val user = child.getValue(User::class.java)
                if (user != null) {
                    val newShift = determineNextShift(user.shift, shiftMap.keys.toList())
                    if (newShift != null) {
                        updates["${child.key}/shift"] = newShift
                        Log.d(TAG, "User ${user.username} shift updated to $newShift")
                    }
                }
            }

            applyShiftUpdates(updates)
        }.addOnFailureListener {
            Log.e(TAG, "Error fetching user data: ${it.message}")
        }
    }

    private fun determineNextShift(currentShift: String, shiftKeys: List<String?>): String? {
        val currentIndex = shiftKeys.indexOf(currentShift)
        if (currentIndex == -1) return null
        val nextIndex = (currentIndex + 1) % shiftKeys.size
        return shiftKeys[nextIndex]
    }

    private fun applyShiftUpdates(updates: Map<String, Any>) {
        if (updates.isEmpty()) {
            Log.d(TAG, "No shifts to update.")
            return
        }

        userDatabase.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Shifts swapped successfully.")
            } else {
                Log.e(TAG, "Error applying shift updates: ${task.exception}")
            }
        }
    }

    companion object {
        private const val TAG = "ShiftSwapWorker"
    }
}
