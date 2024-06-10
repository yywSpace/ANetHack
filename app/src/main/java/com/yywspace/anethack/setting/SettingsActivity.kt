package com.yywspace.anethack.setting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.yywspace.anethack.R
import java.io.File


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat(){
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
                    fileNames.sortByDescending { it.split(".")[fileNames.size-2] }
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