// 文件路径: android_app/app/src/main/java/com/example/subaggregator/MainActivity.kt

package com.example.subaggregator // <-- 请确保这里是您的包名

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.subaggregator.databinding.ActivityMainBinding // 自动生成的绑定类
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // 使用视图绑定安全地访问 XML 布局中的视图
    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 初始化 Python 环境
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // 2. 在后台线程中启动 Flask 服务器
        startFlaskServer()

        // 3. 配置 WebView 来显示我们本地服务器的前端页面
        binding.webView.webViewClient = WebViewClient() // 确保链接在 App 内的 WebView 中打开，而不是跳到外部浏览器
        binding.webView.settings.javaScriptEnabled = true // 必须启用 JavaScript

        // 延迟加载 URL，给后台的 Flask 服务器一点启动时间
        binding.webView.postDelayed({
            binding.webView.loadUrl("http://127.0.0.1:5000")
        }, 2000) // 延迟 2 秒应该足够
    }

    private fun startFlaskServer() {
        // [关键] 必须在后台线程中运行网络服务，否则会阻塞 UI 线程导致应用崩溃
        thread(start = true) {
            try {
                val py = Python.getInstance()
                // Python 模块名就是我们的文件名 `app.py` -> `app`
                val appModule = py.getModule("app")
                // 调用 `app.py` 中我们专门为安卓准备的 `start_server()` 函数
                appModule.callAttr("start_server")

                // 如果服务器成功启动（函数没有崩溃），我们可以更新一下 UI 状态
                runOnUiThread {
                    binding.textViewStatus.text = "服务器运行在 http://127.0.0.1:5000"
                }

            } catch (e: PyException) {
                // 如果 Python 代码在启动时抛出异常，在 UI 上显示错误信息，方便调试
                runOnUiThread {
                    binding.textViewStatus.text = "启动服务器失败:\n${e.message}"
                }
            }
        }
    }
}
