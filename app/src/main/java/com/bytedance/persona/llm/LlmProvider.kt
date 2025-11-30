package com.bytedance.persona.llm

import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import java.io.IOException

// 1. 定义大语言模型 (LLM) 的数据结构
sealed class Llm(val name: String, val apiKey: String) {
    // Gemini 模型，使用其专用 SDK
    data object Gemini : Llm("Gemini", "YOUR_GEMINI_API_KEY")

    // 为所有与 OpenAI API 兼容的模型定义一个抽象基类
    abstract class OpenAiCompatible(name: String, apiKey: String) : Llm(name, apiKey) {
        abstract val baseUrl: String
        abstract val modelId: String
    }

    data object OpenAI : OpenAiCompatible("OpenAI", "YOUR_OPENAI_API_KEY") {
        override val baseUrl = "https://api.openai.com/v1/"
        override val modelId = "gpt-3.5-turbo"
    }

    data object DeepSeek : OpenAiCompatible("DeepSeek", "YOUR_OPENAI_API_KEY") {
        override val baseUrl = "https://api.deepseek.com/v1/"
        override val modelId = "deepseek-chat"
    }

    class Custom(
        name: String,
        apiKey: String,
        override val baseUrl: String,
        override val modelId: String
    ) : OpenAiCompatible(name, apiKey)
}

// 预设模型列表
val defaultModels = listOf(Llm.Gemini, Llm.OpenAI, Llm.DeepSeek)

// --- API 服务定义 ---

// OpenAI 聊天请求模型
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true
)

data class OpenAiMessage(val role: String, val content: String)

// OpenAI 流式响应模型
data class OpenAiStreamChunk(
    val choices: List<Choice>?
) {
    data class Choice(val delta: Delta?)
    data class Delta(val content: String?)
}

// 用于 OpenAI 兼容 API 的 Retrofit 接口
interface OpenAiApiService {
    @POST("chat/completions")
    @Streaming
    fun streamChatCompletions(
        @Body request: OpenAiChatRequest,
        @Header("Authorization") token: String
    ): Call<ResponseBody>
}

// --- LLM Provider 实现 ---

interface LlmProvider {
    fun generateResponse(prompt: String, model: Llm): Flow<String>
}

class DefaultLlmProvider : LlmProvider {

    // 为每个 baseUrl 缓存 Retrofit 客户端
    private val apiClients = mutableMapOf<String, OpenAiApiService>()
    private val gson = Gson()

    private fun getOpenAiApiClient(baseUrl: String): OpenAiApiService {
        return apiClients.getOrPut(baseUrl) {
            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
            val client = OkHttpClient.Builder().addInterceptor(logging).build()
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiApiService::class.java)
        }
    }

    override fun generateResponse(prompt: String, model: Llm): Flow<String> {
        return when (model) {
            is Llm.Gemini -> {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-pro", // Gemini 模型 ID
                    apiKey = model.apiKey
                )
                generativeModel.generateContentStream(prompt).map { it.text ?: "" }.flowOn(Dispatchers.IO)
            }
            // 所有 OpenAI 兼容的 API 都通过这里处理
            is Llm.OpenAiCompatible -> {
                generateOpenAiResponse(prompt, model)
            }
        }
    }

    private fun generateOpenAiResponse(prompt: String, model: Llm.OpenAiCompatible): Flow<String> = flow {
        val apiService = getOpenAiApiClient(model.baseUrl)
        val request = OpenAiChatRequest(
            model = model.modelId,
            messages = listOf(OpenAiMessage(role = "user", content = prompt))
        )

        try {
            val response = apiService.streamChatCompletions(request, "Bearer ${model.apiKey}").execute()

            if (!response.isSuccessful) {
                throw IOException("API call failed: ${response.code()} ${response.errorBody()?.string()}")
            }

            val inputStream = response.body()?.byteStream() ?: throw IOException("Response body is null")

            inputStream.bufferedReader().useLines { lines ->
                lines
                    .map { it.removePrefix("data:").trim() }
                    .filter { it.isNotEmpty() && it != "[DONE]" }
                    .forEach { line ->
                        try {
                            val chunk = gson.fromJson(line, OpenAiStreamChunk::class.java)
                            chunk.choices?.firstOrNull()?.delta?.content?.let { emit(it) }
                        } catch (e: Exception) {
                            // 忽略解析错误，继续处理下一行
                            System.err.println("SSE parsing error for line: $line")
                        }
                    }
            }
        } catch (e: Exception) {
            // 向上抛出异常，由 ViewModel 捕获并显示错误信息
            throw IOException("LLM request failed: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO) // <-- 在这里切换到 IO 线程执行网络请求！
}