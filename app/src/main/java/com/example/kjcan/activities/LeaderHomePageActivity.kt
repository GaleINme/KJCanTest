package com.example.kjcan.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.camera.core.ExperimentalGetImage
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kjcan.R
import com.example.kjcan.activities.AppUtils.shareExcelFile
import com.example.kjcan.activities.AppUtils.showToast
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.*

@ExperimentalGetImage
class LeaderHomePageActivity : AppCompatActivity() {

    // Firebase and SharedPreferences
    private val database: DatabaseReference by lazy { FirebaseDatabase.getInstance().getReference("users") }
    private lateinit var sharedPreferences: SharedPreferences

    // UI Components
    private lateinit var rosterRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var adapter: DutyRosterAdapter
    private lateinit var toolbar: Toolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // Data
    private val dutyRosterList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.leader_home_page)
        sharedPreferences = AppUtils.getSharedPreferences(this,"LoginPrefs")
        setupToolbar()
        setupNavigationDrawer()
        setupButtons()
        setupRecyclerView()
        fetchDutyRoster()
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
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

                    Toast.makeText(this@LeaderHomePageActivity, "Shift updated to: $updatedShift", Toast.LENGTH_SHORT).show()

                    // Optionally refresh your UI or RecyclerView
                    fetchDutyRoster()
                    swipeRefreshLayout.isRefreshing = false
                } else {
                    Toast.makeText(this@LeaderHomePageActivity, "Failed to fetch shift for user!", Toast.LENGTH_SHORT).show()
                    swipeRefreshLayout.isRefreshing = false
                }
            }


            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LeaderHomePageActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    // Set up toolbar with company name
    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.company_name)
    }

    private fun setupNavigationDrawer() {
        Log.d("NavigationSetup", "setupNavigationDrawer called")
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            Log.d("NavigationSetup", "Menu item clicked: ${menuItem.title}")
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    Log.d("Navigation", "Settings selected")
                }
                R.id.nav_share -> {
                    Log.d("Navigation", "Share selected")
                    AppUtils.shareExcelFile(this, "YourExcelFileName.xlsx") // Replace with your actual file name
                }
            }
            drawerLayout.closeDrawers()
            true
        }
    }


    @ExperimentalGetImage
    // Set up button click listeners
    private fun setupButtons() {
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logout() }
        findViewById<Button>(R.id.changeOverChecklistButton).setOnClickListener {
            startActivity(Intent(this, ChangeOverChecklistActivity::class.java))
        }
        findViewById<Button>(R.id.productionButton).setOnClickListener {
            startActivity(Intent(this, ProductionActivity::class.java))
        }
        findViewById<Button>(R.id.systemDownButton).setOnClickListener {
            startActivity(Intent(this, SystemDownActivity::class.java))
        }
        findViewById<Button>(R.id.viewExcelButton).setOnClickListener {
            startActivity(Intent(this, ExcelViewerActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        rosterRecyclerView = findViewById(R.id.rosterRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        rosterRecyclerView.layoutManager = LinearLayoutManager(this)

        adapter = DutyRosterAdapter(this, dutyRosterList) { user, newRole ->
            if (user.username.equals(sharedPreferences.getString("username", ""), ignoreCase = true)) {
                Toast.makeText(this, "You cannot change your own role!", Toast.LENGTH_SHORT).show()
            } else if (newRole.equals("Leader", ignoreCase = true)) {
                Toast.makeText(this, "You cannot assign the Leader role!", Toast.LENGTH_SHORT).show()
            } else {
                updateFirebaseRole(user, newRole)
            }
        }
        rosterRecyclerView.adapter = adapter
    }



    private fun fetchDutyRoster() {
        val currentShift = sharedPreferences.getString("userShift", null)

        if (currentShift.isNullOrEmpty()) {
            Toast.makeText(this, "Shift not set!", Toast.LENGTH_LONG).show()
            return
        }

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updatedRoster = snapshot.children.mapNotNull { child ->
                    val user = child.getValue(User::class.java)
                    user?.takeIf {
                        it.shift.equals(currentShift, ignoreCase = true) &&
                                !it.role.equals("Admin", ignoreCase = true) // Exclude Admins
                    }
                }

                if (updatedRoster.isEmpty()) {
                    showEmptyState()
                } else {
                    showDutyRoster(updatedRoster)
                    adapter.notifyDataSetChanged() // Ensure RecyclerView reflects the changes
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LeaderHomePageActivity, "Error fetching data: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun updateFirebaseRole(user: User, newRole: String) {
        database.child(user.username).child("role").setValue(newRole)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("LeaderHomePage", "Role for ${user.username} updated to $newRole in Firebase")
                    showToast(this, "${user.username}'s role updated to $newRole")
                    // Fetch the updated roster and update the adapter
                    fetchDutyRoster()
                } else {
                    Log.e("LeaderHomePage", "Failed to update role for ${user.username}: ${it.exception?.message}")
                    showToast(this,"Failed to update role. Please try again.")
                }
            }
    }

    // Show empty state UI
    private fun showEmptyState() {
        emptyStateText.visibility = View.VISIBLE
        rosterRecyclerView.visibility = View.GONE
    }

    private fun showDutyRoster(updatedRoster: List<User>) {
        emptyStateText.visibility = View.GONE
        rosterRecyclerView.visibility = View.VISIBLE

        // Only update if the roster has changed
        if (dutyRosterList != updatedRoster) {
            adapter.updateData(updatedRoster.distinctBy { it.username }) // Deduplicate by username
        } else {
            Log.d("DutyRoster", "No changes detected in the roster, skipping update")
        }
    }



    // Handle logout process
    private fun logout() {
        sharedPreferences.edit().clear().apply()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_share -> {
                // Call your share function here
                val shift = sharedPreferences.getString("userShift", "LoginPrefs") ?: "Unassigned"
                shareExcelFile(this,AppUtils.generateFileName2(shift))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}