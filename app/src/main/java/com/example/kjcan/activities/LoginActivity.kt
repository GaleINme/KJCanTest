package com.example.kjcan.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import com.example.kjcan.R
import com.google.firebase.database.*
import java.util.*

// User data class to encapsulate user-related information and provide utility functions
// Includes a function to validate passwords using a salted SHA-256 hash
data class User(
    val username: String = "",
    val password: String = "",
    val salt: String = "",
    val role: String = "",
    val shift: String = "",
    var team: String = ""
) {
    // Validates the input password by hashing it with the salt and comparing it to the stored hash
    fun isPasswordValid(inputPassword: String): Boolean {
        val hashedPassword = AppUtils.hashPassword(inputPassword,salt)
        return hashedPassword == password
    }
}

@ExperimentalGetImage
class LoginActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences // To store login session details
    private val database: DatabaseReference by lazy {
        FirebaseDatabase.getInstance().getReference("users")
    } // Firebase reference to users database
    private val users = mutableListOf<User>() // Local cache of user data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize shared preferences
        sharedPreferences = AppUtils.getSharedPreferences(this, "LoginPrefs")

        // Check if the user has already logged in today
        if (isLoggedInToday()) {
            navigateToHomePage(sharedPreferences.getString("userRole", null))
            return
        }

        setupUI() // Set up UI components and event listeners
        fetchUsersFromFirebase() // Fetch the list of users from Firebase
    }

    // Configures the login screen UI and sets up event listeners
    private fun setupUI() {
        val loginButton = findViewById<Button>(R.id.loginButton)
        val usernameField = findViewById<EditText>(R.id.usernameField)
        val passwordField = findViewById<EditText>(R.id.passwordField)
        val showPasswordCheckBox = findViewById<CheckBox>(R.id.showPasswordCheckBox)

        // Toggles password visibility based on checkbox state
        showPasswordCheckBox.setOnCheckedChangeListener { _, isChecked ->
            passwordField.transformationMethod = if (isChecked) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
            passwordField.setSelection(passwordField.text.length) // Maintain cursor position
        }

        // Handles login button click events
        loginButton.setOnClickListener {
            handleLogin(usernameField.text.toString().trim(), passwordField.text.toString().trim())
        }
    }

    // Handles the login logic, including validation and navigation
    private fun handleLogin(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            AppUtils.showToast(this, "Please enter both username and password!")
            return
        }

        val user = users.find { it.username.equals(username, ignoreCase = true) }
        if (user != null && user.isPasswordValid(password)) {
            if (user.role.isBlank() || !listOf("leader", "feeder", "admin").contains(user.role.lowercase(Locale.getDefault()))) {
                AppUtils.showToast(this, "Invalid user role in system! Contact support.")
                return
            }
            saveLoginDetails(user)
            navigateToHomePage(user.role)
        } else {
            AppUtils.showToast(this, "Invalid login credentials!")
        }
    }


    // Fetches the user data from Firebase and updates the local cache
    private fun fetchUsersFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                users.clear() // Clear the local cache
                snapshot.children.mapNotNullTo(users) { it.getValue(User::class.java) } // Populate users list
                Log.d("LoginActivity", "Users fetched successfully: $users")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LoginActivity", "Error fetching users", error.toException())
                AppUtils.showToast(this@LoginActivity,"Failed to fetch user data. Please try again.")
            }
        })
    }

    private fun navigateToHomePage(role: String?) {
        val intent = when (role?.lowercase(Locale.getDefault())) {
            "leader" -> Intent(this, LeaderHomePageActivity::class.java)
            "feeder" -> Intent(this, FeederHomePageActivity::class.java)
            "admin" -> Intent(this, AdminHomePageActivity::class.java)
            else -> {
                // Clear SharedPreferences for the invalid role
                val sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    clear()
                    apply()
                }
                AppUtils.showToast(this, "Invalid user role! Please log in again.")
                // Restart LoginActivity
                val restartIntent = Intent(this, LoginActivity::class.java)
                startActivity(restartIntent)
                finish()
                null
            }
        }
        if (intent != null) {
            startActivity(intent)
            finish()
        }
    }

    // Checks if the user has already logged in today
    private fun isLoggedInToday(): Boolean {
        val lastLoginDate = sharedPreferences.getString("lastLoginDate", null)
        return lastLoginDate == AppUtils.getCurrentDate() && sharedPreferences.contains("userRole")
    }

    // Saves login details to shared preferences
    private fun saveLoginDetails(user: User) {
        with(sharedPreferences.edit()) {
            putString("username", user.username) // Store the username for later use
            putString("lastLoginDate", AppUtils.getCurrentDate()) // Store today's date
            putString("userShift", user.shift) // Store the shift
            putString("userRole", user.role) // Store user role
            if (user.role.isBlank() || !listOf("leader", "feeder", "admin").contains(user.role.lowercase(Locale.getDefault()))) {
                with(sharedPreferences.edit()) {
                    remove("userRole")
                    apply()
                }
                AppUtils.showToast(this@LoginActivity, "Invalid user role in system! Contact support.")
                return
            }

            // Save additional details based on the user's role
            when (user.role.lowercase(Locale.getDefault())) {
                "leader" -> {
                    putString("leaderShift", user.shift)
                    putString("leaderTeam", user.team)
                    Log.d("LoginActivity", "Leader shift: ${user.shift}, team: ${user.team}")
                }

                "feeder" -> {
                    putString("feederShift", user.shift)
                    putString("feederTeam", user.team)
                    Log.d("LoginActivity", "Feeder shift: ${user.shift}, team: ${user.team}")
                }
            }

            apply() // Apply the changes
        }
    }
}