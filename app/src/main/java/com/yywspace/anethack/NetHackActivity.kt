package com.yywspace.anethack

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yywspace.anethack.command.NHCommandParser
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
import com.yywspace.anethack.identify.NHPriceIDialog
import com.yywspace.anethack.setting.SettingsActivity
import com.yywspace.anethack.window.NHWMenu
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


class NetHackActivity : AppCompatActivity() {
    private lateinit var nethack:NetHack
    private lateinit var handler:Handler
    private lateinit var binding: ActivityNethackBinding
    private lateinit var priceIDialog: NHPriceIDialog
    private var isKeyboardShow = false
    /** 固定在命令栏右侧（键盘、设置），不放进可滚动的命令列表 */
    private val pinnedCommands = setOf(KEYBOARD_COMMAND, SETTING_COMMAND)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNethackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler = Handler(Looper.getMainLooper())
        nethack = NetHack(handler, this, binding,"${filesDir.path}/nethackdir")
        priceIDialog = NHPriceIDialog(this, nethack)
        nethack.priceIDialog = priceIDialog
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

    override fun onDestroy() {
        // 静态缓存持有 Activity context，Activity 重建时清空兜底
        NHWMenu.clearCache()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (nethack.prefs.immersiveMode)
            hideSystemUi()
        else
            showSystemUi()
        binding.keyboardView.setKeyboardVibrate(nethack.prefs.keyboardVibrate)
        initControlPanel()
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
                    // 长按空格条：把角色居中到屏幕
                    "Space" -> {
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
            KEYBOARD_COMMAND -> {
                isKeyboardShow = !isKeyboardShow
                binding.keyboardView.visibility =
                    if (isKeyboardShow) View.VISIBLE else View.GONE
            }
            "Center" -> {
                binding.mapView.centerPlayerInScreen()
            }
            SETTING_COMMAND -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            "PriceID" -> {
                priceIDialog.showFromMessages()
            }
            else -> {
                if(nethack.isRunning) {
                    NHCommandParser.parseNHCommand(cmd).forEach {
                        if (it is NHExtendCommand)
                            nethack.command.sendCommand(NHKeyCommand('#'))
                        nethack.command.sendCommand(it)
                    }
                }
            }
        }
    }

    private fun initControlPanel() {
        var commands = nethack.prefs.commandPanel.orEmpty()
        if (commands.isEmpty())
            commands = getString(R.string.pref_keyboard_command_panel_default)
        binding.commandList.removeAllViews()
        commands.split(" ")
            .filter { it.isNotBlank() && it.substringBefore('|') !in pinnedCommands }
            .forEach { cmdStr ->
                val cmd = cmdStr.substringBefore('|')
                val item = layoutInflater
                    .inflate(R.layout.item_command, binding.commandList, false) as TextView
                item.text = cmdStr.substringAfter('|', cmd)
                item.setOnClickListener { processKeyPress(cmd) }
                binding.commandList.addView(item)
            }
        // 键盘和设置固定在右侧，不跟着列表滚动
        binding.keyboardSwitch.setOnClickListener { processKeyPress(KEYBOARD_COMMAND) }
        binding.settingsSwitch.setOnClickListener { processKeyPress(SETTING_COMMAND) }
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
                dumpDir.listFiles { it.extension == "log" }
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

    companion object {
        /** 固定在命令栏右侧、不参与横向滚动的两个命令 */
        private const val KEYBOARD_COMMAND = "Keyboard"
        private const val SETTING_COMMAND = "Setting"
    }

}