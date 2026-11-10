package com.yywspace.anethack.window

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHPlayerChooseCommand
import com.yywspace.anethack.log.AppLog
import com.yywspace.anethack.entity.NHPlayer
import com.yywspace.anethack.extensions.show


class NHPlayerChoose(val nh: NetHack) {
    private var nameSize = 0
    lateinit var saves: Array<String>
    var players: MutableList<NHPlayer> = mutableListOf()
    /** 当前显示的选人弹窗，主题切换时重建 */
    private var popup: PopupWindow? = null

    fun askName(nameSize: Int, saves: Array<String>) {
        this.nameSize = nameSize
        this.saves = saves
        saves.distinct().forEach {
            // 名字后面跟的是存档里记录的 playmode（'\t' 分隔，见 and_askname()）
            val name = it.substringBefore('\t')
            val playMod = when (it.substringAfter('\t', "").firstOrNull()) {
                'D' -> PLAY_MOD_WIZARD
                'X' -> PLAY_MOD_DISCOVER
                else -> PLAY_MOD_NORMAL
            }
            if(name.startsWith("$PLAY_MOD_WIZARD-")) {
                players.add(0, NHPlayer(name, playMod))
            }else {
                players.add(NHPlayer(name, playMod))
            }
        }
        if (players.isEmpty()) {
            showNewPlayerDialog(false)
        } else {
            showPlayerChooseDialog(players)
        }
    }

    fun waitForPlayerChoose():Array<String> {
        val cmd = nh.command.waitForAnyCommand<NHPlayerChooseCommand>()
        AppLog.d("NHPlayerChoose", "player=${cmd.player}")
        return cmd.toInfoArray()
    }

    private fun finishPlayerChoose(player:NHPlayer) {
        nh.command.sendCommand(NHPlayerChooseCommand(player))
    }


    private fun showPlayerChooseDialog(players: List<NHPlayer>) {
        nh.runOnUi { binding, context ->
            val popupWindow = PopupWindow(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            val view = View.inflate(context, R.layout.dialog_player_choose,null)
                .apply {
                    findViewById<RecyclerView>(R.id.player_choose_list).apply {
                        layoutManager = LinearLayoutManager(context)
                        adapter = NHPlayerChooseAdapter(players).apply {
                            onPlayerAddClick = {
                                showNewPlayerDialog(true)
                            }
                        }
                    }
                    findViewById<MaterialButton>(R.id.player_choose_confirm).apply {
                        setOnClickListener {
                            val first = players.firstOrNull { item -> item.isSelected }
                            if(first == null) {
                                return@setOnClickListener
                            }else {
                                finishPlayerChoose(first)
                                popupWindow.dismiss()
                            }
                        }
                    }
                    findViewById<MaterialButton>(R.id.player_choose_cancel).apply {
                        setOnClickListener {
                            // 交给游戏线程退出，避免 UI 线程直接 nh_terminate
                            nh.command.sendCommand(NHPlayerChooseCommand(NHPlayer("", "")))
                            popupWindow.dismiss()
                        }
                    }
                }

            popupWindow.apply {
                contentView = view
                // 等根视图 attach 到窗口后再显示：native 线程可能在 Activity 窗口就绪前触发弹窗，直接 showAtLocation 会抛 BadTokenException
                binding.root.post {
                    if (binding.root.isAttachedToWindow)
                        showAtLocation(binding.root, Gravity.CENTER, 0, 0)
                }
            }
            popup = popupWindow
        }
    }

    /** 明暗主题切换：按新配色重建仍在显示的选人弹窗 */
    fun onThemeChanged() {
        val showing = popup?.takeIf { it.isShowing } ?: return
        showing.dismiss()
        popup = null
        showPlayerChooseDialog(players)
    }

    private fun showNewPlayerDialog(fromPlayerList: Boolean) {
        nh.runOnUi { _, context ->
            val view = View.inflate(context, R.layout.dialog_player_choose_input, null)
            val input = view.findViewById<EditText>(R.id.player_input)
            val playModG = view.findViewById<RadioGroup>(R.id.play_mod_radio_group)
            val dialog = AlertDialog.Builder(context).run {
                setTitle(R.string.player_input)
                setPositiveButton(R.string.dialog_confirm) { _,_ ->
                    val playMod = when (playModG.checkedRadioButtonId) {
                        R.id.play_mod_normal -> PLAY_MOD_NORMAL
                        R.id.play_mod_discover -> PLAY_MOD_DISCOVER
                        R.id.play_mod_wizard -> PLAY_MOD_WIZARD
                        else -> PLAY_MOD_NORMAL
                    }
                    val player = input.text.toString()
                    if (player.isEmpty())
                        return@setPositiveButton
                    if(player.length >= nameSize) {
                        Toast.makeText(context, context.getString(R.string.player_name_too_long, player), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    players.forEach {
                        if(player == it.player) {
                            Toast.makeText(context, context.getString(R.string.player_name_exists, player), Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                    }
                    popup?.dismiss()
                    finishPlayerChoose(NHPlayer(player, playMod))
                }
                setNegativeButton(R.string.dialog_cancel) { _,_ ->
                    if (!fromPlayerList) {
                        // 没有选人列表可返回时，取消等同于退出选人流程
                        nh.command.sendCommand(NHPlayerChooseCommand(NHPlayer("", "")))
                    }
                }
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

        class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val itemPlayer : TextView = view.findViewById(R.id.item_player)
            val itemPlayMod : TextView = view.findViewById(R.id.item_play_mod)
            val itemCheckbox : CheckBox = view.findViewById(R.id.item_checkbox)
        }

        class PlayerAddViewHolder(view: View) : RecyclerView.ViewHolder(view)

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