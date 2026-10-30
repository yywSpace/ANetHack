package com.yywspace.anethack

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yywspace.anethack.command.NHCommandParser
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
import com.yywspace.anethack.extensions.show
import com.yywspace.anethack.gamedata.GameData
import com.yywspace.anethack.gamedata.NHMonsterDialog
import com.yywspace.anethack.gamedata.NHPriceIDialog
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
    private lateinit var monsterDialog: NHMonsterDialog
    private var isKeyboardShow = false
    /** 当前明暗模式（uiMode），用来判断配置变化是否来自主题切换 */
    private var uiMode = Configuration.UI_MODE_NIGHT_NO

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
        monsterDialog = NHMonsterDialog(this,nethack)
        nethack.monsterDialog = monsterDialog
        // 后台预热资料（monsters.json 约 400KB），避免第一次弹窗/消息列表卡在主线程
        Thread { GameData.ensureLoaded(applicationContext) }.start()
        initKeyboard()
        initControlPanel()
        initExtraCommands()
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
            // 换主题：applyThemeColors() 里键盘、命令栏、对话框各自刷新
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
        // XML 里的 @color/app_background 是 inflate 时解析的，切换主题后要按新主题再设一次，
        // 否则根布局/容器可能还留着旧（浅色）背景，看起来像"命令栏变白了"
        val background = ContextCompat.getColor(this, R.color.app_background)
        binding.root.setBackgroundColor(background)
        binding.panelContainer.setBackgroundColor(Color.TRANSPARENT)
        binding.dialogContainer.setBackgroundColor(Color.TRANSPARENT)
        // 键盘和命令栏各自按新主题刷新（背景、键帽、按钮、图标都在各自类里处理）
        binding.keyboardView.applyTheme()
        initControlPanel()
        // 额外面板的按钮颜色也是 inflate 时解析的，这里一并重建
        binding.extraCommandView.refreshTheme()
        NHWMenu.clearCache()
        nethack.onThemeChanged()
        // 价格鉴定弹窗的视图是构造时 inflate 的，按新配色重建一份
        priceIDialog = NHPriceIDialog(this, nethack)
        nethack.priceIDialog = priceIDialog
        // 怪物弹窗的视图每次显示时重建，但主题切换后也换一份新的，保持一致
        monsterDialog = NHMonsterDialog(this, nethack)
        nethack.monsterDialog = monsterDialog
    }

    private fun initExtraCommands() {
        binding.extraCommandView.apply {
            onCommand = { processKeyPress(it) }
            immersiveProvider = { nethack.prefs.immersiveMode }
            reload()
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
                    // 长按空格条：等同于长按地图上的角色自己（弹出"你想做什么"菜单）
                    "Space" -> {
                        binding.mapView.longPressPlayer()
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
                // 键盘和"更多命令"面板互斥：显示其中一个就收起另一个
                if (isKeyboardShow)
                    binding.extraCommandView.visibility = View.GONE
            }
            CENTER_COMMAND -> {
                binding.mapView.centerPlayerInScreen()
            }
            SETTING_COMMAND -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            PRICE_ID_COMMAND -> {
                priceIDialog.showFromMessages()
            }
            MONSTER_LOOKUP_COMMAND -> {
                monsterDialog.showForMonster()
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
        binding.bottomCommandPanel.apply {
            immersiveProvider = { nethack.prefs.immersiveMode }
            onCommand = { processKeyPress(it) }
            onCommandsChanged = { nethack.prefs.commandPanel = it }
            onExtraCommands = { toggleExtraCommands() }
            setCommands(nethack.prefs.commandPanel)
            refreshTheme()
        }
    }

    /** 展开/收起"更多命令"面板（样式和键盘一样，占在命令栏上方） */
    private fun toggleExtraCommands() {
        val view = binding.extraCommandView
        if (view.visibility == View.VISIBLE) {
            view.visibility = View.GONE
            return
        }
        // 只在需要时重建（启动、改配置、切主题），这里直接显示，保留滑动位置
        view.visibility = View.VISIBLE
        // 和键盘互斥
        isKeyboardShow = false
        binding.keyboardView.visibility = View.GONE
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
        /** 把地图视角移到角色身上 */
        private const val CENTER_COMMAND = "Center"
        /** 价格鉴定弹窗（从最近消息解析交易信息） */
        private const val PRICE_ID_COMMAND = "PriceID"
        /** 怪物信息弹窗 */
        private const val MONSTER_LOOKUP_COMMAND = "Monster"
    }

}