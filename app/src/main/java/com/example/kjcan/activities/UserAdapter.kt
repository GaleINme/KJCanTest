package com.example.kjcan.activities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kjcan.R

class UserAdapter(
    private var userList: List<User>, // Changed to var for dynamic updates
    private val onDeleteClick: (User) -> Unit,
    private val onEditClick: (User) -> Unit // Renamed for clarity
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: TextView = itemView.findViewById(R.id.usernameItem)
        val role: TextView = itemView.findViewById(R.id.roleItem)
        val shift: TextView = itemView.findViewById(R.id.shiftItem)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        val editButton: Button = itemView.findViewById(R.id.editButton)

        fun bind(
            user: User,
            onDeleteClick: (User) -> Unit,
            onEditClick: (User) -> Unit
        ) {
            username.text = user.username
            role.text = user.role
            shift.text = user.shift

            deleteButton.setOnClickListener { onDeleteClick(user) }
            editButton.setOnClickListener { onEditClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_list, parent, false)
        return UserViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userList[position], onDeleteClick, onEditClick)
    }

    override fun getItemCount(): Int = userList.size

    // Update the adapter's list and refresh the RecyclerView
    fun updateList(newList: List<User>) {
        userList = newList
        notifyDataSetChanged()
    }
}
