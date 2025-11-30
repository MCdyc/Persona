package com.bytedance.persona

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.bytedance.persona.llm.DefaultLlmProvider
import com.bytedance.persona.llm.Llm
import com.bytedance.persona.llm.defaultModels
import com.bytedance.persona.model.Message
import com.bytedance.persona.model.MessageType
import com.bytedance.persona.model.Sender
import com.bytedance.persona.ui.theme.PersonaTheme
import com.bytedance.persona.viewmodel.ChatViewModel
import com.bytedance.persona.viewmodel.ChatViewModelFactory
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

class MainActivity : ComponentActivity() {

    // 1. 实例化 LlmProvider 和 ViewModelFactory
    private val llmProvider = DefaultLlmProvider()
    private val viewModelFactory: ChatViewModelFactory by lazy {
        ChatViewModelFactory(llmProvider)
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 2. 使用自定义 Factory 创建 ViewModel
        val viewModel: ChatViewModel by viewModels { viewModelFactory }

        setContent {
            PersonaTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { PersonaTopBar(viewModel = viewModel) }
                ) { innerPadding -> // 明确声明 innerPadding 参数
                    ChatScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding) // 正确使用 innerPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaTopBar(viewModel: ChatViewModel) { // 接收 viewModel
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Persona AI", style = MaterialTheme.typography.titleLarge)
            }
        },
        actions = {
            // LLM 切换菜单
            Box {
                Row(modifier = Modifier.clickable { showMenu = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(viewModel.selectedLlm.value.name, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch LLM")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    defaultModels.forEach { llm ->
                        DropdownMenuItem(text = { Text(llm.name) }, onClick = {
                            viewModel.selectLlm(llm)
                            showMenu = false
                        })
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 消息列表
        MessageList(
            messages = viewModel.messages,
            modifier = Modifier.weight(1f)
        )
        // 输入区域
        InputArea(
            onSend = { viewModel.sendMessage(it) }
        )
    }
}

@Composable
fun MessageList(messages: List<Message>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // 当有新消息时自动滚动到底部
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages) { message ->
            MessageItem(message)
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    val isUser = message.sender == Sender.USER
    val horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Avatar(Icons.Filled.SmartToy, MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment =  horizontalAlignment ) {
            // 消息气泡
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = shape,
                shadowElevation = 2.dp,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    when (message.type) {
                        MessageType.TEXT -> {
                            // 使用 Markwon 渲染 Markdown
                            Markdown(content = message.content)
                        }
                        MessageType.IMAGE -> {
                            Column {
                                AsyncImage(
                                    model = message.content,
                                    contentDescription = "AI Generated Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = "AI Generated Art",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = contentColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                        MessageType.AUDIO -> {
                            AudioMessageItem(contentColor)
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Avatar(Icons.Filled.Person, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun Markdown(content: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 1. 创建并记住 Markwon 实例，配置所需插件
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(CoilImagesPlugin.create(context)) // 使用 Coil 加载图片
            .usePlugin(TablePlugin.create(context))      // 支持 GFM 表格
            .build()
    }

    // 2. 使用 AndroidView 包装一个 TextView
    AndroidView(
        // `factory` 创建 View，只执行一次
        factory = { ctx ->
            androidx.appcompat.widget.AppCompatTextView(ctx).apply {
                // 允许链接被点击
                linksClickable = true
            }
        },
        // `update` 在 content 或其他状态改变时执行
        update = { textView ->
            // 3. 使用 Markwon 将 Markdown 文本设置到 TextView上
            markwon.setMarkdown(textView, content)
        },
        modifier = modifier
    )
}
@Composable
fun AudioMessageItem(tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(150.dp)
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = tint)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = tint, modifier = Modifier.weight(1f))
        Text("12''", style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
fun Avatar(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color)
    }
}

@Composable
fun InputArea(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Chat with Persona...") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotBlank()) {
                            onSend(text)
                            text = ""
                            keyboardController?.hide()
                        }
                    }
                )
            )

            FloatingActionButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                        keyboardController?.hide()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
