package com.yywspace.anethack.window

import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHLineCommand
import com.yywspace.anethack.extensions.showImmersive


class NHQuestion(val nh: NetHack) {
    private fun finishLine(line:String) {
        nh.command.sendCommand(NHLineCommand(line))
    }
    fun waitForLine():String {
        val cmd = nh.command.waitForCommand()
        return if(cmd is NHLineCommand)
            cmd.line
        else {
            nh.command.sendCommand(cmd)
            ""
        }
    }
    public fun showInputQuestion(question: String,  bufSize: Int) {
        nh.runOnUi { _, context ->
            View.inflate(context, R.layout.dialog_question_input,null)
                .apply {
                    var ques = question
                    var hintStr = ""
                    Regex("(.*)\\[(.*)]").find(question)?.apply {
                        if(groupValues.size >= 3 && groupValues[1].isNotEmpty()) {
                            ques = groupValues[1]
                            hintStr = groupValues[2]
                        }
                    }
                    val input = findViewById<EditText>(R.id.dialog_question_input).apply {
                        if (hintStr.isNotEmpty())
                            this.hint = hintStr
                    }
                    val dialog = AlertDialog.Builder(context).run {
                        setTitle(ques)
                        setView(this@apply)
                        setPositiveButton(R.string.dialog_confirm) { _, _ ->
                            // cancel name
                            if(input.text.isEmpty()) {
                                finishLine("")
                                return@setPositiveButton
                            }

                            if(input.text.length > bufSize)
                                finishLine(input.text.substring(0, bufSize))
                            else
                                finishLine(input.text.toString())
                        }
                        setNegativeButton(R.string.dialog_cancel) { _, _ ->
                            // cancel naming attempt
                            finishLine(27.toChar().toString())
                        }
                    }
                    dialog.setCancelable(false)
                    dialog.showImmersive()
                }
        }
    }

    public fun showSelectQuestion(question: String, choices: String, ynNumber:LongArray, def: Char) {
        Log.d("NHQuestion", "question:$question choices:$choices def:$def")
        if (choices.isNotEmpty())
            ynQuestion(question, choices, ynNumber, def)
        else
            selectQuestion(question, def)
    }


    private fun selectQuestion(question: String, def: Char) {
        val regex = Regex("(.*)\\[(.*)](.*)")
        regex.find(question)?.apply {
            Log.d("NHQuestion", groupValues.toString())
            val ques = "${groupValues[1]}${groupValues[3]}"
            val select = mutableListOf<Pair<Char, Int>>()
            groupValues[2].split(" ").forEachIndexed { j, it->
                if (it.length < 2) {
                    select.add(Pair(it[0], 1))
                } else if ((j == 0 && it == "or") || it != "or") {
                    var index = 0
                    for ((i, choice) in it.withIndex()) {
                        if(i < index)
                            continue
                        if(choice == '-') {
                            for (subChoice in it[i-1].code + 1 .. it[i+1].code)
                                select.add(Pair(subChoice.toChar(), 1))
                            index += 2
                            continue
                        }
                        if((choice == '?') or (choice == '*'))
                            select.add(Pair(choice, -1))
                        else
                            select.add(Pair(choice, 1))
                        index++
                    }
                }
            }
            // for select question additional provide ?*
            if(!question.contains("?*")) {
                select.add(Pair('?', -1))
                select.add(Pair('*', -1))
            }
            select.add(Pair(27.toChar(), -1))

            buildDialog(ques, select,null, def)
        }
    }

