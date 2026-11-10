package com.yywspace.anethack.setting

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.yywspace.anethack.R
import com.yywspace.anethack.databinding.ActivityOptionEditBinding
import com.yywspace.anethack.theme.AppTheme
import java.io.File


class OptionEditActivity: AppCompatActivity() {
    private lateinit var binding:ActivityOptionEditBinding

    /** 导出配置文件内容到用户选的文件 */
    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) exportTo(uri) }

    /** 从用户选的文件导入配置内容 */
    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importFrom(uri) }

    /** 打开时的内容，用来判断有没有未保存的修改 */
    private var initialText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityOptionEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        initStatusBar()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> {
                confirmExit()
                return true
            }
            R.id.option_edit_save -> {
                saveConfirm()
            }
            R.id.option_edit_import -> openDocument.launch(
                arrayOf("text/plain", "application/octet-stream", "*/*")
            )
            R.id.option_edit_export -> createDocument.launch(".nethackrc")
        }
        return super.onOptionsItemSelected(item)
    }

    /** 有未保存的修改时，返回前先问一句 */
    private fun confirmExit() {
        if (binding.optionEditText.text.toString() == initialText) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                saveOptionFile(binding.optionEditText.text.toString())
                finish()
            }
            .setNegativeButton(R.string.unsaved_changes_discard) { _, _ -> finish() }
            .setNeutralButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun saveConfirm() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.option_save_dialog_title)
            setMessage(R.string.option_save_dialog_msg)
            setPositiveButton(R.string.dialog_confirm) { _,_ ->
                saveOptionFile(binding.optionEditText.text.toString())
            }
            setNegativeButton(R.string.dialog_cancel) { _,_ ->

            }
            show()
        }
    }
    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    /** 导出：把当前编辑框里的内容原样写到用户选的文件 */
    private fun exportTo(uri: android.net.Uri) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(binding.optionEditText.text.toString().toByteArray())
            }
        }.onSuccess { toast(R.string.option_exported) }
            .onFailure { toast(R.string.option_export_failed) }
    }

    /** 导入：用文件内容替换编辑框，保留撤销前的内容作为"已保存"基准 */
    private fun importFrom(uri: android.net.Uri) {
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            toast(R.string.option_import_failed)
            return
        }
        binding.optionEditText.setText(text)
        processText(binding.optionEditText.editableText)
        toast(R.string.option_imported)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.option_edit_menu, menu)
        return true
    }

    private fun initStatusBar() = AppTheme.applyNormalSystemBars(this)

    private fun initView() {
        val toolbar = findViewById<Toolbar>(R.id.option_edit_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.optionEditText.apply {
            val text = readOptionText()
            initialText = text
            setText(text)
            processText(editableText)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int,count: Int, after: Int
                ) {
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val text = s?:""
                    var endIdx = text.indexOf('\n', start)
                    val startIdx = text.lastIndexOf('\n', start)
                    // 找不到换行符代表时最后一行
                    if(endIdx == -1) endIdx = text.length - 1
                    val line = text.substring(startIdx + 1, endIdx + 1)
                    if (line.startsWith("#")) {
                        editableText.setSpan(
                            ForegroundColorSpan("#77B767".toColorInt()),
                            startIdx + 1, endIdx + 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE )
                    } else {
                        editableText.setSpan(
                            ForegroundColorSpan(ContextCompat.getColor(this@OptionEditActivity, R.color.app_text)),
                            startIdx + 1, endIdx + 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE )
                    }
                }
                override fun afterTextChanged(s: Editable?) {
                }
            })
        }
    }
    private fun processText(text:Editable) {
        var idx = 0
        var lastIdx = -1
        while (idx < text.length - 1) {
            idx = text.indexOf('\n', idx + 1)
            // 找不到换行符代表时最后一行
            if (idx == -1) idx = text.length - 1
            val line = text.substring(lastIdx + 1, idx + 1)
            if (line.startsWith("#")) {
                text.setSpan(
                    ForegroundColorSpan("#77B767".toColorInt()),
                    lastIdx + 1, idx + 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
            }
            lastIdx = idx
        }
    }

    private fun readOptionText():String {
        return File(filesDir,"nethackdir/.nethackrc").readText()
    }

    private fun saveOptionFile(option:String) {
        File(filesDir,"nethackdir/.nethackrc").writeText(option)
        // 保存后内容就是新的基准，再返回时不会重复提示
        initialText = option
    }
}
