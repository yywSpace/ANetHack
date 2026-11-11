package com.yywspace.anethack.window

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
import com.google.android.material.button.MaterialButton
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHPlayerChooseCommand
import com.yywspace.anethack.entity.NHPlayer
import com.yywspace.anethack.extensions.show
import com.yywspace.anethack.log.AppLog


class NHPlayerChoose(val nh: NetHack) {
    private var nameSize = 0
    lateinit var saves: Array<String>
    var players: MutableList<NHPlayer> = mutableListOf()

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
        if (players.isEmpty())
            showNameInput()
        else
            showPlayerList(players)
    }

    fun waitForPlayerChoose():Array<String> {
        val cmd = nh.command.waitForAnyCommand<NHPlayerChooseCommand>()
        AppLog.d("NHPlayerChoose", "player=${cmd.player}")
        return cmd.toInfoArray()
    }

    private fun finishPlayerChoose(player:NHPlayer) {
        nh.command.sendCommand(NHPlayerChooseCommand(player))
    }


    private fun showPlayerList(players: List<NHPlayer>) {
        nh.runOnUi { _, context ->
            nh.dialogs.show(
                TAG_PLAYER_LIST,
                createPlayerListView(context, players),
                EmbeddedDialogController.Options(outsideTouchable = true, dim = false)
            )
        }
    }

    private fun createPlayerListView(context: android.content.Context, players: List<NHPlayer>): View =
        View.inflate(context, R.layout.dialog_player_choose, null).apply {
            findViewById<RecyclerView>(R.id.player_choose_list).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = NHPlayerChooseAdapter(players).apply {
                    onPlayerAddClick = {
                        showNewPlayerDialog()
                    }
                }
            }
            findViewById<MaterialButton>(R.id.player_choose_confirm).setOnClickListener {
                val first = players.firstOrNull { item -> item.isSelected }
                    ?: return@setOnClickListener
                nh.dialogs.dismiss(TAG_PLAYER_LIST)
                finishPlayerChoose(first)
            }
            findViewById<MaterialButton>(R.id.player_choose_cancel).setOnClickListener {
                // 交给游戏线程退出，避免 UI 线程直接 nh_terminate
                nh.dialogs.dismiss(TAG_PLAYER_LIST)
                nh.command.sendCommand(NHPlayerChooseCommand(NHPlayer("", "")))
            }
        }

    /** 明暗主题切换：由本类按当前状态重建 */
    fun onThemeChanged() {
        nh.dialogs.dismiss(TAG_PLAYER_LIST)
        nh.dialogs.dismiss(TAG_NAME_INPUT)
        if (players.isEmpty())
            showNameInput()
        else
            showPlayerList(players)
    }

    /** 列表里的“新建游戏”入口：保持系统 Dialog 实现 */
    private fun showNewPlayerDialog() {
        nh.runOnUi { _, context ->
            var dialog: AlertDialog? = null
            val view = createNameInputView(
                context,
                onConfirm = { player ->
                    dialog?.dismiss()
                    nh.dialogs.dismiss(TAG_PLAYER_LIST)
                    finishPlayerChoose(player)
                },
                onCancel = { dialog?.dismiss() }
            )
            dialog = AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(false)
                .create()
            dialog.show(nh.prefs.immersiveMode)
        }
    }

    /** 列表为空时的内嵌名字输入界面 */
    private fun showNameInput() {
        nh.runOnUi { _, context ->
            val view = createNameInputView(
                context,
                onConfirm = { player ->
                    nh.dialogs.dismiss(TAG_NAME_INPUT)
                    finishPlayerChoose(player)
                },
                onCancel = {
                    nh.dialogs.dismiss(TAG_NAME_INPUT)
                    nh.command.sendCommand(NHPlayerChooseCommand(NHPlayer("", "")))
                }
            )
            nh.dialogs.show(
                TAG_NAME_INPUT,
                view,
                EmbeddedDialogController.Options(outsideTouchable = true, dim = false)
            )
        }
    }

    private fun createNameInputView(
        context: android.content.Context,
        onConfirm: (NHPlayer) -> Unit,
        onCancel: () -> Unit
    ): View {
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
                Toast.makeText(
                    context,
                    context.getString(R.string.player_name_too_long, player),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            if (players.any { it.player == player }) {
                Toast.makeText(
                    context,
                    context.getString(R.string.player_name_exists, player),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            onConfirm(NHPlayer(player, playMod))
        }
        view.findViewById<MaterialButton>(R.id.player_input_cancel).setOnClickListener {
            onCancel()
        }
        return view
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
        private const val TAG_PLAYER_LIST = "player_list"
        private const val TAG_NAME_INPUT = "name_input"

        const val PLAY_MOD_WIZARD = "wizard"
        const val PLAY_MOD_DISCOVER = "discover"
        const val PLAY_MOD_NORMAL = "normal"
    }

}