    private fun ynQuestion(question: String, choices: String, ynNumber:LongArray?, def: Char) {
        val select = choices.toCharArray().filter {
            it!= ' '
        }.toMutableList().run {
            add(27.toChar())
            map {
                Pair(it, if(it == '#') 1 else -1)
            }.toMutableList()
        }
        buildDialog(question, select, ynNumber, def)
    }
    private fun buildDialog(question: String, choices:MutableList<Pair<Char, Int>>, ynNumber: LongArray?, def: Char) {
        nh.runOnUi { _, context ->
            val dialog = AlertDialog.Builder(context).create()
            val questionView = View.inflate(context, R.layout.dialog_question,null)
                .apply {
                    findViewById<TextView>(R.id.dialog_question).text = question
                    val colCount = if (choices.size < 3) choices.size else 3
                    findViewById<RecyclerView>(R.id.dialog_question_answer).apply {
                        layoutManager = GridLayoutManager(context, colCount).apply {
                            spanSizeLookup = object :GridLayoutManager.SpanSizeLookup() {
                                override fun getSpanSize(position: Int): Int {
                                    return 1
                                }
                            }
                        }
                        adapter = NHQuestionAnswerAdapter(choices).apply {
                            onItemClick = { _, _, answer ->
                                if(answer.first.code == 27)
                                    finishAnswer(def, -1)
                                else if(answer.first == '#') {
                                    ynNumber?.set(0, answer.second.toLong())
                                    finishAnswer('#', -1)
                                } else
                                    finishAnswer(answer.first, answer.second)
                                dialog.dismiss()
                            }
                            onItemLongClick = { _, index, _ ->
                                showNumberInputDialog(context, choices, index, this)
                            }
                        }
                    }
                }
            dialog.apply {
                setView(questionView)
                setCancelable(false)
                showImmersive()
            }
        }
    }

    private fun showNumberInputDialog(context: Context, choices:MutableList<Pair<Char, Int>>,
                                      parentPosition:Int, parentAdapter: NHQuestionAnswerAdapter) {
        val answer = choices[parentPosition]
        val dialogView = View.inflate(context, R.layout.dialog_number_input, null)
        val input = dialogView.findViewById<EditText>(R.id.dialog_number_input).apply {
            setText(answer.second.toString())
        }
        val title = context.resources.getString(R.string.number_select)
        val dialog = AlertDialog.Builder(context).run {
            setTitle(title)
            setView(dialogView)
            setPositiveButton(R.string.dialog_confirm){ _, _ ->
                if(input.text.isNotEmpty()) {
                    choices[parentPosition] = answer
                        .copy(second = input.text.toString().toInt())
                    parentAdapter.notifyItemChanged(parentPosition)
                }
            }
            setNegativeButton(R.string.dialog_cancel){ _, _ ->

            }
            create()
        }
        dialog.setCancelable(false)
        dialog.showImmersive()

    }
    private fun finishAnswer(answer:Char, count:Int) {
        nh.command.sendCommand(NHCommand(answer, count))
    }
    fun waitForAnswer():Char {
        val cmd = nh.command.waitForCommand()
        return cmd.key
    }

    private class NHQuestionAnswerAdapter(val answers:List<Pair<Char,Int>>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var onItemClick:((view:View, index:Int, item:Pair<Char,Int>)->Unit)? = null
        var onItemLongClick:((view:View, index:Int, item:Pair<Char,Int>)->Unit)? = null

        inner class ButtonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val button : Button
            init {
                button = view.findViewById(R.id.item_answer_btn)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dialog_question_answer_item, parent, false)
            return ButtonViewHolder(view)
        }

        override fun getItemViewType(position: Int): Int {
            return super.getItemViewType(position)
        }

        override fun getItemCount(): Int {
            return answers.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val answer = answers[position]
            (holder as ButtonViewHolder).apply {
                if(answer.first.code == 27) {
                    button.text = holder.itemView.context.getString(R.string.button_esc)
                }else{
                    button.text = if(answer.second > 1)
                         "[${answer.second}]${answer.first}"
                    else
                        answer.first.toString()
                }
                button.setOnClickListener {
                    onItemClick?.invoke(it, position, answer)
                }
                button.setOnLongClickListener {
                    if(answer.second > 0)
                        onItemLongClick?.invoke(it, position, answer)
                    true
                }
            }
        }
    }
}