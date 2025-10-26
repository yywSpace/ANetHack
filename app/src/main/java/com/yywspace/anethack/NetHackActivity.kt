package com.yywspace.anethack

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHCommandParser
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
import com.yywspace.anethack.identify.NHPriceIDialog
import com.yywspace.anethack.setting.SettingsActivity
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Integer.parseInt


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

    private fun hideKeyboard() {
        //向下位移隐藏动画  从自身位置的最上端向下滑动了自身的高度
        TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f
        ).apply {
            repeatMode = Animation.REVERSE
            duration = 200
            setAnimationListener(object :AnimationListener{
                override fun onAnimationStart(animation: Animation?) {
                }
                override fun onAnimationEnd(animation: Animation?) {
                    binding.keyboardView.visibility = View.GONE
                }
                override fun onAnimationRepeat(animation: Animation?) {
                }
            })
            binding.keyboardView.startAnimation(this)
        }
    }
    private fun showKeyboard() {
        //向上位移显示动画  从自身位置的最下端向上滑动了自身的高度
        TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f
        ).apply {
            repeatMode = Animation.REVERSE
            duration = 200
            binding.keyboardView.startAnimation(this)
            binding.keyboardView.visibility = View.VISIBLE
        }
    }



    private fun processKeyPress(cmd:String) {
        if (cmd.isEmpty()) return
        when (cmd) {
            "Keyboard" -> {
                if (!isKeyboardShow)
                    showKeyboard()
                else
                    hideKeyboard()
                isKeyboardShow = !isKeyboardShow
            }
            "Repeat" -> {
                Thread {
                    for (i in 0..100) {
                        for (j in 0..5) {
                            nethack.command.sendCommand(NHKeyCommand('h'))
                        }
                        for (j in 0..5) {
                            nethack.command.sendCommand(NHKeyCommand('l'))
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
        // Ctrl|^C Meta|^M
        // Setting LS|Save #quit|Quit L20s|20s Keyboard|Abc
        nethack.prefs.commandPanel?.apply {
            var panelDefault = ifEmpty {
                getString(R.string.pref_keyboard_command_panel_default)
            }
            if (!panelDefault.contains("Setting"))
                panelDefault = "${panelDefault}\nSetting"
            binding.baseCommandPanel.apply {
                initBottomCommandSheet(panelDefault)
                onCommandPress = { cmd->
                    processKeyPress(cmd)
                }
            }
        }
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