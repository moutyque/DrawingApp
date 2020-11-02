package small.app.drawingapp

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import small.app.drawingapp.databinding.ActivityMainBinding
import small.app.drawingapp.databinding.DialogBrushSizeBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field


class MainActivity : AppCompatActivity() {

    lateinit var drawingView: DrawingView
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        drawingView = binding.drawingView
        drawingView.setBrushSize(20.toFloat())
        setContentView(binding.root)
    }

    private fun showBrushSizeChooserDialog() {
        val bindingLocal = DialogBrushSizeBinding.inflate(layoutInflater)

        val brushDialog = Dialog(this)
        brushDialog.setContentView(bindingLocal.root)
        brushDialog.setTitle("Brush size: ")

        val smallBtn = bindingLocal.smallBrush
        smallBtn.setOnClickListener {
            drawingView.setBrushSize(10.toFloat())
            brushDialog.dismiss()
        }

        val mediumBtn = bindingLocal.mediumBrush
        mediumBtn.setOnClickListener {
            drawingView.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn = bindingLocal.largeBrush
        largeBtn.setOnClickListener {
            drawingView.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()

    }

    fun setBrushColor(view: View) {
        val btn = view as ImageButton
        val gradientDrawable = btn.drawable as GradientDrawable
        val aClass: Class<out GradientDrawable> = gradientDrawable.javaClass
        try {
            val mFillPaint: Field = aClass.getDeclaredField("mFillPaint")
            mFillPaint.setAccessible(true)
            val strokePaint: Paint = mFillPaint.get(gradientDrawable) as Paint
            val solidColor: Int = strokePaint.getColor()
            drawingView.setColor(solidColor)
        } catch (e: Exception) {
            e.printStackTrace()
        }


    }

    fun showBrushSizeChooserDialog(view: View) {
        showBrushSizeChooserDialog()
    }

    private fun requestStoragePermission() {
        val arrayOfPermission = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                arrayOfPermission.toString()
            )
        ) {
            Toast.makeText(this, "Need permission to add a background task", Toast.LENGTH_SHORT)
                .show()
        }
        ActivityCompat.requestPermissions(this, arrayOfPermission, STORAGE_PERMISSION_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GALLERY) {
                try {
                    if (data!!.data != null) {
                        binding.ivBackground.visibility = View.VISIBLE
                        binding.ivBackground.setImageURI(data.data)
                    } else {
                        Toast.makeText(
                            this,
                            "Error while parsing the selected image",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Permission has been granted to use the storage",
                    Toast.LENGTH_SHORT
                )
                    .show()
            } else {
                Toast.makeText(
                    this,
                    "Permission has not been granted to use the storage",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    fun onSelectBackgroundPicture(view: View) {
        if (isReadStorageAllowed()) {
            val pickPhotoIntent =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

            startActivityForResult(pickPhotoIntent, GALLERY)
        } else {
            requestStoragePermission()
        }
    }

    private fun isReadStorageAllowed(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isWriteStorageAllowed(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onUndo(view: View) {
        drawingView.onClickUndo()
    }

    fun getBitmapFromView(view: View): Bitmap {
        val returnBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)

        return returnBitmap
    }

    fun onSave(view: View) {
        if (isWriteStorageAllowed()) {
            val uiScope = CoroutineScope(Dispatchers.Main)
            uiScope.launch {
                displayLoadingDialog()
                saveFile(getBitmapFromView(binding.frame))
            }
        } else
            requestStoragePermission()
    }

    lateinit var mProgressDialog: Dialog
    private fun displayLoadingDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog.show()
    }


    private suspend fun saveFile(mBitmap: Bitmap) {

        withContext(Dispatchers.IO) {
            val f =
                File(externalCacheDir!!.absoluteFile.toString() + File.separator + "KidDrawingApp_" + System.currentTimeMillis() / 1000 + ".png")

            try {
                val bytes = ByteArrayOutputStream()
                mBitmap.compress(
                    Bitmap.CompressFormat.PNG, 90, bytes
                )

                val fos = FileOutputStream(f)
                fos.write(bytes.toByteArray())
                fos.close()
            } catch (e: java.lang.Exception) {
                Log.e("SaveProcess", e.stackTraceToString())
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "File saved failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "File saved successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }

            mProgressDialog.dismiss()
            MediaScannerConnection.scanFile(
                this@MainActivity, arrayOf(f.toString()), null
            ) { path, uri ->
                val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.type = "image/png"
                startActivity(Intent.createChooser(shareIntent, "Share"))
            }
        }
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1
        private const val GALLERY = 2

    }
}