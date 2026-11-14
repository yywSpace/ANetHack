package com.yywspace.anethack

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yywspace.anethack.command.NHCommandParser
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
import com.yywspace.anethack.gamedata.GameData
import com.yywspace.anethack.gamedata.NHMonsterDialog
import com.yywspace.anethack.gamedata.NHItemDialog
import com.yywspace.anethack.gamedata.NHPriceIDialog
import com.yywspace.anethack.keybord.KeyboardVibrate
import com.yywspace.anethack.log.AppLog
import com.yywspace.anethack.setting.SettingsActivity
import com.yywspace.anethack.theme.AppTheme


class NetHackActivity : AppCompatActivity() {

    private lateinit var nethack:NetHack
    private lateinit var handler:Handler
    private lateinit var binding: ActivityNethackBinding
    private lateinit var priceIDialog: NHPriceIDialog
    private lateinit var monsterDialog: NHMonsterDialog
    private lateinit var itemDialog: NHItemDialog
    private var isKeyboardShow = false
    /** 当前明暗模式（uiMode），用来判断配置变化是否来自主题切换 */
    private var uiMode = Configuration.UI_MODE_NIGHT_NO

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyTheme(this)
        super.onCreate(savedInstanceState)
        uiMode = if (AppTheme.isDark(this)) Configuration.UI_MODE_NIGHT_YES
                else Configuration.UI_MODE_NIGHT_NO
        binding = ActivityNethackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler = Handler(Looper.getMainLooper())
        nethack = NetHack(handler, this, binding,"${filesDir.path}/nethackdir")
        AppTheme.applyGameBars(this, nethack.prefs.immersiveMode)
        priceIDialog = NHPriceIDialog(this, nethack)
        nethack.priceIDialog = priceIDialog
        monsterDialog = NHMonsterDialog(this,nethack)
        nethack.monsterDialog = monsterDialog
        itemDialog = NHItemDialog(this, nethack)
        // 后台预热资料（monsters.json 约 400KB），避免第一次弹窗/消息列表卡在主线程
        Thread { GameData.ensureLoaded(applicationContext) }.start()
        initKeyboard()
        initControlPanel()
        initMapSafeArea()
        AssetStartupController(
            this,
            binding.assetLoadingStub
        ) {
            nethack.run()
        }.start()
    }

    /** 地图固定铺满，顶部/底部覆盖层的高度决定镜头和指示符的安全区 */
    private fun initMapSafeArea() {
        binding.statusMessageBar.addOnLayoutChangeListener {
            _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop)
                updateMapSafeArea()
        }
        binding.bottomControls.addOnLayoutChangeListener {
            _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop)
                updateMapSafeArea()
        }
    }

    private fun updateMapSafeArea() {
        binding.mapView.setSafeInsets(
            0,
            binding.statusMessageBar.height,
            0,
            binding.bottomControls.height
        )
    }

    override fun onDestroy() {
        nethack.dialogs.clear()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        AppTheme.applyGameBars(this, nethack.prefs.immersiveMode)
        KeyboardVibrate.bind(this)
        KeyboardVibrate.enabled = nethack.prefs.keyboardVibrate
        // 设置里可能刚改过自定义键盘布局
        binding.keyboardView.reloadCustomLayout()
        initControlPanel()
        // 从设置返回：贴图/视口可能已变化，主动重绘一次
        binding.mapView.requestRedraw()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newUiMode != uiMode) {
            uiMode = newUiMode
            AppLog.d("NetHackActivity", "theme changed: $newUiMode")
            // 换主题：applyThemeColors() 里键盘、命令栏、对话框各自刷新
            applyThemeColors()
        } else if (isKeyboardShow) {
            // 旋转/键盘弹出等非主题变化：只重放按键标签
            binding.keyboardView.apply {
                postDelayed({
                    refreshKeyboardLayout()
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
        AppTheme.applyTheme(this)
        // setTheme 让之后创建的对话框按新的（night）主题解析
        setTheme(R.style.Theme_ANetHack)
        // XML 里的 @color/app_background 是 inflate 时解析的，切换主题后要按新主题再设一次，
        // 否则根布局/容器可能还留着旧（浅色）背景，看起来像"命令栏变白了"
        val background = ContextCompat.getColor(this, R.color.app_background)
        binding.root.setBackgroundColor(background)
        // 键盘和命令栏各自按新主题刷新（背景、键帽、按钮、图标都在各自类里处理）
        binding.keyboardView.applyTheme()
        initControlPanel()
        nethack.onThemeChanged()
        // 价格鉴定弹窗的视图是构造时 inflate 的，按新配色重建一份
        priceIDialog = NHPriceIDialog(this, nethack)
        nethack.priceIDialog = priceIDialog
        // 怪物弹窗的视图每次显示时重建，但主题切换后也换一份新的，保持一致
        monsterDialog = NHMonsterDialog(this, nethack)
        nethack.monsterDialog = monsterDialog
        itemDialog = NHItemDialog(this, nethack)
    }

    private fun initKeyboard() {
        binding.keyboardView.apply {
            onKeyPress = {
                if (nethack.isRunning && it.label.isNotEmpty())
                    processKeyPress(it.command)
            }
            onSpecialKeyLongPress = {
                when(it.label) {
                    // 长按空格：发送 ESC，当作"取消"
                    "Space" -> processKeyPress("27")
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
            KEYBOARD_COMMAND -> cycleCommandPanel()
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
                monsterDialog.showMonsterList()
            }
            ITEM_LOOKUP_COMMAND -> {
                itemDialog.showItems()
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

    /** 两条命令面板一起初始化：回调一致，刷新入口也一致 */
    private fun initControlPanel() {
        binding.bottomCommandBar.apply {
            onCommand = { processKeyPress(it) }
            immersiveProvider = { nethack.prefs.immersiveMode }
            refreshTheme()
        }
        binding.extraCommandPanel.apply {
            onCommand = { processKeyPress(it) }
            immersiveProvider = { nethack.prefs.immersiveMode }
            refreshTheme()
        }
        binding.keyboardView.immersiveProvider = { nethack.prefs.immersiveMode }
    }

    /** 底部命令栏上的面板状态："更多命令"面板是否由键盘切换而来 */
    private var extraPanelVisible = false

    /**
     * 面板按钮：一个按钮在键盘与扩展命令面板之间切换。
     *
     * 图标跟着"当前显示的是哪个面板"走：
     * - 什么都没展开 / 键盘展开 → 显示键盘图标；
     * - 扩展面板展开 → 显示扩展面板图标。
     */
    private fun cycleCommandPanel() {
        when {
            isKeyboardShow -> {
                // 键盘 → 扩展面板
                isKeyboardShow = false
                binding.keyboardView.visibility = View.GONE
                binding.extraCommandPanel.visibility = View.VISIBLE
                extraPanelVisible = true
            }
            extraPanelVisible -> {
                // 扩展面板 → 键盘
                extraPanelVisible = false
                binding.extraCommandPanel.visibility = View.GONE
                isKeyboardShow = true
                binding.keyboardView.visibility = View.VISIBLE
            }
            else -> {
                // 都收起 → 先打开键盘
                isKeyboardShow = true
                binding.keyboardView.visibility = View.VISIBLE
            }
        }
        binding.bottomCommandBar.setSwitchIcon(isKeyboardIcon = !extraPanelVisible)
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
        /** 物品资料弹窗 */
        private const val ITEM_LOOKUP_COMMAND = "Item"
    }

}
