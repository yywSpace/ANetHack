package com.yywspace.anethack

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
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
                    refreshNHKeyboard()
                }, 100)
            }
        }
    }
    private fun initKeyboard() {
        binding.keyboardView.apply {
            onKeyPress = {
                if (nethack.isRunning && it.label.isNotEmpty())
                    processKeyPress(it.value)
            }
            onSpecialKeyLongPress = {
                when(it.label) {
                    "ESC" -> {
                        processKeyPress("Center")
                    }
                    "Meta", "Ctrl" ->
                        processKeyPress("#")
                }
            }
            visibility = View.GONE
        }
    }
    private fun processKeyPress(cmd:String) {
        if (cmd.isEmpty()) return
        when (cmd) {
            "Letter" -> {
                if (!isKeyboardShow)
                    binding.keyboardView.visibility = View.VISIBLE
                else
                    binding.keyboardView.visibility = View.GONE
                isKeyboardShow = !isKeyboardShow
            }
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
            "Center" -> {
                binding.mapView.centerPlayerInScreen()
            }
            "Setting" -> {
                binding.mapView.centerPlayerInScreen()
            }
            else -> {
                if(nethack.isRunning) {
                    if(cmd.startsWith("#")) {
                        nethack.command.sendExtendCommand(NHExtendCommand(cmd))
                        return
                    } else if(cmd.startsWith("L")) {
                        cmd.substring(1).toCharArray().forEach {
                            nethack.command.sendCommand(NHCommand(it))
                        }
                        return
                    } else {
                        nethack.command.sendCommand(NHCommand(cmd.toInt().toChar()))
                    }
                }

            }
        }
    }
    private fun initControlPanel() {
        // Ctrl|^C Meta|^M
        val panelDefault = """
            Setting LS|Save #quit|Quit L20s|20s Li|Bag Letter|abc
        """.trimIndent()
        val panel = nethack.prefs.panel?:panelDefault
        initCustomControlPanel(this, binding.baseCommandPanel, panel)
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

    private fun initCustomControlPanel(context: Context, panelView:LinearLayout, panel:String) {
        panelView.removeAllViews()
        val panelItems = panel.split(" ")
        panelItems.forEachIndexed { i, item ->
            val array = item.split("|")
            val cmd = array[0]
            var label = array[0]
            if(array.size == 2)
                label = array[1]
            val button =
                (LayoutInflater.from(context).inflate(R.layout.panel_cmd_item, null) as TextView)
                    .apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
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