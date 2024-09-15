package com.yywspace.anethack.setting

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.toSpannable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.yywspace.anethack.databinding.ActivityOptionEditBinding
import java.io.File


class OptionEditActivity: AppCompatActivity() {
    private lateinit var binding:ActivityOptionEditBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOptionEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        initStatusBar()
    }

    private fun initStatusBar() {
        window.statusBarColor = Color.TRANSPARENT
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun initView() {
        binding.optionEditText.apply {
            setText(processText(readOptionFile()))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int,count: Int, after: Int
                ) {
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val lines = s.toString().split("\n")
                    var cnt = 0
                    for (line in lines) {
                        cnt += line.length + 1
                        // Log.d("onTextChanged", "line:${line}\n line.length:${line.length} cnt:$cnt")
                        if (cnt > start + count) {
                            // Log.d("onTextChanged", "line:${line}\nstart:$start count:$count before:$before")
                            if (line.startsWith("#")) {
                                editableText.setSpan(
                                    ForegroundColorSpan(Color.parseColor("#77B767")),
                                    cnt - line.length-1, cnt, Spanned.SPAN_INCLUSIVE_EXCLUSIVE )
                            } else {
                                editableText.setSpan(ForegroundColorSpan(Color.BLACK),
                                    cnt - line.length-1, cnt, Spanned.SPAN_INCLUSIVE_EXCLUSIVE )
                            }
                            return
                        }
                    }
                }
                override fun afterTextChanged(s: Editable?) {
                }
            })
        }

        binding.optionEditCancel.setOnClickListener {
            finish()
        }
        binding.optionEditSave.setOnClickListener {
            saveOptionFile(binding.optionEditText.text.toString())
            finish()
        }
    }
    private fun processText(lines:List<String>):Spannable {
        val spBuilder = SpannableStringBuilder()
        for (line in lines) {
            if (line.startsWith("#")) {
                SpannableString(line).apply {
                    setSpan(
                        ForegroundColorSpan(Color.parseColor("#77B767")),
                        0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                    spBuilder.append(this)
                }
            }else {
                spBuilder.append(line)
            }
            spBuilder.append("\n")
        }
        return spBuilder.toSpannable()
    }
    private fun readOptionFile():List<String> {
        return File(filesDir,"nethackdir/sysconf").readLines()
    }

    private fun saveOptionFile(option:String) {
        File(filesDir,"nethackdir/sysconf").writeText(option)
    }
}