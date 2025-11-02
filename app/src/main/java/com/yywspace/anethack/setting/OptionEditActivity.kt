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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.yywspace.anethack.R
import com.yywspace.anethack.databinding.ActivityOptionEditBinding
import java.io.File
import androidx.core.graphics.toColorInt


class OptionEditActivity: AppCompatActivity() {
    private lateinit var binding:ActivityOptionEditBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOptionEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        initStatusBar()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.option_edit_save -> {
                saveConfirm()
            }
        }
        return super.onOptionsItemSelected(item)
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
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.option_edit_menu, menu)
        return true
    }

    private fun initStatusBar() {
        window.statusBarColor = Color.TRANSPARENT
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun initView() {
        val toolbar = findViewById<Toolbar>(R.id.option_edit_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.optionEditText.apply {
            setText(readOptionText())
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
                        editableText.setSpan(ForegroundColorSpan(Color.BLACK),
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
    }
}