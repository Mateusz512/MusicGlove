package mateuszhinc.musicglove.adapter

import android.bluetooth.BluetoothDevice
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.reactivex.subjects.PublishSubject
import mateuszhinc.musicglove.R
import java.util.*


/**
 * Created by Math on 2017-12-03.
 */
class DeviceListAdapter(val clickSubject: PublishSubject<BluetoothDevice>)
    : RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {

    val items: MutableList<BluetoothDevice> = ArrayList()
    fun addItems(item: BluetoothDevice) {
        items.add(item)
        notifyDataSetChanged()
    }

    fun size() = items.size

    fun clear() {
        items.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_device, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = if(item.name.isNotEmpty()) item.name else "Unknown device"
        holder.address.text = item.address
        holder.layout.setOnClickListener({ clickSubject.onNext(item) })
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layout: View
        var name: TextView
        var address: TextView

        init {
            layout = itemView.findViewById(R.id.layout_root)
            name = itemView.findViewById(R.id.view_text_device_name)
            address = itemView.findViewById(R.id.view_text_device_address)
        }
    }
}
