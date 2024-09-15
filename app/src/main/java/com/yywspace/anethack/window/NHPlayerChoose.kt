package com.yywspace.anethack.window

import android.content.DialogInterface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHPlayerChooseCommand
import com.yywspace.anethack.entity.NHPlayer
import com.yywspace.anethack.extensions.show

class NHPlayerChoose(val nh: NetHack) {
    private var nameSize = 0
    lateinit var saves: Array<String>
    var players: MutableList<NHPlayer> = mutableListOf()

    fun askName(nameSize: Int, saves: Array<String>) {
        this.nameSize = nameSize
        this.saves = saves
        var hasWizardSave = false
        saves.distinct().forEach {
            if(it == PLAY_MOD_WIZARD) {
                hasWizardSave = true
                players.add(0, NHPlayer(it, PLAY_MOD_WIZARD))
            }else {
                players.add(NHPlayer(it, nh.prefs.getSaves()[it]?:"undefine"))
            }
        }
        if(!hasWizardSave)
            players.add(0, NHPlayer(PLAY_MOD_WIZARD, PLAY_MOD_WIZARD))
        showPlayerChooseDialog(players)
    }

    fun waitForPlayerChoose():Array<String> {
        val cmd = nh.command.waitForAnyCommand<NHPlayerChooseCommand>()
        Log.d("waitForPlayerChoose", "${cmd.player} ${cmd.toInfoArray()}")
        return cmd.toInfoArray()
    }

    private fun finishPlayerChoose(player:NHPlayer) {
        nh.command.sendCommand(NHPlayerChooseCommand(player))
    }


    private fun showPlayerChooseDialog(players: List<NHPlayer>) {
        nh.runOnUi { _, context ->
            val dialog = AlertDialog.Builder(context).run {
                setTitle(R.string.player_select)
                setPositiveButton(R.string.dialog_confirm, null)
                setNeutralButton(R.string.dialog_cancel) { _,_ ->
                    nh.stop()
                    nh.context.finish()
                }
                create()
            }
            val view = View.inflate(context, R.layout.dialog_player_choose,null)
                .apply {
                    findViewById<RecyclerView>(R.id.player_choose_list).apply {
                        layoutManager = LinearLayoutManager(context)
                        adapter = NHPlayerChooseAdapter(players).apply {
                            onPlayerAddClick = {
                                showNewPlayerDialog(dialog)
                            }
                        }
                    }
                }
            dialog.apply {
                setView(view)
                setCancelable(false)
                show(nh.prefs.immersiveMode)
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val first = players.firstOrNull { item -> item.isSelected }
                    if(first == null) {
                        return@setOnClickListener
                    }else {
                        finishPlayerChoose(first)
                        dialog.dismiss()
                    }
                    Log.d("showPlayerChooseDialog", first.toString())
                }
            }
        }
    }

    private fun showNewPlayerDialog(parentDialog: AlertDialog) {
        nh.runOnUi() { _, context ->
            val view = View.inflate(context, R.layout.dialog_player_choose_input, null)
            val input = view.findViewById<EditText>(R.id.player_input)
            val playModG = view.findViewById<RadioGroup>(R.id.play_mod_radio_group)
            val dialog = AlertDialog.Builder(context).run {
                setTitle(R.string.player_input)
                setPositiveButton(R.string.dialog_confirm) { _,_ ->
                    val playMod = when (playModG.checkedRadioButtonId) {
                        R.id.play_mod_normal -> PLAY_MOD_NORMAL
                        R.id.play_mod_discover -> PLAY_MOD_DISCOVER
                        else -> PLAY_MOD_WIZARD
                    }
                    val player = input.text.toString()
                    if (player.isEmpty())
                        return@setPositiveButton
                    if(player.length >= nameSize) {
                        Toast.makeText(context, "Player $player name too long.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    players.forEach {
                        if(player == it.player) {
                            Toast.makeText(context, "Player $player already exists.", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                    }
                    parentDialog.dismiss()
                    nh.prefs.addSaves(player, playMod)
                    finishPlayerChoose(NHPlayer(player, playMod))
                }
                setNegativeButton(R.string.dialog_cancel, null)
                create()
            }

            dialog.apply {
                setView(view)
                setCancelable(false)
                show(nh.prefs.immersiveMode)
            }
        }
    }


    private class NHPlayerChooseAdapter(val players:List<NHPlayer>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var onItemClick:((view: View, index:Int, item:NHPlayer)->Unit)? = null
        var onPlayerAddClick:((view: View)->Unit)? = null

        inner class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val itemPlayer : TextView
            val itemPlayMod : TextView
            val itemCheckbox : CheckBox
            init {
                itemPlayer = view.findViewById(R.id.item_player)
                itemPlayMod = view.findViewById(R.id.item_play_mod)
                itemCheckbox = view.findViewById(R.id.item_checkbox)
            }
        }

        inner class PlayerAddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val itemPlayerAdd : TextView
            init {
                itemPlayerAdd = view.findViewById(R.id.item_player_add)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

            return if(viewType == ITEM_PLAYER) {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.dialog_player_choose_item, parent, false)
                PlayerViewHolder(view)
            }else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.dialog_player_choose_item_add, parent, false)
                PlayerAddViewHolder(view)
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if(position == players.size)
                ITEM_ADD
            else
                ITEM_PLAYER
        }

        override fun getItemCount(): Int {
            return players.size + 1
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

            when(getItemViewType(position)) {
                ITEM_PLAYER -> {
                    val player = players[position]
                    (holder as PlayerViewHolder).apply {
                        itemPlayer.text = player.player
                        itemPlayMod.text = player.playMod
                        itemCheckbox.isChecked = player.isSelected
                        itemView.setOnClickListener {
                            for((i, p) in players.withIndex()) {
                                if (p.isSelected) {
                                    p.isSelected = false
                                    notifyItemChanged(i)
                                    break
                                }
                            }
                            itemCheckbox.isChecked = true
                            player.isSelected = true
                            onItemClick?.invoke(it, position, player)
                        }
                    }
                }
                ITEM_ADD -> {
                    (holder as PlayerAddViewHolder).apply {
                        itemView.setOnClickListener {
                            onPlayerAddClick?.invoke(it)
                        }
                    }

                }
            }

        }
        companion object {
            const val ITEM_ADD = 0
            const val ITEM_PLAYER = 1
        }
    }

    companion object {
        const val PLAY_MOD_WIZARD = "wizard"
        const val PLAY_MOD_DISCOVER = "discover"
        const val PLAY_MOD_NORMAL = "normal"
    }

}