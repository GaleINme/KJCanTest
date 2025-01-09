package com.example.kjcan.activities

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.kjcan.R
import java.util.Locale

class DutyRosterAdapter(
    private val context: Context,
    private val dutyList: MutableList<User>,
    private val onRoleChanged: (User, String) -> Unit // Callback for role change
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
        private const val HEADER_USERNAME = "Username"
        private const val HEADER_ROLE = "Role"
        private const val HEADER_SHIFT = "Shift"
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val usernameLabel: TextView = itemView.findViewById(R.id.headerUsername)
        val roleLabel: TextView = itemView.findViewById(R.id.headerRole)
        val shiftLabel: TextView = itemView.findViewById(R.id.headerShift)
    }

    inner class DutyRosterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: TextView = itemView.findViewById(R.id.rosterUsername)
        val roleSpinner: Spinner = itemView.findViewById(R.id.roleSpinner)
        val shift: TextView = itemView.findViewById(R.id.rosterShift)

        fun bind(user: User, loggedInRole: String, loggedInUsername: String) {
            username.text = user.username
            shift.text = user.shift

            val roles = mutableListOf("Printer", "Feeder", "Packer")

            when (loggedInRole.lowercase(Locale.getDefault())) {
                "leader" -> {
                    if (user.username.equals(loggedInUsername, ignoreCase = true) || user.role.equals("Leader", ignoreCase = true)) {
                        roles.clear()
                        roles.add(user.role)
                    }
                }
                "feeder" -> {
                    roleSpinner.isEnabled = false // Feeder cannot edit roles
                }
                "admin" -> {
                    roles.add(0, "Leader")
                }
            }

            val adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, roles)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            roleSpinner.adapter = adapter

            // Set spinner selection to the user's current role
            val currentRoleIndex = roles.indexOfFirst { it.equals(user.role, ignoreCase = true) }
            if (currentRoleIndex >= 0) {
                roleSpinner.setSelection(currentRoleIndex, false)
            }

            // Disable spinner if not allowed to edit
            roleSpinner.isEnabled = loggedInRole.equals("admin", ignoreCase = true) || (loggedInRole.equals("leader", ignoreCase = true) && !user.username.equals(loggedInUsername, ignoreCase = true) && !user.role.equals("Leader", ignoreCase = true))
            roleSpinner.alpha = if (roleSpinner.isEnabled) 1.0f else 0.5f

            // Attach the listener
            roleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedRole = roles[position]

                    // Only trigger if the selected role is different
                    if (!user.role.equals(selectedRole, ignoreCase = true)) {
                        Log.d("DutyRosterAdapter", "Role changed for ${user.username} to $selectedRole")
                        onRoleChanged(user, selectedRole)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // No action needed
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_duty_roster, parent, false)
            DutyRosterViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.usernameLabel.text = HEADER_USERNAME
            holder.roleLabel.text = HEADER_ROLE
            holder.shiftLabel.text = HEADER_SHIFT
        } else if (holder is DutyRosterViewHolder) {
            val sharedPreferences = holder.itemView.context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
            val loggedInUsername = sharedPreferences.getString("username", "") ?: ""
            val loggedInRole = sharedPreferences.getString("userRole", "") ?: ""

            val adjustedPosition = position - 1 // Account for header row
            val user = dutyList[adjustedPosition]
            Log.d("DutyRosterAdapter", "Binding ViewHolder at position $position: ${user.username} (${user.role})")
            holder.bind(user, loggedInRole, loggedInUsername)
        }
    }

    override fun getItemCount(): Int = dutyList.size + 1 // Include header row

    fun updateData(newList: List<User>) {
        val roleOrder = listOf("Leader", "Printer", "Feeder", "Packer")
        Log.d("DutyRosterAdapter", "Initial list size: ${newList.size}")

        // Exclude Admins from the list
        val filteredList = newList.filterNot { it.role.equals("Admin", ignoreCase = true) }
        Log.d("DutyRosterAdapter", "Filtered list size (excluding Admins): ${filteredList.size}")

        // Remove duplicates by username
        val distinctList = filteredList.distinctBy { it.username }
        Log.d("DutyRosterAdapter", "Distinct list size: ${distinctList.size}")

        // Sort the list by role order and username
        val sortedList = distinctList.sortedWith(
            compareBy<User>({ roleOrder.indexOf(it.role).takeIf { index -> index != -1 } ?: Int.MAX_VALUE }) // Sort by role order
                .thenBy { it.username.lowercase(Locale.getDefault()) } // Sort alphabetically within roles
        )
        Log.d("DutyRosterAdapter", "Sorted list:\n" +
                sortedList.joinToString(separator = "\n") { "Username: ${it.username}, Role: ${it.role}" })

            // Calculate differences for smooth updates
            val diffCallback = DutyRosterDiffCallback(dutyList, sortedList)
            val diffResult = DiffUtil.calculateDiff(diffCallback)

            // Update the adapter's data
            dutyList.clear()
            dutyList.addAll(sortedList)
            Log.d("DutyRosterAdapter", "Updated duty list size: ${dutyList.size}")

            // Dispatch the updates to the adapter
            diffResult.dispatchUpdatesTo(this)
            Log.d("DutyRosterAdapter", "Adapter updates dispatched successfully")
        }


    class DutyRosterDiffCallback(
        private val oldList: List<User>,
        private val newList: List<User>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].username == newList[newItemPosition].username
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.username == newItem.username && oldItem.role == newItem.role && oldItem.shift == newItem.shift
        }
    }
}
