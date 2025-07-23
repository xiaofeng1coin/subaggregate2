// 文件路径: app/src/main/java/com/example/subaggregator/MainActivity.kt

package com.example.subaggregator

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.subaggregator.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val GITHUB_API_URL = "https://api.github.com/repos/xiaofeng1coin/subaggregate2/releases/latest"
    // [关键修改开始] 新增一个变量来存储下载任务ID
    private var downloadID: Long = -1L
    // [关键修改结束]

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startFlaskService()
            } else {
                Toast.makeText(this, "需要通知权限以确保服务在后台稳定运行。", Toast.LENGTH_LONG).show()
                binding.textViewStatus.text = "服务未运行（需要通知权限）"
            }
        }

    // [关键修改开始] 注册一个新的启动器来处理“安装未知应用”权限的返回结果
    private val requestInstallPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    // 用户授权后，再次尝试安装
                    installApkFromStoredID()
                } else {
                    showToastOnUI("未授予安装权限，无法完成更新。")
                }
            }
        }
    // [关键修改结束]

    // [关键修改开始] 定义一个广播接收器来监听下载完成事件
    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            // 确保是我们发起的下载任务
            if (downloadID == id) {
                val query = DownloadManager.Query().setFilterById(id)
                val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                         // 下载成功，弹出安装确认对话框
                        showInstallConfirmDialog()
                    } else {
                        showToastOnUI("下载失败，请重试。")
                    }
                }
                cursor.close()
            }
        }
    }
    // [关键修改结束]


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // [关键修改开始] 注册广播接收器
        registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        // [关键修改结束]

        askForNotificationPermissionAndStartService()

        binding.webView.webViewClient = WebViewClient()
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.postDelayed({
            binding.webView.loadUrl("http://127.0.0.1:5000")
        }, 1500)
    }

    // [关键修改开始] 在Activity销毁时，取消注册接收器，避免内存泄漏
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }
    // [关键修改结束]

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_check_update -> {
                Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show()
                checkForUpdate()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun askForNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startFlaskService()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startFlaskService()
        }
    }

    private fun startFlaskService() {
        binding.textViewStatus.text = "服务运行在 http://127.0.0.1:5000"
        val serviceIntent = Intent(this, FlaskService::class.java).apply {
            action = FlaskService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

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

    private fun downloadApk(url: String, fileName: String) {
        try {
            // [关键修改] 清理旧的APK文件，防止冲突
            val destination = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (destination.exists()) {
                destination.delete()
            }

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("正在下载 SubAggregator 更新")
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadID = downloadManager.enqueue(request) // 保存下载ID
            showToastOnUI("开始下载...请在通知栏查看进度。")
        } catch (e: Exception){
            showToastOnUI("下载启动失败: ${e.message}")
        }
    }

    // [关键修改开始] 新增函数：显示安装确认对话框
    private fun showInstallConfirmDialog() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("下载完成")
                .setMessage("新版本已下载完毕，是否立即安装？")
                .setPositiveButton("是") { _, _ ->
                    checkInstallPermissionAndInstall()
                }
                .setNegativeButton("否", null)
                .setCancelable(false)
                .show()
        }
    }
    // [关键修改结束]

    // [关键修改开始] 新增函数：检查并请求安装权限
    private fun checkInstallPermissionAndInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(this)
                    .setTitle("需要授权")
                    .setMessage("为了更新应用，请授予“安装未知应用”的权限。")
                    .setPositiveButton("去授权") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        requestInstallPermissionLauncher.launch(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
        }
        installApkFromStoredID()
    }
    // [关键修改结束]

    // [关键修改开始] 新增函数：根据已保存的下载ID来安装APK
    private fun installApkFromStoredID() {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val uri: Uri? = downloadManager.getUriForDownloadedFile(downloadID)

        if (uri == null) {
            showToastOnUI("无法找到下载的文件，请重试。")
            return
        }
        
        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) ,"SubAggregator_v${packageManager.getPackageInfo(packageName,0).versionName}.apk")
                FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", File(uri.path!!))
            } else {
                uri
            }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
             startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            showToastOnUI("无法启动安装程序: ${e.localizedMessage}")
        }
    }
    // [关键修改结束]

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

    private fun showToastOnUI(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
