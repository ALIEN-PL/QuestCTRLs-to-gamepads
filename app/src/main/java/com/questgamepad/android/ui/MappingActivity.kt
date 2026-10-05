package com.questgamepad.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.questgamepad.android.Prefs
import com.questgamepad.android.databinding.ActivityMappingBinding
import com.questgamepad.android.databinding.ItemButtonMappingBinding
import com.questgamepad.android.input.aggregator.QuestControllerAggregator
import com.questgamepad.android.input.mapping.QuestSourceButton
import com.questgamepad.android.input.mapping.TargetGamepadButton

class MappingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMappingBinding
    private lateinit var prefs: Prefs
    private var mappingsMap = mutableMapOf<QuestSourceButton, TargetGamepadButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMappingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        mappingsMap.putAll(prefs.loadMappings())

        binding.toolbarMapping.setNavigationOnClickListener { finish() }

        val adapter = MappingAdapter()
        binding.rvMappings.layoutManager = LinearLayoutManager(this)
        binding.rvMappings.adapter = adapter

        binding.btnResetDefaultMapping.setOnClickListener {
            mappingsMap.clear()
            mappingsMap.putAll(QuestControllerAggregator.defaultMappings())
            prefs.saveMappings(mappingsMap)
            adapter.notifyDataSetChanged()
        }
    }

    private fun showPickTargetDialog(source: QuestSourceButton, position: Int) {
        val targets = TargetGamepadButton.entries.toTypedArray()
        val names = targets.map { it.getDisplayName(isDualSense = true) }.toTypedArray()

        val currentTarget = mappingsMap[source] ?: TargetGamepadButton.NONE
        val selectedIndex = targets.indexOf(currentTarget)

        MaterialAlertDialogBuilder(this)
            .setTitle("Map [${source.displayName}] to:")
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                val chosen = targets[which]
                mappingsMap[source] = chosen
                prefs.saveMappings(mappingsMap)
                binding.rvMappings.adapter?.notifyItemChanged(position)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class MappingAdapter : RecyclerView.Adapter<MappingAdapter.ViewHolder>() {

        private val items = QuestSourceButton.entries.toTypedArray()

        inner class ViewHolder(val b: ItemButtonMappingBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemButtonMappingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val source = items[position]
            val target = mappingsMap[source] ?: TargetGamepadButton.NONE

            holder.b.tvSourceShort.text = source.shortLabel
            holder.b.tvSourceName.text = source.displayName
            holder.b.tvSourceCategory.text = source.category.title
            holder.b.btnTargetChip.text = target.getDisplayName(isDualSense = true)

            holder.b.btnTargetChip.setOnClickListener {
                showPickTargetDialog(source, position)
            }
            holder.b.root.setOnClickListener {
                showPickTargetDialog(source, position)
            }
        }
    }
}
