package com.yywspace.anethack.window

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHAnswerCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.command.NHLineCommand
import com.yywspace.anethack.extensions.show


class NHQuestion(val nh: NetHack) {
    // 防连点：一次输入/回答只发一条命令，避免残留命令被下一次同类型窗口消费。
    // 每次 answerInputQuestion / answerSelectQuestion 开始时重置。
    private var finished = false
    // 对话框/视图缓存：复用避免每次 inflate + create 的开销（与菜单对话框同一模式）
    private var inputView: View? = null
    private var inputDialog: AlertDialog? = null
    private var questionView: View? = null
    private var questionDialog: AlertDialog? = null
    private var currentBufSize = 0

    init {
        // NHQuestion 随 NetHack 在 Activity onCreate（主线程）时创建，
        // 提前建好两个对话框，第一次打开不再付 inflate + create 的开销
        ensureInputDialog(nh.context)
        ensureQuestionDialog(nh.context)
        // 预演：show 一次立即 dismiss（同一消息内不会渲染出来，无闪烁），
        // 把第一次 show() 的窗口初始化（~100ms+）挪到启动阶段
        nh.handler.post {
            inputDialog?.apply { show(); dismiss() }
            questionDialog?.apply { show(); dismiss() }
        }
    }

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

    /** 首次创建输入对话框（inflate + create + 一次性按钮配置）；构造时和运行时兜底调用 */
    private fun ensureInputDialog(context: Context) {
        if (inputDialog != null) return
        val view = View.inflate(context, R.layout.dialog_question_input, null)
        inputView = view
        inputDialog = AlertDialog.Builder(context).apply {
            setView(inputView)
            setCancelable(false)
        }.create()
        // 按钮回调一次性配置（引用缓存的对话框；bufSize 用字段，每次打开更新）
        view.apply {
            findViewById<Button>(R.id.input_btn_1).apply {
                setText(R.string.dialog_cancel)
                setOnClickListener {
                    // cancel naming attempt
                    finishLine(27.toChar().toString())
                    inputDialog?.dismiss()
                }
            }
            findViewById<Button>(R.id.input_btn_2).visibility = View.GONE
            findViewById<Button>(R.id.input_btn_3).apply {
                setText(R.string.dialog_confirm)
                setOnClickListener {
                    // 注意：此处 this 是按钮，findViewById 必须显式用 view（输入框在 view 上）
                    val inputText =
                        view.findViewById<AppCompatAutoCompleteTextView>(R.id.dialog_question_input)
                    // cancel name
                    if (inputText.text.isEmpty()) {
                        finishLine(" ")
                        inputDialog?.dismiss()
                        return@setOnClickListener
                    }
                    nh.prefs.addInputPrompts(inputText.text.toString())
                    if (inputText.text.length > currentBufSize)
                        finishLine(inputText.text.substring(0, currentBufSize))
                    else
                        finishLine(inputText.text.toString())
                    inputDialog?.dismiss()
                }
            }
        }
    }

    private fun showInputQuestion(question: String, input:String, bufSize: Int) {
        currentBufSize = bufSize
        nh.runOnUi { _, context ->
            if (inputDialog == null) {
                ensureInputDialog(context)
            }
            // 每次打开刷新内容（复用视图必须重置输入文本/hint/提示词）
            inputView?.apply {
                var ques = question
                var hintStr = ""
                Regex("(.*)\\[(.*)]").find(question)?.apply {
                    if (groupValues.size >= 3 && groupValues[1].isNotEmpty()) {
                        ques = groupValues[1]
                        hintStr = groupValues[2]
                    }
                }
                findViewById<TextView>(R.id.dialog_question_title).apply {
                    text = ques
                }
                findViewById<AppCompatAutoCompleteTextView>(R.id.dialog_question_input).apply {
                    hint = hintStr
                    setText(input)
                    val adapter = ArrayAdapter(
                        nh.context, android.R.layout.simple_dropdown_item_1line,
                        nh.prefs.getInputPrompts()
                    )
                    threshold = 1
                    setAdapter(adapter)
                }
            }
            inputDialog?.show(nh.prefs.immersiveMode)
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

    /** 首次创建问题对话框（inflate + create + RecyclerView 配置）；构造时和运行时兜底调用 */
    private fun ensureQuestionDialog(context: Context) {
        if (questionDialog != null) return
        questionView = View.inflate(context, R.layout.dialog_question, null)
        questionDialog = AlertDialog.Builder(context).apply {
            setView(questionView)
            setCancelable(false)
        }.create()
        questionView?.findViewById<RecyclerView>(R.id.dialog_question_answer)?.layoutManager =
            GridLayoutManager(context, 3).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return 1
                    }
                }
            }
    }

    private fun buildDialog(question: String, choices:MutableList<Pair<Char, Int>>, ynNumber: LongArray?, def: Char) {
        nh.runOnUi { _, context ->
            if (questionDialog == null) {
                ensureQuestionDialog(context)
            }
            // 每次打开刷新内容（问题文本 + 选项 adapter，choices 每次不同）
            questionView?.apply {
                findViewById<TextView>(R.id.dialog_question).text = question
                findViewById<RecyclerView>(R.id.dialog_question_answer).apply {
                    val colCount = if (choices.size < 3) choices.size else 3
                    (layoutManager as? GridLayoutManager)?.spanCount = colCount
                    adapter = NHQuestionAnswerAdapter(choices).apply {
                        onItemClick = { _, _, answer ->
                            if (answer.first.code == 27)
                                finishAnswer(def, -1)
                            else if (answer.first == '#') {
                                ynNumber?.set(0, answer.second.toLong())
                                finishAnswer('#', -1)
                            } else
                                finishAnswer(answer.first, answer.second)
                            questionDialog?.dismiss()
                        }
                        onItemLongClick = { _, index, _ ->
                            showNumberInputDialog(context, choices, index, this)
                        }
                    }
                }
            }
            questionDialog?.show(nh.prefs.immersiveMode)
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

    private class NHQuestionAnswerAdapter(val answers:List<Pair<Char,Int>>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var onItemClick:((view:View, index:Int, item:Pair<Char,Int>)->Unit)? = null
        var onItemLongClick:((view:View, index:Int, item:Pair<Char,Int>)->Unit)? = null

        class ButtonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val button : Button = view.findViewById(R.id.item_answer_btn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dialog_question_answer_item, parent, false)
            return ButtonViewHolder(view)
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