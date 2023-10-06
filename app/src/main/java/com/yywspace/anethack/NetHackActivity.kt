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
    private var isCustomPanelShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNethackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler = Handler(Looper.getMainLooper())
        nethack = NetHack(handler, this, binding,"${filesDir.path}/nethackdir")
        binding.keyboardView.apply {
            onKeyPress = {
                if(nethack.isRunning)
                    nethack.command.sendCommand(NHCommand(it.toChar()))
            }
            visibility = View.GONE
        }
        initControlPanel()
        copyFilesFromAssets("nethackdir")
        nethack.run()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun initControlPanel() {
        val panelDefault = """
            z|Zap f|Fire #kick|Kick a|Apply D|Drop Center
            <|Up >|Down e|Eat .|Rest ,|Pick i|Bag
            Custom #|Extend S|Save #quit|Quit 20s|Search Keyboard|...
        """.trimIndent()
        val panel = nethack.prefs.panel?:panelDefault
        val panelArray = panel.split("\n")
        panelArray.forEachIndexed { i, rowPanel->
            val linearLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dip2px(context, 40f)
                )
                dividerDrawable = GradientDrawable().apply {
                    setColor(Color.GRAY)
                    setSize(1, layoutParams.height)
                }
                showDividers = SHOW_DIVIDER_MIDDLE
                dividerPadding = 2
                orientation = HORIZONTAL
            }
            initCustomControlPanel(this, linearLayout, rowPanel)

            if(i == panelArray.size -1)
                initCustomControlPanel(this, binding.basePanel, rowPanel)
            else {
                initCustomControlPanel(this, linearLayout, rowPanel)
                binding.customPanel.addView(linearLayout)
            }
        }
        binding.customPanel.visibility = View.GONE
    }
    private fun initCustomControlPanel(context: Context,panelView:LinearLayout, panel:String) {
        panelView.removeAllViews()
        panel.split(" ").forEach{ item ->
            val array = item.split("|")
            val cmd = array[0]
            var label = array[0]
            if(array.size == 2)
                label = array[1]
            val button = TextView(context).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(
                     0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
                gravity = Gravity.CENTER
                typeface = Typeface.defaultFromStyle(Typeface.BOLD);
                setTextColor(Color.GRAY)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                when (cmd) {
                    "Keyboard" -> {
                        setOnClickListener {
                            if(isCustomPanelShow) {
                                binding.customPanel.visibility = View.GONE
                                isCustomPanelShow = false
                            }
                            if(isKeyboardShow)
                                binding.keyboardView.visibility = View.GONE
                            else
                                binding.keyboardView.visibility = View.VISIBLE
                            isKeyboardShow = !isKeyboardShow
                        }
                    }
                    "Center" -> {
                        setOnClickListener {
                            binding.mapView.centerPlayerInScreen()
                        }
                    }
                    "Custom" -> {
                        setOnLongClickListener {
                            true
                        }
                        setOnClickListener {
                            if(isKeyboardShow) {
                                binding.keyboardView.visibility = View.GONE
                                isKeyboardShow = false
                            }
                            if(isCustomPanelShow)
                                binding.customPanel.visibility = View.GONE
                            else
                                binding.customPanel.visibility = View.VISIBLE
                            isCustomPanelShow = !isCustomPanelShow
                        }
                    }
                    else -> {
                        setOnClickListener {
                            if(nethack.isRunning) {
                                if(cmd.startsWith("#")) {
                                    nethack.command.sendExtendCommand(NHExtendCommand(cmd))
                                    return@setOnClickListener
                                }
                                cmd.toCharArray().forEach {
                                    nethack.command.sendCommand(NHCommand(it))
                                }
                            }

                        }
                    }
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

    // 根据手机的分辨率从 dp 的单位 转成为 px(像素)
    private fun dip2px(context: Context, dpValue: Float): Int {
        // 获取当前手机的像素密度（1个dp对应几个px）
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt() // 四舍五入取整
    }

    // 根据手机的分辨率从 px(像素) 的单位 转成为 dp
    fun px2dip(context: Context, pxValue: Float): Int {
        // 获取当前手机的像素密度（1个dp对应几个px）
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt() // 四舍五入取整
    }
}