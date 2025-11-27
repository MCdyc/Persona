// 包声明，定义了代码所在的包
package com.bytedance.persona

// 导入所需的 Android 和 Jetpack Compose 库
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bytedance.persona.ui.theme.PersonaTheme

// MainActivity 是应用的主活动，继承自 ComponentActivity
class MainActivity : ComponentActivity() {
    // onCreate 是活动的入口点，在活动创建时调用
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边缘到边缘显示，使应用内容可以绘制到系统栏后面
        enableEdgeToEdge()
        // 设置活动的内容为 Jetpack Compose 界面
        setContent {
            //应用自定义的 PersonaTheme 主题
            PersonaTheme {
                // Scaffold 是一个提供基本应用布局结构的 Composable
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Greeting 是一个自定义的 Composable，用于显示问候语
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// @Composable 注解表示这是一个 Composable 函数，用于构建 UI
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    // Text 是一个 Composable，用于显示文本
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

// @Preview 注解用于在 Android Studio 中预览 Composable
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    //应用自定义的 PersonaTheme 主题
    PersonaTheme {
        // 预览 Greeting Composable
        Greeting("Android")
    }
}
