package com.yywspace.anethack.setting

import android.app.Dialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.yywspace.anethack.NHTileSet
import com.yywspace.anethack.R
import com.yywspace.anethack.SharedPreferencesUtils

/**
 * 自定义 tileset 的图片与单元格尺寸设置。
 *
 * 设置页只负责打开本对话框；图片选择、拷贝和校验都收敛在这里。
 */
class CustomTileSetDialog : DialogFragment() {

    private var selectedUri: Uri? = null
    private var preview: ImageView? = null
    private var widthInput: EditText? = null
    private var heightInput: EditText? = null
    private var onSaved: (() -> Unit)? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        selectedUri = uri
        runCatching {
            requireContext().contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()?.let { showPreview(it) }
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        selectedUri = savedInstanceState?.getString(STATE_URI)?.let(Uri::parse)
        val prefs = SharedPreferencesUtils(requireContext())
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_custom_tileset, null)

        preview = view.findViewById(R.id.custom_tileset_preview)
        widthInput = view.findViewById(R.id.custom_tileset_width)
        heightInput = view.findViewById(R.id.custom_tileset_height)

        widthInput?.setText(prefs.customTileWidth.toString())
        heightInput?.setText(prefs.customTileHeight.toString())

        view.findViewById<View>(R.id.custom_tileset_picker).setOnClickListener {
            pickImage.launch("image/*")
        }

        val current = NHTileSet.customTileFile(requireContext())
        if (selectedUri == null && current.isFile) {
            BitmapFactory.decodeFile(current.absolutePath)?.let { showPreview(it) }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_tileset_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (save()) {
                    onSaved?.invoke()
                    dialog.dismiss()
                }
            }
        }
        return dialog
    }

    fun setOnSavedListener(listener: () -> Unit): CustomTileSetDialog {
        onSaved = listener
        return this
    }


    private fun showPreview(bitmap: android.graphics.Bitmap) {
        preview?.apply {
            setPadding(0, 0, 0, 0)
            setImageBitmap(bitmap)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedUri?.let { outState.putString(STATE_URI, it.toString()) }
    }

    private fun save(): Boolean {
        val context = context ?: return false
        val width = widthInput?.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val height = heightInput?.text?.toString()?.trim()?.toIntOrNull() ?: 0
        if (width <= 0 || height <= 0) {
            Toast.makeText(context, R.string.custom_tileset_invalid_size, Toast.LENGTH_SHORT).show()
            return false
        }

        val target = NHTileSet.customTileFile(context)
        val temp = NHTileSet.customTileTempFile(context)
        val sourceUri = selectedUri
        if (sourceUri != null) {
            val copyResult = runCatching {
                val parent = target.parentFile ?: error("no parent")
                if (!parent.exists()) parent.mkdirs()
                val input = context.contentResolver.openInputStream(sourceUri)
                    ?: error("openInputStream returned null")
                input.use { source ->
                    temp.outputStream().use { output -> source.copyTo(output) }
                }
            }
            if (copyResult.isFailure) {
                temp.delete()
                return false
            }
        } else if (!target.isFile) {
            Toast.makeText(context, R.string.custom_tileset_no_image, Toast.LENGTH_SHORT).show()
            return false
        }

        val candidate = if (sourceUri != null) temp else target
        val bitmap = BitmapFactory.decodeFile(candidate.absolutePath)
        if (bitmap == null) {
            if (sourceUri != null) temp.delete()
            Toast.makeText(context, R.string.custom_tileset_invalid_size, Toast.LENGTH_SHORT).show()
            return false
        }
        if (sourceUri != null) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                return false
            }
        }

        val prefs = SharedPreferencesUtils(context)
        prefs.customTileWidth = width
        prefs.customTileHeight = height
        prefs.customTileSetVersion++
        prefs.tileSet = TILE_SET_CUSTOM
        Toast.makeText(context, R.string.custom_tileset_saved, Toast.LENGTH_SHORT).show()
        return true
    }

    companion object {
        const val TAG = "CustomTileSetDialog"
        const val TILE_SET_CUSTOM = "custom"
        private const val STATE_URI = "selected_uri"


        fun newInstance(onSaved: () -> Unit): CustomTileSetDialog =
            CustomTileSetDialog().setOnSavedListener(onSaved)

    }
}


