package com.yywspace.anethack

import android.content.Intent
import android.content.res.ColorStateList
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
    /** 当前明暗模式（uiMode），用来判断配置变化是否来自主题切换 */
    private var uiMode = Configuration.UI_MODE_NIGHT_NO
    /** 固定在命令栏右侧（键盘、设置），不放进可滚动的命令列表 */
    private val pinnedCommands = setOf(KEYBOARD_COMMAND, SETTING_COMMAND)

    override fun onCreate(savedInstanceState: Bundle?) {
        Utils.applyThemeMode(this)
        super.onCreate(savedInstanceState)
        uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
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
        val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newUiMode != uiMode) {
            uiMode = newUiMode
            // 换主题，applyThemeColors() 内部会 refreshTheme() 重建键盘，键盘不需要再单独刷新
            applyThemeColors()
        } else if (isKeyboardShow) {
            // 旋转/键盘弹出等非主题变化：只重放按键标签
            binding.keyboardView.apply {
                postDelayed({
                    refreshNHKeyboard()
                }, 100)
            }
        }
    }

    /**
     * 明暗主题切换（uiMode）不重建 Activity，避免游戏重开：手动刷新受影响的颜色和缓存。
     * 注意：布局里的 @color 是在 inflate 时解析的，本 Activity 不重建就不会自动更新，
     * 所以下面这些"再设一次"是必要的；能整体 inflate 的（按键、命令栏）走重建。
     */
    private fun applyThemeColors() {
        Utils.applyThemeMode(this)
        // setTheme 让之后创建的对话框按新的（night）主题解析
        setTheme(R.style.Theme_ANetHack)
        // 面板背景：view 已存在，drawable 是旧的，只能重新设置
        binding.keyboardView.setBackgroundColor(getColor(R.color.app_background))
        binding.baseCommandPanel.setBackgroundColor(getColor(R.color.app_background))
        // 图标 tint 同理，inflate 时解析，切换后重新设置
        val iconTint = ColorStateList.valueOf(getColor(R.color.app_text))
        binding.keyboardSwitch.imageTintList = iconTint
        binding.settingsSwitch.imageTintList = iconTint
        // 命令栏条目重新 inflate（文字色来自 item_command.xml）
        initControlPanel()
        // 按键背景来自 drawable，需要重建按键视图才能换配色
        binding.keyboardView.postDelayed({
            binding.keyboardView.refreshTheme()
        }, 100)
        NHWMenu.clearCache()
        nethack.onThemeChanged()
        // 价格鉴定弹窗的视图是构造时 inflate 的，按新配色重建一份
        priceIDialog = NHPriceIDialog(this, nethack)
        nethack.priceIDialog = priceIDialog
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