package com.yywspace.anethack.setting

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
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
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.yywspace.anethack.R
import java.io.File


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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

    private fun initStatusBar() {
        window.statusBarColor = Color.TRANSPARENT
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    class SettingsFragment : PreferenceFragmentCompat(){
       private var userSoundPerm:Preference? = null

        override fun onResume() {
            super.onResume()
            userSoundPerm?.apply {
                if (XXPermissions.isGranted(context, Permission.READ_MEDIA_AUDIO))
                    setSummary(R.string.pref_sound_user_perm_granted)
                else
                    setSummary(R.string.pref_sound_user_perm_not_granted)
            }
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
                    it.inputType = InputType.TYPE_CLASS_NUMBER;
                }
            }
            findPreference<EditTextPreference>("messageHistorySize")?.apply {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER;
                }
            }
            findPreference<Preference>("optionEdit")?.apply {
                setOnPreferenceClickListener { _ ->
                    startActivity(Intent(context, OptionEditActivity::class.java))
                    true
                }
            }

            userSoundPerm = findPreference<Preference>("userSoundPerm")?.apply {
                setOnPreferenceClickListener {
                    XXPermissions.with(context)
                        .permission(Permission.READ_MEDIA_AUDIO)
                        .request(object : OnPermissionCallback {
                            override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                                if (!allGranted)
                                    return
                                Toast.makeText(context, R.string.permission_granted, Toast.LENGTH_SHORT).show()
                            }
                            override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                                if (doNotAskAgain) {
                                    Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show()
                                    XXPermissions.startPermissionActivity(context, permissions)
                                }
                            }
                        })
                    true
                }
            }
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