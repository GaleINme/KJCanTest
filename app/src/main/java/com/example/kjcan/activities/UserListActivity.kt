package com.example.kjcan.activities

import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kjcan.R
import com.example.kjcan.activities.AppUtils.showToast
import com.google.firebase.database.*

class UserListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserAdapter
    private val database: DatabaseReference by lazy { FirebaseDatabase.getInstance().getReference("users") }
    private val userList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_list)
        // Initialize and set up the Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Enable back/up navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        setupRecyclerView()
        fetchUsers()
    }

    // Handle the back button press
    override fun onSupportNavigateUp(): Boolean {
        finish() // Close this activity and return to the previous one
        return true
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.userRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter(
            userList = userList,
            onDeleteClick = ::confirmDeleteUser,
            onEditClick = ::showEditUserDialog
        )
        recyclerView.adapter = adapter

        // Initialize the Search Bar
        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterUsers(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterUsers(newText)
                return true
            }
        })
    }

    // Filter users based on the search query
    private fun filterUsers(query: String?) {
        val filteredList = userList.filter { user ->
            user.username.contains(query ?: "", ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }


    private fun fetchUsers() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userList.clear()
                snapshot.children.mapNotNullTo(userList) { it.getValue(User::class.java) }
                userList.sortBy { it.username } // Sort users alphabetically
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                showToast(this@UserListActivity, "Failed to fetch users: ${error.message}")
            }
        })
    }


    private fun confirmDeleteUser(user: User) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_user))
            .setMessage("Are you sure you want to delete ${user.username}?")
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteUser(user) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteUser(user: User) {
        if (user.role.equals("admin", ignoreCase = true)) {
            Toast.makeText(this, "Admin user cannot be deleted!", Toast.LENGTH_SHORT).show()
            return
        }
        database.child(user.username).removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                userList.remove(user)
                adapter.notifyDataSetChanged()
                showToast(this, "${user.username} deleted!")
            } else {
                showToast(this, getString(R.string.failed_to_delete_user))
            }
        }
    }

    private fun showEditUserDialog(user: User) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_user, null)
        val usernameField = dialogView.findViewById<EditText>(R.id.usernameEdit)
        val passwordField = dialogView.findViewById<EditText>(R.id.passwordEdit)
        val confirmPasswordField = dialogView.findViewById<EditText>(R.id.confirmPasswordEdit)
        val roleSpinner = dialogView.findViewById<Spinner>(R.id.roleSpinner)
        val shiftSpinner = dialogView.findViewById<Spinner>(R.id.shiftSpinner)
        val teamSpinner = dialogView.findViewById<Spinner>(R.id.teamSpinner)
        val showPasswordCheckBox = dialogView.findViewById<CheckBox>(R.id.showPasswordCheckBox)

        usernameField.setText(user.username)

        // Set up role spinner
        val rolesAdapter = ArrayAdapter.createFromResource(this, R.array.roles_array, android.R.layout.simple_spinner_item)
        rolesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = rolesAdapter
        roleSpinner.setSelection(rolesAdapter.getPosition(user.role))

        // Set up shift spinner
        val shiftsAdapter = ArrayAdapter.createFromResource(this, R.array.shift_array, android.R.layout.simple_spinner_item)
        shiftsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        shiftSpinner.adapter = shiftsAdapter
        shiftSpinner.setSelection(shiftsAdapter.getPosition(user.shift))

        // Set up team spinner
        val teamsAdapter = ArrayAdapter.createFromResource(this, R.array.team_arrays, android.R.layout.simple_spinner_item)
        teamsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamsAdapter
        teamSpinner.setSelection(teamsAdapter.getPosition(user.team))

        // Toggle password visibility
        showPasswordCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordField.inputType = inputType
            confirmPasswordField.inputType = inputType
            passwordField.setSelection(passwordField.text.length)
            confirmPasswordField.setSelection(confirmPasswordField.text.length)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit User Details")
            .setView(dialogView)
            .setPositiveButton("Save", null) // Set null initially
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val updatedUsername = usernameField.text.toString().trim()
                val newPassword = passwordField.text.toString().trim()
                val confirmPassword = confirmPasswordField.text.toString().trim()
                val updatedRole = roleSpinner.selectedItem.toString()
                val updatedShift = shiftSpinner.selectedItem.toString()
                val updatedTeam = teamSpinner.selectedItem.toString()

                if (updatedUsername.isEmpty()) {
                    showToast(this, "Username cannot be empty!")
                    return@setOnClickListener
                }

                // Check and update password if fields are filled
                if (newPassword.isNotEmpty() && confirmPassword.isNotEmpty()) {
                    if (newPassword != confirmPassword) {
                        showToast(this, "Passwords do not match! Please try again.")
                        return@setOnClickListener
                    } else {
                        updatePassword(user, newPassword)
                    }
                }

                val updates = mapOf(
                    "username" to updatedUsername,
                    "role" to updatedRole,
                    "shift" to updatedShift,
                    "team" to updatedTeam
                )

                database.child(user.username).updateChildren(updates).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        showToast(this, "User updated successfully!")
                        fetchUsers()
                        dialog.dismiss() // Dismiss dialog after successful update
                    } else {
                        showToast(this, "Failed to update user!")
                    }
                }
            }
        }

        dialog.show()
    }


    private fun updatePassword(user: User, newPassword: String) {
        val salt = AppUtils.generateSalt()
        val hashedPassword = AppUtils.hashPassword(newPassword, salt)

        val updates = mapOf(
            "password" to hashedPassword,
            "salt" to salt
        )

        database.child(user.username).updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                showToast(this,"Password updated for ${user.username}!")
            } else {
                showToast(this,getString(R.string.failed_to_update_password))
            }
        }
    }
}

