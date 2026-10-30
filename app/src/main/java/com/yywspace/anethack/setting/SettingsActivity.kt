package com.yywspace.anethack.setting

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.gson.Gson
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.yywspace.anethack.R
import com.yywspace.anethack.keybord.CommandEntry
import com.yywspace.anethack.SharedPreferencesUtils
import com.yywspace.anethack.Utils
import java.io.File


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Utils.applyThemeMode(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        val toolbar = findViewById<Toolbar>(R.id.setting_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initStatusBar()
    }

    /** 当前是否为深色（按资源当前配置判断） */
    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private fun initStatusBar() {
        window.statusBarColor = Color.TRANSPARENT
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        // 深色主题下状态栏图标要用亮色
        windowInsetsController.isAppearanceLightStatusBars = !isNightMode()
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    class SettingsFragment : PreferenceFragmentCompat(){
        private var userSoundPerm:CheckBoxPreference? = null

        override fun onResume() {
            super.onResume()
            // without the permission the sound files of the player cannot be
            // read at all, so the switch is forced off in that case
            if (!XXPermissions.isGranted(requireContext(), Permission.READ_MEDIA_AUDIO))
                userSoundPerm?.isChecked = false
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            findPreference<Preference>("dumpLog")?.setOnPreferenceClickListener {
                context?.apply {
                    val fileNames = mutableListOf<String>()
                    File(filesDir,"logs/dump")
                        .walk()
                        .maxDepth(1)
                        .filter { it.extension == "log"  }
                        .forEach { fileNames.add(it.name) }
                    fileNames.sortByDescending { it.split(".")[0] }
                    AlertDialog.Builder(this).apply {
                        setTitle(R.string.pref_log_dump)
                        setItems(fileNames.toTypedArray()) { _, which ->
                            openInternalFile(context, "logs/dump/${fileNames[which]}")
                        }
                        setNegativeButton(R.string.dialog_confirm, null)
                        create()
                        show()
                    }
                }

                true
            }
            findPreference<Preference>("errorLog")?.setOnPreferenceClickListener {
                openInternalFile(context, "logs/error/nethack.log")
                true
            }
            findPreference<EditTextPreference>("dumpLogMaxSize")?.apply {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }
            findPreference<EditTextPreference>("messageHistorySize")?.apply {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }
            findPreference<Preference>("themeMode")?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    // 先把新值写进内存再切换主题，然后重建本页让配色立刻生效
                    Utils.applyThemeMode(requireContext(), newValue as String)
                    activity?.recreate()
                    true
                }
            }
            findPreference<Preference>("optionEdit")?.apply {
                setOnPreferenceClickListener { _ ->
                    startActivity(Intent(context, OptionEditActivity::class.java))
                    true
                }
            }
            findPreference<Preference>("commandPanelExtra")?.apply {
                setOnPreferenceClickListener {
                    showExtraCommandPanelEditor()
                    true
                }
                summary = extraCommandPanelSummary()
            }
            findPreference<Preference>("commandPanel")?.apply {
                setOnPreferenceClickListener {
                    showCommandPanelEditor()
                    true
                }
                updateCommandPanelSummary()
            }
            userSoundPerm = findPreference<CheckBoxPreference>("userSound")?.apply {
                setOnPreferenceClickListener {
                    if (XXPermissions.isGranted(context, Permission.READ_MEDIA_AUDIO)) {
                        // permission is there: let the switch toggle itself
                        return@setOnPreferenceClickListener false
                    }
                    isChecked = false
                    XXPermissions.with(context)
                        .permission(Permission.READ_MEDIA_AUDIO)
                        .request(object : OnPermissionCallback {
                            override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                                if (!allGranted)
                                    return
                                isChecked = true
                                Toast.makeText(context, R.string.permission_granted, Toast.LENGTH_SHORT).show()
                            }

                            override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                                isChecked = false
                                if (doNotAskAgain) {
                                    AlertDialog.Builder(context).apply {
                                        setTitle(R.string.permission_audio)
                                        setMessage(R.string.permission_denied)
                                        setPositiveButton(R.string.permission_open_settings) { _, _ ->
                                            XXPermissions.startPermissionActivity(context, permissions)
                                        }
                                        setNegativeButton(R.string.dialog_cancel, null)
                                        create()
                                        show()
                                    }
                                } else {
                                    Toast.makeText(context, R.string.permission_denied_hint, Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                    true
                }
            }
        }

        /** 读命令栏配置（与设置框架默认使用同一个 SharedPreferences 文件） */
        private fun readCommandPanel(): String =
            context?.let { SharedPreferencesUtils(it).commandPanel }.orEmpty()

        /** 写命令栏配置 */
        private fun writeCommandPanel(value: String) {
            val ctx = context ?: return
            SharedPreferencesUtils(ctx).commandPanel = value
        }

        /** 命令栏配置的简略显示：默认命令或"N 个命令：前几个标签" */
        private fun updateCommandPanelSummary() {
            val pref = findPreference<Preference>("commandPanel") ?: return
            val ctx = pref.context
            val entries = runCatching {
                Gson().fromJson(readCommandPanel(), Array<CommandEntry>::class.java)
            }.getOrNull()?.filter { it.command.isNotEmpty() && it.label.isNotEmpty() }
            pref.summary = if (entries.isNullOrEmpty())
                ctx.getString(R.string.command_panel_summary_default)
            else
                ctx.getString(
                    R.string.command_panel_summary,
                    entries.size,
                    entries.take(3).joinToString(" / ") { it.label }
                )
        }
        /** "更多命令"面板的简略显示 */
        private fun extraCommandPanelSummary(): String {
            val ctx = context ?: return ""
            val raw = SharedPreferencesUtils(ctx).commandPanelExtra
            val count = runCatching {
                Gson().fromJson(raw, Array<Array<CommandEntry>>::class.java)?.sumOf { it.size }
            }.getOrNull() ?: 0
            return if (count == 0) ctx.getString(R.string.command_panel_summary_default)
            else ctx.getString(R.string.command_panel_summary, count, ctx.getString(R.string.pref_keyboard_command_panel_extra))
        }

        /** "更多命令"面板的编辑弹窗：和命令栏那项一致，只是存到另一个偏好 */
        private fun showExtraCommandPanelEditor() {
            val ctx = context ?: return
            val prefs = SharedPreferencesUtils(ctx)
            val input = EditText(ctx).apply {
                setText(prefs.commandPanelExtra)
                setTextIsSelectable(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            val container = FrameLayout(ctx).apply {
                val pad = (20 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, 0)
                addView(input)
            }
            AlertDialog.Builder(ctx)
                .setTitle(R.string.pref_keyboard_command_panel_extra)
                .setView(container)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    prefs.commandPanelExtra = input.text.toString().trim()
                    findPreference<Preference>("commandPanelExtra")?.summary = extraCommandPanelSummary()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        /**
         * 命令栏配置的编辑弹窗。
         *
         * 用应用自己的 Confirm/Cancel 按钮（而不是 EditTextPreference 的系统按钮），
         * 避免在中文系统上和其它英文界面混搭。
         */
        private fun showCommandPanelEditor() {
            val ctx = context ?: return
            val input = EditText(ctx).apply {
                setText(readCommandPanel())
                setTextIsSelectable(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            val container = FrameLayout(ctx).apply {
                val pad = (20 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, 0)
                addView(input)
            }
            AlertDialog.Builder(ctx)
                .setTitle(R.string.pref_keyboard_command_panel)
                .setView(container)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    writeCommandPanel(input.text.toString().trim())
                    updateCommandPanelSummary()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        private fun openInternalFile(context: Context?, relativePath:String) {
            context?.apply {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "com.yywspace.anethack.provider",
                    File(filesDir,relativePath)
                )
                intent.setDataAndType(contentUri, "text/plain")
                startActivity(intent)
            }
        }
    }
}