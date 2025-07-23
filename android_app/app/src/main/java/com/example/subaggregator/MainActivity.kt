// 文件路径: app/src/main/java/com/example/subaggregator/MainActivity.kt

package com.example.subaggregator

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.subaggregator.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

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

        // 2. 启动 Flask 服务器并立即更新UI
        startFlaskServer()

        // 3. 配置 WebView
        binding.webView.webViewClient = WebViewClient()
        binding.webView.settings.javaScriptEnabled = true
        
        // 延迟加载URL，给后台的Flask服务器一点启动时间
        binding.webView.postDelayed({
            binding.webView.loadUrl("http://127.0.0.1:5000")
        }, 1500) // 延迟1.5秒
    }

    private fun startFlaskServer() {
        // [关键修正] 在启动后台线程前，我们先乐观地更新UI
        // 因为服务器启动是阻塞的，我们不能等它返回。
        binding.textViewStatus.text = "服务器运行在 http://127.0.0.1:5000"

        thread(start = true) {
            try {
                val py = Python.getInstance()
                val appModule = py.getModule("app")

                // 这个调用会阻塞当前线程，并且永远不会返回
                appModule.callAttr("start_server")
                
            } catch (e: PyException) {
                // [关键修正] 如果上面的代码（例如 getModule 或 callAttr 之前的准备工作）
                // 出现问题，或者服务器启动失败（如端口被占用），
                // 我们就在UI线程上显示详细的错误信息，方便调试。
                runOnUiThread {
                    binding.textViewStatus.text = "启动服务器失败:\n${e.message}"
                }
            }
        }
    }
}

