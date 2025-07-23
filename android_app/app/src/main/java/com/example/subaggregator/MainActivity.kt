// 文件路径: app/src/main/java/com/example/subaggregator/MainActivity.kt

package com.example.subaggregator

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.subaggregator.databinding.ActivityMainBinding
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    // [新增] 你的GitHub仓库API地址
    private val GITHUB_API_URL = "https://api.github.com/repos/xiaofeng1coin/subaggregate2/releases/latest"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // [修改] 设置我们自己的 Toolbar 作为 App 的 ActionBar
        setSupportActionBar(binding.toolbar)

        // 1. 初始化 Python (保持不变)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // 2. 启动 Flask (保持不变)
        startFlaskServer()

        // 3. 配置 WebView (保持不变)
        binding.webView.webViewClient = WebViewClient()
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.postDelayed({
            binding.webView.loadUrl("http://127.0.0.1:5000")
        }, 1500)
    }

    // [新增] 重写此方法来加载我们的菜单布局 (main_menu.xml)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // [新增] 重写此方法来处理菜单项的点击事件
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_check_update -> {
                // 当用户点击“检查更新”按钮时，执行此处的代码
                Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show()
                checkForUpdate()
                true // 返回true，表示事件已被我们成功处理
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // startFlaskServer 函数保持不变
    private fun startFlaskServer() {
        binding.textViewStatus.text = "服务器运行在 http://127.0.0.1:5000"
        thread(start = true) {
            try {
                val py = Python.getInstance()
                val appModule = py.getModule("app")
                appModule.callAttr("start_server")
            } catch (e: PyException) {
                runOnUiThread {
                    binding.textViewStatus.text = "启动服务器失败:\n${e.message}"
                }
            }
        }
    }

    // [新增] 检查更新的完整逻辑
    private fun checkForUpdate() {
        thread(start = true) {
            try {
                val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersion = pInfo.versionName
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000 // 15秒连接超时
                connection.readTimeout = 15000    // 15秒读取超时
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(response)
                val latestVersion = json.getString("tag_name").removePrefix("v")
                if (isNewerVersion(latestVersion, currentVersion)) {
                    val notes = json.getString("body")
                    val assets = json.getJSONArray("assets")
                    var apkUrl: String? = null
                    if (assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                apkUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }
                    if (apkUrl != null) {
                        showUpdateDialog(latestVersion, notes, apkUrl)
                    } else {
                        showToastOnUI("找到新版本，但未找到APK下载链接。")
                    }
                } else {
                    showToastOnUI("当前已是最新版本 (v$currentVersion)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToastOnUI("检查更新失败: ${e.localizedMessage}")
            }
        }
    }

    // [新增] 显示更新对话框
    private fun showUpdateDialog(version: String, notes: String, url: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("发现新版本 v$version")
                .setMessage("更新日志:\n\n$notes")
                .setPositiveButton("立即下载") { _, _ ->
                    downloadApk(url, "SubAggregator_v$version.apk")
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }

    // [新增] 使用系统下载器下载APK
    private fun downloadApk(url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("正在下载 SubAggregator 更新")
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            showToastOnUI("开始下载...请在通知栏查看进度。")
        } catch (e: Exception){
            showToastOnUI("下载启动失败: ${e.message}")
        }
    }

    // [新增] 版本号比较逻辑
    private fun isNewerVersion(newVersion: String, oldVersion: String): Boolean {
       val newParts = newVersion.split('.').map { it.toIntOrNull() ?: 0 }
       val oldParts = oldVersion.split('.').map { it.toIntOrNull() ?: 0 }
       val maxLen = maxOf(newParts.size, oldParts.size)
       for (i in 0 until maxLen) {
           val newPart = newParts.getOrElse(i) { 0 }
           val oldPart = oldParts.getOrElse(i) { 0 }
           if (newPart > oldPart) return true
           if (newPart < oldPart) return false
       }
       return false
    }

    // [新增] 在UI线程显示Toast
    private fun showToastOnUI(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
