package com.app.plateup.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.databinding.ItemContactReadOnlyBinding
import com.app.plateup.models.CanteenContact

class AdminContactAdapter(
    private val contacts: List<CanteenContact>
) : RecyclerView.Adapter<AdminContactAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemContactReadOnlyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactReadOnlyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.contactNameText.text = contact.name
        holder.binding.contactRoleText.text = contact.role
        holder.binding.contactPhoneText.text = formatPhoneNumber(contact.phoneNumber)
        
        holder.binding.primaryChip.visibility = if (contact.isPrimary) View.VISIBLE else View.GONE
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
