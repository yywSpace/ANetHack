package com.yywspace.anethack

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.yywspace.anethack.identify.NHPriceIDialog
import com.yywspace.anethack.setting.SettingsActivity
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream


class NetHackActivity : AppCompatActivity() {
    private lateinit var nethack:NetHack
    private lateinit var handler:Handler
    private lateinit var binding: ActivityNethackBinding
    private lateinit var priceIDialog: NHPriceIDialog
    private var isKeyboardShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNethackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler = Handler(Looper.getMainLooper())
        nethack = NetHack(handler, this, binding,"${filesDir.path}/nethackdir")
        priceIDialog = NHPriceIDialog(this, nethack)
        initView()
        initKeyboard()
        initControlPanel()
        AssetsLoader(this).loadAssets(
            listOf("nethackdir", "logs", "conf"), false
        ) { overwrite ->
            processLogs()
            processConf(overwrite)
            nethack.run()
        }
    }

    override fun onResume() {
        super.onResume()
        if (nethack.prefs.immersiveMode)
            hideSystemUi()
        else
            showSystemUi()
        if (nethack.prefs.priceId)
            binding.floatingButton.visibility = View.VISIBLE
        else
            binding.floatingButton.visibility = View.GONE
        binding.keyboardView.setKeyboardVibrate(nethack.prefs.keyboardVibrate)
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

    private fun initView() {
        if (nethack.prefs.priceId)
            binding.floatingButton.visibility = View.VISIBLE
        else
            binding.floatingButton.visibility = View.GONE
        binding.floatingButton.setOnClickListener {
            priceIDialog.show()
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
                startActivity(Intent(this, SettingsActivity::class.java))
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
            Setting LS|Save #quit|Quit L20s|20s Letter|Abc
        """.trimIndent()
        initCustomControlPanel(this, binding.baseCommandPanel, panelDefault)
//        BottomSheetBehavior.from(binding.baseCommandPanel).apply {
//            isHideable = false
//        }
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
        window.statusBarColor = Color.BLACK
        windowInsetsController.isAppearanceLightStatusBars = false
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
                (View.inflate(context, R.layout.panel_cmd_item, null) as TextView)
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
    private fun processLogs() {
        val dumpLogMaxSize = nethack.prefs.dumpLogMaxSize
        object : Thread() {
            override fun run() {
                // save error log
                val logcatProcess = Runtime.getRuntime().exec("logcat -d *:W")
                val errorLog = File(filesDir,"logs/error/nethack.log")
                FileOutputStream(errorLog).use { fileOut ->
                    logcatProcess.inputStream.copyTo(fileOut)
                }
                // delete dump logs that exceed the size
                val dumpDir = File(filesDir,"logs/dump")
                dumpDir.listFiles(FileFilter { it.extension == "log" })
                    ?.apply {
                    if (size > dumpLogMaxSize) {
                        toList()
                            .sortedBy { it.name.split(".")[0] }
                            .stream().limit((size - dumpLogMaxSize).toLong()).forEach {
                                it.delete()
                            }
                    }
                }
            }
        }.start()
    }

    private fun processConf(overwrite:Boolean) {
        val sysconf = File(filesDir,"conf/sysconf")
        val userConf = File(filesDir,"conf/nethackrc")
        val sysconfTarget = File(filesDir,"nethackdir/sysconf")
        val userConfTarget = File(filesDir,"nethackdir/.nethackrc")
        if (!sysconfTarget.exists() || overwrite)
            FileOutputStream(sysconfTarget).use { fileOut ->
                FileInputStream(sysconf).copyTo(fileOut)
            }
        if (!userConfTarget.exists() || overwrite)
            FileOutputStream(userConfTarget).use { fileOut ->
                FileInputStream(userConf).copyTo(fileOut)
            }
    }
}