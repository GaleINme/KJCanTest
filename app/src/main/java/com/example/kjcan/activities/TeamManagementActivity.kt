package com.example.kjcan.activities

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kjcan.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.*

class TeamManagementActivity : AppCompatActivity() {

    private lateinit var teamRecyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private val teamList = mutableListOf<Team>() // Will hold teams and members
    private val unassignedUsers = mutableListOf<User>() // Users without a team
    private lateinit var shiftList: Array<String> // Shift list from strings.xml
    private lateinit var teamArray: Array<String> // Team list from strings.xml
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_team_management)

        setupToolbarAndNav()
        initializeResources()
        setupRecyclerView()
        setupFab()

        fetchUsersFromFirebase()
    }

    private fun setupToolbarAndNav() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.company_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
    }

    // Handle the back button press
    override fun onSupportNavigateUp(): Boolean {
        finish() // Close this activity and return to the previous one
        return true
    }

    private fun initializeResources() {
        shiftList = resources.getStringArray(R.array.shift_array)
        teamArray = resources.getStringArray(R.array.team_arrays)
        database = FirebaseDatabase.getInstance().reference
    }

    private fun setupRecyclerView() {
        teamRecyclerView = findViewById(R.id.teamRecyclerView)
        teamRecyclerView.layoutManager = LinearLayoutManager(this)
        teamRecyclerView.adapter = TeamAdapter(teamList)
    }

    private fun setupFab() {
        fab = findViewById(R.id.addMemberButton)
        fab.setOnClickListener { showAddMemberDialog() }
    }

    private fun fetchUsersFromFirebase() {
        database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                teamList.clear()
                unassignedUsers.clear()

                val users = snapshot.children.mapNotNull { userSnapshot ->
                    val username = userSnapshot.child("username").value as? String ?: return@mapNotNull null
                    val team = userSnapshot.child("team").value as? String ?: ""
                    val shift = userSnapshot.child("shift").value as? String ?: "Normal"
                    User(username, team, shift)
                }

                // Group users by team
                teamArray.forEach { teamName ->
                    val members = users.filter { it.team == teamName }
                    teamList.add(Team(teamName, members.toMutableList()))
                }

                // Find unassigned users
                unassignedUsers.addAll(users.filter { it.team.isEmpty() })

                teamRecyclerView.adapter?.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error fetching users: ${error.message}")
            }
        })
    }

    private fun showAddMemberDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_assign_team, null)
        val userSpinner = dialogView.findViewById<Spinner>(R.id.teamSpinner)
        val teamSpinner = dialogView.findViewById<Spinner>(R.id.shiftSpinner)

        // Set up user spinner for unassigned users
        val userAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, unassignedUsers.map { it.username })
        userAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userSpinner.adapter = userAdapter

        // Set up team spinner
        val teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, teamArray)
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamAdapter

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val selectedUser = userSpinner.selectedItem.toString()
                val selectedTeam = teamSpinner.selectedItem.toString()
                assignUserToTeam(selectedUser, selectedTeam)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun assignUserToTeam(username: String, teamName: String) {
        val user = unassignedUsers.find { it.username == username } ?: return
        user.team = teamName
        unassignedUsers.remove(user)

        // Find the team and its shift
        val team = teamList.find { it.name == teamName }
        val teamShift = team?.members?.firstOrNull()?.shift ?: "Normal" // Default to "Normal" if no members exist

        // Update the user's shift
        user.shift = teamShift

        // Add the user to the team
        team?.members?.add(user)

        // Update UI
        teamRecyclerView.adapter?.notifyDataSetChanged()

        // Update Firebase with the new team and shift
        database.child("users").child(username).apply {
            child("team").setValue(teamName)
            child("shift").setValue(teamShift)
        }
    }


    private fun removeUserFromTeam(teamName: String, username: String) {
        val team = teamList.find { it.name == teamName }
        val user = team?.members?.find { it.username == username } ?: return

        team.members.remove(user)
        user.team = "" // Mark user as unassigned
        unassignedUsers.add(user)

        // Update UI and Firebase
        teamRecyclerView.adapter?.notifyDataSetChanged()
        database.child("users").child(username).child("team").setValue("")
    }


    private fun handleShiftChange(team: Team, newShift: String) {
        team.members.forEach { member ->
            member.shift = newShift
            database.child("users").child(member.username).child("shift").setValue(newShift)
        }
        // Get the other team
        val otherTeam = getOtherTeam(team)

        // Automatically adjust the other team's shift based on the new shift
        when (newShift) {
            "Morning" -> {
                otherTeam?.members?.forEach { member ->
                    member.shift = "Night"
                    database.child("users").child(member.username).child("shift").setValue("Night")
                }
            }
            "Night" -> {
                otherTeam?.members?.forEach { member ->
                    member.shift = "Morning"
                    database.child("users").child(member.username).child("shift").setValue("Morning")
                }
            }
            "Normal" -> {
                // Both teams are set to Normal
                team.members.forEach { member ->
                    member.shift = "Normal"
                    database.child("users").child(member.username).child("shift").setValue("Normal")
                }
                otherTeam?.members?.forEach { member ->
                    member.shift = "Normal"
                    database.child("users").child(member.username).child("shift").setValue("Normal")
                }
            }
        }
        teamRecyclerView.adapter?.notifyDataSetChanged()
    }

    private fun getOtherTeam(currentTeam: Team): Team? {
        return teamList.firstOrNull { it != currentTeam }
    }

    data class User(var username: String, var team: String, var shift: String)
    data class Team(val name: String, val members: MutableList<User>)

    inner class TeamAdapter(private val teams: List<Team>) :
        RecyclerView.Adapter<TeamAdapter.TeamViewHolder>() {

        inner class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val teamName: TextView = itemView.findViewById(R.id.teamNameTextView)
            val membersLayout: LinearLayout = itemView.findViewById(R.id.membersLayout)
            val shiftSpinner: Spinner = itemView.findViewById(R.id.shiftSpinner)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_team, parent, false)
            return TeamViewHolder(view)
        }

        override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
            val team = teams[position]
            holder.teamName.text = team.name
            holder.membersLayout.removeAllViews()

            team.members.forEach { member ->
                val memberView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_member, holder.membersLayout, false)
                val memberText = memberView.findViewById<TextView>(R.id.memberNameTextView)
                val removeButton = memberView.findViewById<ImageButton>(R.id.removeMemberButton)

                memberText.text = member.username
                removeButton.setOnClickListener {
                    removeUserFromTeam(team.name, member.username)
                }

                holder.membersLayout.addView(memberView)
            }

            val spinnerAdapter = ArrayAdapter(
                holder.itemView.context,
                android.R.layout.simple_spinner_item,
                shiftList
            )
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            holder.shiftSpinner.adapter = spinnerAdapter
            holder.shiftSpinner.setSelection(shiftList.indexOf(team.members.firstOrNull()?.shift ?: "Normal"))

            holder.shiftSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val selectedShift = shiftList[pos]
                    if (team.members.firstOrNull()?.shift != selectedShift) {
                        handleShiftChange(team, selectedShift)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        }

        override fun getItemCount(): Int = teams.size
    }
}
