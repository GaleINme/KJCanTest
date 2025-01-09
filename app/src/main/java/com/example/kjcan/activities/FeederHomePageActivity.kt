package com.example.kjcan.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kjcan.R
import com.google.firebase.database.*
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kjcan.activities.AppUtils.showToast
import com.google.android.material.navigation.NavigationView

@ExperimentalGetImage
class FeederHomePageActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var rosterRecyclerView: RecyclerView
    private lateinit var adapter: DutyRosterAdapter
    private lateinit var emptyStateText: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val dutyRosterList = mutableListOf<User>()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val database: DatabaseReference by lazy { FirebaseDatabase.getInstance().getReference("users") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.feeder_home_page)

        sharedPreferences = AppUtils.getSharedPreferences(this,"LoginPrefs")

        setupToolbar()
        setupNavigationDrawer()
        // Setup buttons

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            logout()
        }

        // Setup RecyclerView for Duty Roster
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
                    showToast(this@FeederHomePageActivity,"Refreshed")
                    // Optionally refresh your UI or RecyclerView
                    fetchDutyRoster()
                    swipeRefreshLayout.isRefreshing = false
                } else {
                    showToast(this@FeederHomePageActivity, "Failed to fetch shift for user!")
                    swipeRefreshLayout.isRefreshing = false
                }
            }


            override fun onCancelled(error: DatabaseError) {
                showToast(this@FeederHomePageActivity,"Error: ${error.message}")
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    // Set up toolbar with company name
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.company_name)
    }

    // Configure navigation drawer and its item click events
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

        adapter = DutyRosterAdapter(this, dutyRosterList) { _, _ ->
            // Feeder cannot modify roles, no callback needed
        }
        rosterRecyclerView.adapter = adapter
    }


    private fun fetchDutyRoster() {
        val currentShift = sharedPreferences.getString("userShift", null)

        if (currentShift.isNullOrEmpty()) {
            showToast(this,"Shift not set!")
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
                Toast.makeText(this@FeederHomePageActivity, "Error fetching data: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // Show empty state UI
    private fun showEmptyState() {
        emptyStateText.visibility = View.VISIBLE
        rosterRecyclerView.visibility = View.GONE
    }

    // Update and show duty roster UI
    private fun showDutyRoster(updatedRoster: List<User>) {
        emptyStateText.visibility = View.GONE
        rosterRecyclerView.visibility = View.VISIBLE
        adapter.updateData(updatedRoster.distinctBy { it.username }) // Deduplicate by username
    }

    private fun logout() {
        sharedPreferences.edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
