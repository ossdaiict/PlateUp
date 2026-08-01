package com.app.plateup.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.databinding.ItemCanteenContactInfoBinding
import com.app.plateup.models.CanteenContact

class ContactInfoAdapter(
    private val context: Context,
    private val contacts: List<CanteenContact>
) : RecyclerView.Adapter<ContactInfoAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCanteenContactInfoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCanteenContactInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.contactNameText.text = contact.name
        holder.binding.contactRoleText.text = contact.role
        holder.binding.contactPhoneText.text = formatPhoneNumber(contact.phoneNumber)

        holder.binding.callButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:${contact.phoneNumber}")
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = contacts.size

    private fun formatPhoneNumber(phone: String): String {
        return if (phone.startsWith("+91") && phone.length == 13) {
            "+91 " + phone.substring(3, 8) + " " + phone.substring(8)
        } else phone
    }
}
