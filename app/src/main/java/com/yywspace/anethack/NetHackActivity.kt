package com.yywspace.anethack

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
import com.yywspace.anethack.keybord.NHKeyboard
import com.yywspace.anethack.keybord.NHKeyboardUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class NetHackActivity : AppCompatActivity() {
    private val TAG = NetHackActivity::class.java.name
    private lateinit var nethack:NetHack
    private lateinit var handler:Handler
    private lateinit var binding: ActivityNethackBinding
    private var isKeyboardShow = false
    private var isUpper = false
    private var keyboardType:NHKeyboard.Type = NHKeyboard.Type.NONE
    private lateinit var keyboardLetter:NHKeyboard
    private lateinit var keyboardSymbol:NHKeyboard
    private lateinit var keyboardCtrl:NHKeyboard
    private lateinit var keyboardMeta:NHKeyboard
    private lateinit var keyboardCustom:NHKeyboard
    private lateinit var keyboardLetterUpper:NHKeyboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNethackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler = Handler(Looper.getMainLooper())
        nethack = NetHack(handler, this, binding,"${filesDir.path}/nethackdir")
        initKeyboard()
        initControlPanel()
        hideSystemUi()
        // showSystemUi()
        copyFilesFromAssets("nethackdir")
        nethack.run()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if(isKeyboardShow) {
            binding.keyboardView.apply {
                postDelayed({
                    when(keyboardType) {
                        NHKeyboard.Type.UPPER_LETTER ->
                            binding.keyboardView.setNHKeyboard(keyboardLetterUpper)
                        NHKeyboard.Type.LETTER ->
                            binding.keyboardView.setNHKeyboard(keyboardLetter)
                        NHKeyboard.Type.SYMBOL ->
                            binding.keyboardView.setNHKeyboard(keyboardSymbol)
                        NHKeyboard.Type.META ->
                            binding.keyboardView.setNHKeyboard(keyboardMeta)
                        NHKeyboard.Type.CTRL ->
                            binding.keyboardView.setNHKeyboard(keyboardCtrl)
                        NHKeyboard.Type.CUSTOM ->
                            binding.keyboardView.setNHKeyboard(keyboardCustom)
                        NHKeyboard.Type.NONE ->
                            binding.keyboardView.visibility = View.GONE
                    }
                }, 100)
            }

        }
    }

    private fun initKeyboard() {
        binding.keyboardView.apply {
            onKeyPress = {
                if(nethack.isRunning)
                    processKeyPress(it.value)
            }
            visibility = View.GONE
        }
        val keyboardLetterJson = Utils.readAssetsFile(
            this, "keyboard/keyboard_letter.json")
        keyboardLetter = NHKeyboardUtils.readFromJson(keyboardLetterJson)
        val keyboardLetterUpperJson = Utils.readAssetsFile(
            this, "keyboard/keyboard_letter_upper.json")
        keyboardLetterUpper = NHKeyboardUtils.readFromJson(keyboardLetterUpperJson)
        val keyboardSymbolJson = Utils.readAssetsFile(
            this, "keyboard/keyboard_symbol.json")
        keyboardSymbol = NHKeyboardUtils.readFromJson(keyboardSymbolJson)
        val keyboardCtrlJson = Utils.readAssetsFile(
            this, "keyboard/keyboard_ctrl.json")
        keyboardCtrl = NHKeyboardUtils.readFromJson(keyboardCtrlJson)
        val keyboardMetaJson = Utils.readAssetsFile(
            this, "keyboard/keyboard_meta.json")
        keyboardMeta = NHKeyboardUtils.readFromJson(keyboardMetaJson)
        val keyboardCustomJson = Utils.readAssetsFile(
            this, "keyboard/keyboard_custom.json")
        keyboardCustom = NHKeyboardUtils.readFromJson(keyboardCustomJson)
    }

    private fun processKeyPress(cmd:String) {
        when (cmd) {
            "Repeat" -> {
                Thread {
                    for (i in 0..100) {
                        for (j in 0..5) {
                            nethack.command.sendCommand(NHCommand('h'))
                        }
                        for (j in 0..5) {
                            nethack.command.sendCommand(NHCommand('l'))
                        }
                    }
                }.start()
            }
            "Shift" -> {
                if(isUpper) {
                    keyboardType = NHKeyboard.Type.LETTER
                    binding.keyboardView.setNHKeyboard(keyboardLetter)
                }
                else {
                    keyboardType = NHKeyboard.Type.UPPER_LETTER
                    binding.keyboardView.setNHKeyboard(keyboardLetterUpper)
                }
                isUpper = !isUpper
            }
            "Letter" -> {
                    if(isKeyboardShow) {
                        if(keyboardType != NHKeyboard.Type.LETTER
                            && keyboardType != NHKeyboard.Type.UPPER_LETTER) {
                            if(isUpper)
                                binding.keyboardView.setNHKeyboard(keyboardLetterUpper)
                            else
                                binding.keyboardView.setNHKeyboard(keyboardLetter)
                        }
                        else {
                            binding.keyboardView.visibility = View.GONE
                            isKeyboardShow = false
                        }
                    }
                    else {
                        binding.keyboardView.visibility = View.VISIBLE
                        if(isUpper)
                            binding.keyboardView.setNHKeyboard(keyboardLetterUpper)
                        else
                            binding.keyboardView.setNHKeyboard(keyboardLetter)
                        isKeyboardShow = true
                    }
                keyboardType = if(isUpper)
                    NHKeyboard.Type.UPPER_LETTER
                else
                    NHKeyboard.Type.LETTER
            }
            "Symbol"->{
                if(isKeyboardShow) {
                    if(keyboardType != NHKeyboard.Type.SYMBOL)
                        binding.keyboardView.setNHKeyboard(keyboardSymbol)
                    else {
                        binding.keyboardView.visibility = View.GONE
                        isKeyboardShow = !isKeyboardShow
                    }
                }
                else {
                    binding.keyboardView.visibility = View.VISIBLE
                    binding.keyboardView.setNHKeyboard(keyboardSymbol)
                    isKeyboardShow = !isKeyboardShow
                }
                keyboardType = NHKeyboard.Type.SYMBOL
            }
            "Ctrl" -> {
                if(isKeyboardShow) {
                    if(keyboardType != NHKeyboard.Type.CTRL)
                        binding.keyboardView.setNHKeyboard(keyboardCtrl)
                    else {
                        binding.keyboardView.visibility = View.GONE
                        isKeyboardShow = !isKeyboardShow
                    }
                }
                else {
                    binding.keyboardView.visibility = View.VISIBLE
                    binding.keyboardView.setNHKeyboard(keyboardCtrl)
                    isKeyboardShow = !isKeyboardShow
                }
                keyboardType = NHKeyboard.Type.CTRL
            }
            "Meta" -> {
                if(isKeyboardShow) {
                    if(keyboardType != NHKeyboard.Type.META)
                        binding.keyboardView.setNHKeyboard(keyboardMeta)
                    else {
                        binding.keyboardView.visibility = View.GONE
                        isKeyboardShow = !isKeyboardShow
                    }
                }
                else {
                    binding.keyboardView.visibility = View.VISIBLE
                    binding.keyboardView.setNHKeyboard(keyboardMeta)
                    isKeyboardShow = !isKeyboardShow
                }
                keyboardType = NHKeyboard.Type.META
            }
            "Center" -> {
                binding.mapView.centerPlayerInScreen()
            }
            "Custom" -> {
                if(isKeyboardShow) {
                    if(keyboardType != NHKeyboard.Type.CUSTOM)
                        binding.keyboardView.setNHKeyboard(keyboardCustom)
                    else {
                        binding.keyboardView.visibility = View.GONE
                        isKeyboardShow = !isKeyboardShow
                    }
                }
                else {
                    binding.keyboardView.visibility = View.VISIBLE
                    binding.keyboardView.setNHKeyboard(keyboardCustom)
                    isKeyboardShow = !isKeyboardShow
                }
                keyboardType = NHKeyboard.Type.CUSTOM
            }
            else -> {
                if(nethack.isRunning) {
                    if(cmd.startsWith("#")) {
                        nethack.command.sendExtendCommand(NHExtendCommand(cmd))
                        return
                    }
                    if(cmd.startsWith("L")) {
                        cmd.substring(1).toCharArray()
                            .forEach {
                            nethack.command.sendCommand(NHCommand(it))
                        }
                        return
                    }
                    nethack.command.sendCommand(NHCommand(cmd.toInt().toChar()))
                }

            }
        }
    }
    private fun initControlPanel() {
        // Ctrl|^C Meta|^M
        val panelDefault = """
            Custom #|Extend LS|Save #quit|Quit L20s|20s Symbol|1?@ Letter|abc
        """.trimIndent()
        val panel = nethack.prefs.panel?:panelDefault
        initCustomControlPanel(this, binding.basePanel, panel)
    }

    private fun hideSystemUi() {
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemUi() {
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun initCustomControlPanel(context: Context,panelView:LinearLayout, panel:String) {
        panelView.removeAllViews()
        val panelItems = panel.split(" ")
        panelItems.forEachIndexed { i, item ->
            val array = item.split("|")
            val cmd = array[0]
            var label = array[0]
            if(array.size == 2)
                label = array[1]
            val button = TextView(context).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(
                     0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    leftMargin = 10
                    if(i == panelItems.size -1)
                        rightMargin = 10
                }
                setPadding(0,15,0,15)
                gravity = Gravity.CENTER
                // typeface = Typeface.defaultFromStyle(Typeface.BOLD);
                setBackgroundResource(R.drawable.btn_bg_selector)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setOnClickListener {
                    processKeyPress(cmd)
                }
            }
            panelView.addView(button)
        }
    }

    private fun copyFilesFromAssets(assetsDir:String) {
        if(File(filesDir, assetsDir).exists())
            return
        Log.d(TAG, "localHackDir: $filesDir")
        val assetsQueue = ArrayDeque<String>()
        val assetsList = mutableListOf<String>()
        assetsQueue.add(assetsDir)
        while (assetsQueue.isNotEmpty()) {
            val basePath = assetsQueue.removeFirst()
            val fileList = assets.list(basePath)
            if (fileList?.isEmpty() == true)
                assetsList.add(basePath)
            fileList?.forEach { subDir-> assetsQueue.add("$basePath/$subDir") }
        }
        assetsList.forEach {
                path->
            try {
                val source: InputStream = assets.open(File(path).path)
                val localFile = File(filesDir, path)
                if (!localFile.exists()) {
                    localFile.parentFile?.mkdirs()
                    val destination: OutputStream = FileOutputStream(localFile)
                    val buffer = ByteArray(1024)
                    var nread: Int
                    while (source.read(buffer).also { nread = it } != -1) {
                        if (nread == 0) {
                            nread = source.read()
                            if (nread < 0) break
                            destination.write(nread)
                            continue
                        }
                        destination.write(buffer, 0, nread)
                    }
                    destination.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

}