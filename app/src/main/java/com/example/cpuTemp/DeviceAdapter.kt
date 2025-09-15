package com.example.cpuTemp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val items: MutableList<DeviceItem>,
    private val onClick: (DeviceItem) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val addr: TextView = v.findViewById(R.id.tvAddr)
        val rssi: TextView = v.findViewById(R.id.tvRssi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = if (item.name.isNotBlank()) item.name else "(no name)"
        holder.addr.text = item.address
        holder.rssi.text = "RSSI: ${item.rssi}"

        // 👇 Use the item, not the View (it)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onClick(items[pos])
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
