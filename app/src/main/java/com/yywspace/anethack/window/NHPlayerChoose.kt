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
import com.yywspace.anethack.entity.NHPlayer
import com.yywspace.anethack.extensions.show
import com.yywspace.anethack.extensions.showAtLocation
import com.yywspace.anethack.log.AppLog


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
        popup?.takeIf { it.isShowing }?.dismiss()
        popup = null
        if (players.isEmpty())
            showNewPlayerDialog(false)
        else
            showPlayerChooseDialog(players)
    }

    private fun showNewPlayerDialog(fromPlayerList: Boolean) {
        nh.runOnUi { binding, context ->
            val parentPopup = if (fromPlayerList) popup else null
            val view = View.inflate(context, R.layout.dialog_player_choose_input, null)
            val input = view.findViewById<EditText>(R.id.player_input)
            val playModG = view.findViewById<RadioGroup>(R.id.play_mod_radio_group)

            playModG.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.play_mod_wizard) {
                    // wizard 模式默认填入 wizard，但仍允许用户修改
                    input.setText(PLAY_MOD_WIZARD)
                } else if (input.text.toString() == PLAY_MOD_WIZARD) {
                    input.setText("")
                }
            }

            var dismissContainer: (() -> Unit)? = null
            view.findViewById<MaterialButton>(R.id.player_input_confirm).setOnClickListener {
                val playMod = when (playModG.checkedRadioButtonId) {
                    R.id.play_mod_normal -> PLAY_MOD_NORMAL
                    R.id.play_mod_discover -> PLAY_MOD_DISCOVER
                    R.id.play_mod_wizard -> PLAY_MOD_WIZARD
                    else -> PLAY_MOD_NORMAL
                }
                val player = input.text.toString()
                if (player.isEmpty())
                    return@setOnClickListener
                if (player.length >= nameSize) {
                    Toast.makeText(context, context.getString(R.string.player_name_too_long, player), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (players.any { it.player == player }) {
                    Toast.makeText(context, context.getString(R.string.player_name_exists, player), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dismissContainer?.invoke()
                parentPopup?.dismiss()
                finishPlayerChoose(NHPlayer(player, playMod))
            }
            view.findViewById<MaterialButton>(R.id.player_input_cancel).setOnClickListener {
                dismissContainer?.invoke()
                if (!fromPlayerList) {
                    popup = null
                    // 没有选人列表可返回时，取消等同于退出选人流程
                    nh.command.sendCommand(NHPlayerChooseCommand(NHPlayer("", "")))
                }
            }

            if (fromPlayerList) {
                // 有存档列表时用 Dialog：能完整覆盖列表，不会与列表窗口叠在一起
                val dialog = AlertDialog.Builder(context)
                    .setView(view)
                    .setCancelable(false)
                    .create()
                dismissContainer = { dialog.dismiss() }
                dialog.show(nh.prefs.immersiveMode)
            } else {
                // 没有存档时用 PopupWindow，保持非模态，底部命令栏仍可操作
                val popupWindow = PopupWindow(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                dismissContainer = { popupWindow.dismiss() }
                popupWindow.apply {
                    contentView = view
                    // 不抢焦点：点击外部不会让系统把它当成失焦窗口关闭
                    isFocusable = false
                    isOutsideTouchable = false
                    binding.root.post {
                        if (binding.root.isAttachedToWindow) {
                            showAtLocation(
                                binding.root, Gravity.CENTER, 0, 0,
                                nh.prefs.immersiveMode
                            )
                            input.requestFocus()
                        }
                    }
                }
                popup = popupWindow
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