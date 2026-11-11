package com.yywspace.anethack.window

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import com.google.android.material.button.MaterialButton
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHAnswerCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.command.NHLineCommand
import com.yywspace.anethack.extensions.show


class NHQuestion(val nh: NetHack) {
    private companion object {
        const val TAG_INPUT = "question_input"
        const val TAG_QUESTION = "question"
    }

    // 防连点：一次输入/回答只发一条命令，避免残留命令被下一次同类型窗口消费。
    // 每次 answerInputQuestion / answerSelectQuestion 开始时重置。
    private var finished = false
    private var currentBufSize = 0

    private fun finishLine(line:String) {
        if (finished) return
        finished = true
        nh.command.sendCommand(NHLineCommand(line))
    }

    private fun waitForLine():String {
       return nh.command.waitForAnyCommand<NHLineCommand>().line
    }

    fun answerInputQuestion(question: String, input:String, bufSize: Int):String {
        finished = false
        // 弹窗前首先查找是否已存在NHLineCommand,如果有直接返回
        nh.command.findAnyCommand<NHLineCommand>()?.apply {
            return line
        }
        showInputQuestion(question, input, bufSize)
        return waitForLine()
    }

    private fun showInputQuestion(question: String, input:String, bufSize: Int) {
        currentBufSize = bufSize
        nh.runOnUi { _, context ->
            val view = View.inflate(context, R.layout.dialog_question_input, null)

            var ques = question
            var hintStr = ""
            Regex("(.*)\\[(.*)]").find(question)?.apply {
                if (groupValues.size >= 3 && groupValues[1].isNotEmpty()) {
                    ques = groupValues[1]
                    hintStr = groupValues[2]
                }
            }
            view.findViewById<TextView>(R.id.dialog_question_title).apply {
                text = ques
            }
            view.findViewById<AppCompatAutoCompleteTextView>(R.id.dialog_question_input).apply {
                hint = hintStr
                setText(input)
                val adapter = ArrayAdapter(
                    nh.context, android.R.layout.simple_dropdown_item_1line,
                    nh.prefs.getInputPrompts()
                )
                threshold = 1
                setAdapter(adapter)
            }

            view.findViewById<Button>(R.id.input_btn_1).apply {
                setText(R.string.dialog_cancel)
                setOnClickListener {
                    // cancel naming attempt
                    finishLine(27.toChar().toString())
                    nh.dialogs.dismiss(TAG_INPUT)
                }
            }
            view.findViewById<Button>(R.id.input_btn_2).visibility = View.GONE
            view.findViewById<Button>(R.id.input_btn_3).apply {
                setText(R.string.dialog_confirm)
                setOnClickListener {
                    val inputText =
                        view.findViewById<AppCompatAutoCompleteTextView>(R.id.dialog_question_input)
                    // cancel name
                    if (inputText.text.isEmpty()) {
                        finishLine(" ")
                        nh.dialogs.dismiss(TAG_INPUT)
                        return@setOnClickListener
                    }
                    nh.prefs.addInputPrompts(inputText.text.toString())
                    if (inputText.text.length > currentBufSize)
                        finishLine(inputText.text.substring(0, currentBufSize))
                    else
                        finishLine(inputText.text.toString())
                    nh.dialogs.dismiss(TAG_INPUT)
                }
            }

            nh.dialogs.show(TAG_INPUT, view)
        }
    }

    fun answerSelectQuestion(question: String, choices: String, ynNumber:LongArray, def: Char):Char {
        finished = false
        // 弹窗前首先查找是否已存在NHKeyCommand
        nh.command.findAnyCommand<NHKeyCommand>()?.apply {
            return key
        }
        showSelectQuestion(question, choices, ynNumber, def)
        return waitForAnswer()
    }

    private fun showSelectQuestion(question: String, choices: String, ynNumber:LongArray, def: Char) {
        if (choices.isNotEmpty())
            ynQuestion(question, choices, ynNumber, def)
        else
            selectQuestion(question, def)
    }


    private fun selectQuestion(question: String, def: Char) {
        val regex = Regex("(.*)\\[(.*)](.*)")
        regex.find(question)?.apply {
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

            buildDialog(ques, select,null, def)
        }
    }

    private fun ynQuestion(question: String, choices: String, ynNumber:LongArray?, def: Char) {
        val select = choices.toCharArray().filter {
            it!= ' '
        }.map {
            Pair(it, if(it == '#') 1 else -1)
        }.toMutableList()
        buildDialog(question, select, ynNumber, def)
    }

    private fun buildDialog(question: String, choices:MutableList<Pair<Char, Int>>, ynNumber: LongArray?, def: Char) {
        nh.runOnUi { _, context ->
            val view = View.inflate(context, R.layout.dialog_question, null)
            view.findViewById<TextView>(R.id.dialog_question).text = question
            view.findViewById<ScrollView>(R.id.dialog_question_scroll).scrollTo(0, 0)
            view.findViewById<MaterialButton>(R.id.dialog_question_cancel).apply {
                setOnClickListener {
                    // 取消时使用用默认答案
                    finishAnswer(def, -1)
                    nh.dialogs.dismiss(TAG_QUESTION)
                }
            }
            view.findViewById<NHAnswerGrid>(R.id.dialog_question_answer).apply {
                onPick = { _, answer ->
                    when {
                        answer.first == '#' -> {
                            ynNumber?.set(0, answer.second.toLong())
                            finishAnswer('#', -1)
                        }
                        else -> finishAnswer(answer.first, answer.second)
                    }
                    nh.dialogs.dismiss(TAG_QUESTION)
                }
                onLongPress = { index ->
                    showNumberInputDialog(context, choices, index, this)
                }
                submit(choices)
            }
            nh.dialogs.show(TAG_QUESTION, view)
        }
    }

    private fun showNumberInputDialog(context: Context, choices:MutableList<Pair<Char, Int>>,
                                      parentPosition:Int, parentGrid: NHAnswerGrid) {
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
                input.text.toString().toIntOrNull()?.let { count ->
                    choices[parentPosition] = answer.copy(second = count)
                    parentGrid.updateCount(parentPosition, count)
                }
            }
            setNegativeButton(R.string.dialog_cancel){ _, _ ->

            }
            create()
        }
        dialog.setCancelable(false)
        dialog.show(nh.prefs.immersiveMode)

    }

    private fun finishAnswer(answer:Char, count:Int) {
        if (finished) return
        finished = true
        nh.command.sendCommand(NHAnswerCommand(answer, count))
    }

    private fun waitForAnswer():Char {
        val cmd = nh.command.waitForAnyCommand<NHAnswerCommand>()
        return cmd.key
    }

}
