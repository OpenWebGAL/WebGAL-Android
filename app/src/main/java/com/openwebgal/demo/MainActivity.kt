package com.openwebgal.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import androidx.webkit.WebViewClientCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var audioManager: AudioManager? = null
    private var saveLoadPath: ValueCallback<Array<Uri>>? = null
    private var saveData: String? = null
    private val FILECHOOSER_REQUEST_CODE = 0
    private val FILECREATE_REQUEST_CODE = 1


    @SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fullscreen() //全屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) //屏幕保持开启

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.domStorageEnabled = true
        webView.setInitialScale(100)
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                webView: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val interceptedRequest = assetLoader.shouldInterceptRequest(request.url)
                interceptedRequest?.let {
                    if (request.url.toString().endsWith("js", true)) {
                        it.mimeType = "text/javascript"
                    }
                }
                return interceptedRequest
            }

            override fun shouldOverrideUrlLoading(
                webView: WebView,
                request: WebResourceRequest
            ): Boolean {
                // 使用 Custom Tabs 打开外部链接
                val intent = CustomTabsIntent.Builder().build()
                intent.launchUrl(this@MainActivity, request.url)
                return true
            }
        }

        webView.loadUrl(getString(R.string.load_url))

        webView.addJavascriptInterface(ExportInterface(), "Export")
        webView.setDownloadListener { url, _, _, _, _ ->
            //获取 Blob 数据
            if (url.startsWith("blob:")) {
                getBlobData(url)
            } else return@setDownloadListener
        }

        webView.webChromeClient = object : WebChromeClient() {
            //导入存档与选项
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                return chooseFile(filePathCallback)
            }

            //移除默认播放海报
            override fun getDefaultVideoPoster(): Bitmap? {
                return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            }
        }
    }

    //获取 blob 数据
    private fun getBlobData(url: String) {
        try {
            val script = "javascript: (() => {" +
                    "async function getBase64StringFromBlobUrl() {" +
                    "const xhr = new XMLHttpRequest();" +
                    "xhr.open('GET', '${url}', true);" +
                    "xhr.responseType = 'blob';" +
                    "xhr.onload = () => {" +
                    "if (xhr.status === 200) {" +
                    "const blobResponse = xhr.response;" +
                    "const fileReaderInstance = new FileReader();" +
                    "fileReaderInstance.readAsDataURL(blobResponse);" +
                    "fileReaderInstance.onloadend = () => {" +
                    "const base64data = fileReaderInstance.result.replace(/data:/,'').split(';base64,');" +
                    //当 mime 为 application/json 时将存档数据传递到 getSaveData()
                    "if( base64data[0] === 'application/json') Export.getSaveData(base64data[1]);" +
                    "}" +
                    "}" +
                    "};" +
                    "xhr.send();" +
                    "}" +
                    "getBase64StringFromBlobUrl()" +
                    "}) ()"

            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Get blob data Failed", Toast.LENGTH_LONG).show()
        }
    }

    inner class ExportInterface {
        //获取存档数据
        @JavascriptInterface
        fun getSaveData(data: String) {
            saveData = String(Base64.decode(data, Base64.DEFAULT))
            createSave()
        }
    }

    //打开存档保存界面
    private fun createSave() {
        val saveName = getString(R.string.app_name) + " - save.json"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, saveName)
        }
        startActivityForResult(intent, FILECREATE_REQUEST_CODE)
    }

    //写入存档数据
    private fun saveFile(intent: Intent?) {
        try {
            val uri = intent?.data ?: return
            val outputStream = contentResolver.openOutputStream(uri)
            outputStream?.use {
                it.write(saveData?.toByteArray())
                it.flush()
                it.close()
            }
            saveData = null
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Export failed", Toast.LENGTH_LONG).show()
        }
    }

    //打开存档选择界面
    private fun chooseFile(filePathCallback: ValueCallback<Array<Uri>>): Boolean {
        saveLoadPath = filePathCallback
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, FILECHOOSER_REQUEST_CODE)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == Activity.RESULT_OK) {
            intent ?: return
            //requestCode 为 FILECREATE_REQUEST_CODE 时向 saveFile() 传递 intent
            if (requestCode == FILECREATE_REQUEST_CODE) saveFile(intent)
            //requestCode 为 FILECHOOSER_REQUEST_CODE 时向 WebView 传递 intent
            if (requestCode == FILECHOOSER_REQUEST_CODE) {
                saveLoadPath!!.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, intent)
                )
                saveLoadPath = null
            }
        }

        if (resultCode == Activity.RESULT_CANCELED) {
            if (requestCode == FILECREATE_REQUEST_CODE) return
            if (requestCode == FILECHOOSER_REQUEST_CODE) {
                saveLoadPath!!.onReceiveValue(null)
                saveLoadPath = null
                return
            }
        } else return
    }

    //游戏后台暂停
    override fun onPause() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
//        val pauseMediaScript =
//            "document.querySelectorAll('video, audio').forEach(mediaElement => mediaElement.pause())"
//        webView.evaluateJavascript(pauseMediaScript, null)
        webView.run {
            pauseTimers()
            onPause()
        }
        super.onPause()
    }

    //游戏从后台恢复
    override fun onResume() {
        audioManager?.abandonAudioFocus(null)
//        val resumeMediaScript =
//            "document.querySelectorAll('video, audio').forEach(mediaElement => {" +
//                    "if((!mediaElement.loop && !mediaElement.ended) || mediaElement.loop) {" +
//                    "if(mediaElement.readyState >= 2) {" +
//                    "mediaElement.play()" +
//                    "}" +
//                    "}" +
//                    "})"
//        webView.evaluateJavascript(resumeMediaScript, null)
        webView.run {
            resumeTimers()
            onResume()
        }
        super.onResume()
    }

    //全屏
    private fun fullscreen() {
        //android R 以上全屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController!!.hide(
                WindowInsets.Type.statusBars()
                        or WindowInsets.Type.navigationBars()
            )
        } else {
            //android R 以下全屏
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }
}

