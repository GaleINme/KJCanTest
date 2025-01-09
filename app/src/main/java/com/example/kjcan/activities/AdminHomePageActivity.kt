package com.example.kjcan.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.camera.core.ExperimentalGetImage
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.kjcan.R
import com.example.kjcan.activities.AppUtils.showToast
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

@ExperimentalGetImage
class AdminHomePageActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var rosterRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: DutyRosterAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val database by lazy { FirebaseDatabase.getInstance().getReference("users") }

    // Data
    private val dutyRosterList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_home_page)

        sharedPreferences = AppUtils.getSharedPreferences(this,"LoginPrefs")
        setupToolbar()
        setupNavigationDrawer()

        setupRecyclerView()
        fetchDutyRoster()

        // Schedule periodic worker for shift swapping
        scheduleShiftSwapWorker()
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        // Setup button click listeners
        findViewById<Button>(R.id.createUserButton).setOnClickListener { showCreateUserDialog() }
        findViewById<Button>(R.id.userManagementButton).setOnClickListener { navigateToUserList() }
        findViewById<Button>(R.id.teamManagementButton).setOnClickListener { navigateToTeamManagement() }
        findViewById<Button>(R.id.shiftManagementButton).setOnClickListener { navigateToShiftSetting() }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logout() }
        swipeRefreshLayout.setOnRefreshListener {
            refreshPage()
        }
    }

    private fun refreshPage() {
        val currentUsername = sharedPreferences.getString("username", null)
        if (currentUsername.isNullOrEmpty()) {
            showToast(this,"No user logged in!")
            return
        }

        // Fetch the latest shift from Firebase for the logged-in user
        val userRef = database.child(currentUsername) // Firebase reference to the user
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updatedShift = snapshot.child("shift").getValue(String::class.java)
                if (updatedShift != null) {
                    // Update the SharedPreferences with the new shift
                    sharedPreferences.edit().putString("userShift", updatedShift).apply()
                    showToast(this@AdminHomePageActivity,"Refreshed")

                    // Optionally refresh your UI or RecyclerView
                    fetchDutyRoster()
                    swipeRefreshLayout.isRefreshing = false
                } else {
                    showToast(this@AdminHomePageActivity, "Failed to fetch shift for user!")
                    swipeRefreshLayout.isRefreshing = false
                }
            }


            override fun onCancelled(error: DatabaseError) {
                showToast(this@AdminHomePageActivity, "Error: ${error.message}")
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.company_name)
    }

    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, findViewById<Toolbar>(R.id.toolbar),
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> Log.d("Navigation", "Settings selected")
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun setupRecyclerView() {
        rosterRecyclerView = findViewById(R.id.rosterRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        rosterRecyclerView.layoutManager = LinearLayoutManager(this)

        adapter = DutyRosterAdapter(this, dutyRosterList) { user, newRole ->
            if (user.role.equals("Admin", ignoreCase = true)) {
                showToast(this, "Admins cannot be displayed or edited in the duty roster!")
            } else {
                updateFirebaseRole(user, newRole)
            }
        }
        rosterRecyclerView.adapter = adapter
    }


    private fun fetchDutyRoster() {
        val currentShift = sharedPreferences.getString("userShift", null)

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updatedRoster = snapshot.children.mapNotNull { child ->
                    val user = child.getValue(User::class.java)
                    user?.takeIf {
                        it.shift.equals(currentShift, ignoreCase = true) &&
                                !it.role.equals("Admin", ignoreCase = true) // Exclude Admins
                    }
                }
                adapter.updateData(updatedRoster) // Pass updated list to adapter
                adapter.notifyDataSetChanged() // Temporarily force refresh
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminHomePageActivity, "Error fetching data: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }


    private fun updateFirebaseRole(user: User, newRole: String) {
        database.child(user.username).child("role").setValue(newRole)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("AdminHomePage", "Role updated for ${user.username} to $newRole")
                    showToast(this, "${user.username}'s role updated to $newRole")
                    fetchDutyRoster() // Refresh the roster after role update
                } else {
                    Log.e("AdminHomePage", "Failed to update role for ${user.username}: ${it.exception?.message}")
                    showToast(this,"Failed to update role. Please try again.")
                }
            }
    }

    private fun showCreateUserDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_user, null)
        val usernameField = dialogView.findViewById<EditText>(R.id.usernameField)
        val passwordField = dialogView.findViewById<EditText>(R.id.passwordField)
        val confirmPasswordField = dialogView.findViewById<EditText>(R.id.confirmPasswordField)
        val roleSpinner = dialogView.findViewById<Spinner>(R.id.roleSpinner)
        val shiftSpinner = dialogView.findViewById<Spinner>(R.id.shiftSpinner)
        val teamsSpinner = dialogView.findViewById<Spinner>(R.id.teamsSpinner)
        val showPasswordCheckBox = dialogView.findViewById<CheckBox>(R.id.showPasswordCheckBox)

        showPasswordCheckBox.text = getString(R.string.show_password)

        // Toggle password visibility
        showPasswordCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordField.inputType = inputType
            confirmPasswordField.inputType = inputType
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_user))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.create), null) // Set null initially to override later
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            val createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            createButton.setOnClickListener {
                val username = usernameField.text.toString().trim()
                val password = passwordField.text.toString().trim()
                val confirmPassword = confirmPasswordField.text.toString().trim()
                val role = roleSpinner.selectedItem.toString().trim()
                val shift = shiftSpinner.selectedItem.toString().trim()
                val team = teamsSpinner.selectedItem.toString().trim()

                if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    showToast(this, "All fields are required!")
                } else if (password != confirmPassword) {
                    showToast(this, "Passwords do not match! Please try again.")
                } else {
                    createUser(username, password, role, shift, team)
                    dialog.dismiss() // Dismiss dialog only after successful creation
                }
            }
        }

        dialog.show()
    }


    /**
     * Creates a new user in the Firebase database.
     */
    private fun createUser(username: String, plainPassword: String, role: String, shift: String, team: String) {
        val salt = AppUtils.generateSalt()
        val hashedPassword = AppUtils.hashPassword(plainPassword, salt)
        val newUser = User(username, hashedPassword, salt, role, shift, team)

        database.child(username).setValue(newUser).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                showToast(this, "User created successfully!")
            } else {
                showToast(this, "Failed to create user!")
            }
        }
    }

    /**
     * Schedules a periodic worker to swap shifts every Monday at midnight.
     */
    private fun scheduleShiftSwapWorker() {
        val workRequest = PeriodicWorkRequestBuilder<ShiftSwapWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ShiftSwapWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP, // Ensures only one instance is active
            workRequest
        )

        Log.d("ShiftSwapWorker", "Worker scheduled to maintain single instance.")
    }

    private fun calculateInitialDelay(): Long {
        val calendar = Calendar.getInstance().apply {
            if (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || get(Calendar.HOUR_OF_DAY) >= 7) {
                add(Calendar.DAY_OF_YEAR, (Calendar.MONDAY - get(Calendar.DAY_OF_WEEK) + 7) % 7)
            }
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis - System.currentTimeMillis()
    }

    private fun navigateToUserList() {
        startActivity(Intent(this, UserListActivity::class.java))
    }
    private fun navigateToTeamManagement(){
        startActivity(Intent(this, TeamManagementActivity::class.java))
    }

    private fun navigateToShiftSetting(){
        startActivity(Intent(this, ShiftSettingActivity::class.java))
    }


    private fun logout() {
        sharedPreferences.edit().clear().apply()
        Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
        }
    }

}

