package com.app.plateup.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemContactConfigBinding
import com.app.plateup.models.CanteenContact

class CanteenContactAdapter(
    private val contacts: MutableList<CanteenContact>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onPrimaryChanged: (Int) -> Unit
) : RecyclerView.Adapter<CanteenContactAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemContactConfigBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.contactNameText.text = contact.name
        holder.binding.contactRoleText.text = contact.role
        holder.binding.contactPhoneText.text = formatPhoneNumber(contact.phoneNumber)

        holder.binding.primaryToggle.isChecked = contact.isPrimary
        holder.binding.primaryToggle.setOnClickListener {
            onPrimaryChanged(holder.adapterPosition)
        }

        holder.binding.editIcon.setOnClickListener { onEdit(holder.adapterPosition) }
        holder.binding.deleteIcon.setOnClickListener { onDelete(holder.adapterPosition) }

        // Set icon based on role (simple mapping for now)
        holder.binding.roleIcon.setImageResource(R.drawable.ic_person)
    }

    override fun getItemCount() = contacts.size

    private fun formatPhoneNumber(phone: String): String {
        return if (phone.startsWith("+91") && phone.length == 13) {
            "+91 ${phone.substring(3, 8)} ${phone.substring(8)}"
        } else {
            phone
        }
    }
}
