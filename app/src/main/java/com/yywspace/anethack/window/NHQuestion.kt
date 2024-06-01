package com.yywspace.anethack.window

import android.R.attr.data
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHLineCommand
import com.yywspace.anethack.extensions.showImmersive
import java.nio.charset.StandardCharsets


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

    public fun showInputQuestion(question: String, input:String, bufSize: Int) {
        nh.runOnUi { _, context ->
            val dialogQuesView = View.inflate(context, R.layout.dialog_question_input, null)
            val dialog = AlertDialog.Builder(context).run {
                setView(dialogQuesView)
                setCancelable(false)
                create()
            }
            dialogQuesView.apply {
                    var ques = question
                    var hintStr = ""
                    Regex("(.*)\\[(.*)]").find(question)?.apply {
                        if(groupValues.size >= 3 && groupValues[1].isNotEmpty()) {
                            ques = groupValues[1]
                            hintStr = groupValues[2]
                        }
                    }
                    findViewById<TextView>(R.id.dialog_question_title).apply {
                        text = ques
                    }
                    val inputText = findViewById<AppCompatAutoCompleteTextView>(R.id.dialog_question_input).apply {
                        if (hintStr.isNotEmpty())
                            this.hint = hintStr
                        if (input.isNotEmpty()) {
                            this.setText(input)
                        }
                        val adapter = ArrayAdapter(
                            nh.context, android.R.layout.simple_dropdown_item_1line,
                            nh.prefs.getInputPrompts()
                        )
                        threshold = 1
                        setAdapter(adapter)
                    }
                    findViewById<Button>(R.id.input_btn_1).apply {
                        setText(R.string.dialog_cancel)

                        setOnClickListener {
                            // cancel naming attempt
                            // 如果输入框中以del 为前缀取消时，删除此历史记录
                            val delPrefix = "del "
                            if (inputText.text.startsWith(delPrefix)) {
                                nh.prefs.removeInputPrompts(inputText.text.substring(delPrefix.length))
                                inputText.setText("")
                            }
                            else {
                                finishLine(27.toChar().toString())
                                dialog.dismiss()
                            }
                        }
                    }
                    findViewById<Button>(R.id.input_btn_2).visibility = View.GONE
                    findViewById<Button>(R.id.input_btn_3).apply {
                        setText(R.string.dialog_confirm)
                        setOnClickListener {
                            // cancel name
                            if(inputText.text.isEmpty()) {
                                finishLine(" ")
                                dialog.dismiss()
                                return@setOnClickListener
                            }
                            if(inputText.text.length > bufSize)
                                finishLine(inputText.text.substring(0, bufSize))
                            else
                                finishLine(inputText.text.toString())
                            nh.prefs.addInputPrompts(inputText.text.toString())
                            dialog.dismiss()
                        }
                    }
                    dialog.showImmersive()
                }
        }
    }

    public fun showSelectQuestion(question: String, choices: String, ynNumber:LongArray, def: Char) {
        Log.d("NHQuestion", "question:$question choices:$choices def:$def")
        if (choices.isNotEmpty())
            ynQuestion(question, choices, ynNumber, def)
        else {
            if (question.startsWith("In what direction")) {
                return
            }
            selectQuestion(question, def)
        }
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