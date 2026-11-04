package com.yywspace.anethack.setting

import com.yywspace.anethack.theme.AppTheme
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.yywspace.anethack.R
import com.yywspace.anethack.log.LogFiles
import java.io.File


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyTheme(this)
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



    private fun initStatusBar() = AppTheme.applyStatusBar(this)

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
                context?.let { ctx ->
                    DumpLogDialog.show(ctx, File(ctx.filesDir, "logs/dump")) { name ->
                        startActivity(
                            Intent(ctx, TextViewerActivity::class.java)
                                .putExtra(TextViewerActivity.EXTRA_PATH, File(ctx.filesDir, "logs/dump/$name").absolutePath)
                        )
                    }
                }

                true
            }
            findPreference<Preference>("clearDumpLog")?.setOnPreferenceClickListener {
                val ctx = requireContext()
                DumpLogDialog.clear(ctx, File(ctx.filesDir, "logs/dump"))
                true
            }
            findPreference<Preference>("appLog")?.setOnPreferenceClickListener {
                val ctx = requireContext()
                val file = LogFiles.appLog(ctx)
                if (file.exists()) openTextViewer(ctx, file)
                else Toast.makeText(ctx, R.string.pref_log_no_app, Toast.LENGTH_SHORT).show()
                true
            }
            findPreference<Preference>("lastCrash")?.setOnPreferenceClickListener {
                val ctx = requireContext()
                val file = LogFiles.latestCrash(ctx)
                if (file != null) openTextViewer(ctx, file)
                else Toast.makeText(ctx, R.string.pref_log_no_crash, Toast.LENGTH_SHORT).show()
                true
            }
            findPreference<Preference>("exportLogs")?.setOnPreferenceClickListener {
                shareLogs(requireContext())
                true
            }
            findPreference<Preference>("clearLogs")?.setOnPreferenceClickListener {
                val ctx = requireContext()
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.pref_log_clear_logs)
                    .setMessage(R.string.pref_log_clear_logs_confirm)
                    .setPositiveButton(R.string.dialog_confirm) { _, _ -> LogFiles.clear(ctx) }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
                true
            }
            findPreference<Preference>("themeMode")?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    // 先把新值写进内存再切换主题，然后重建本页让配色立刻生效
                    AppTheme.applyTheme(requireContext(), newValue as String)
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
            findPreference<Preference>("commandPanel")?.apply {
                setOnPreferenceClickListener {
                    openCommandEditor()
                    true
                }
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

        /** 打开命令编辑页：底部命令栏与扩展面板在同一个页面里编辑 */
        private fun openCommandEditor() {
            val ctx = context ?: return
            startActivity(Intent(ctx, CommandEditorActivity::class.java))
        }

        private fun openTextViewer(context: Context, file: File) {
            startActivity(
                Intent(context, TextViewerActivity::class.java)
                    .putExtra(TextViewerActivity.EXTRA_PATH, file.absolutePath)
            )
        }

        private fun shareLogs(context: Context) {
            val zip = LogFiles.export(context)
            if (zip == null) {
                Toast.makeText(context, R.string.pref_log_no_app, Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "com.yywspace.anethack.provider", zip)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.pref_log_export)))
        }
    }
}
