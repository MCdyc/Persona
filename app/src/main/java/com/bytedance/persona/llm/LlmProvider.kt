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
import java.util.UUID

// 1. 定义更灵活的模型数据结构
enum class ModelType {
    GEMINI,
    OPENAI_COMPATIBLE
}

data class LlmModel(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var apiKey: String,
    val type: ModelType,
    // baseUrl 和 modelId 对于 OpenAI 兼容模型是必需的
    var baseUrl: String? = null,
    var modelId: String? = null
)

// --- API 服务定义 (与之前相同) ---
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true
)
data class OpenAiMessage(val role: String, val content: String)
data class OpenAiStreamChunk(val choices: List<Choice>?) {
    data class Choice(val delta: Delta?)
    data class Delta(val content: String?)
}

interface OpenAiApiService {
    @POST("chat/completions")
    @Streaming
    fun streamChatCompletions(
        @Body request: OpenAiChatRequest,
        @Header("Authorization") token: String
    ): Call<ResponseBody>
}


// --- LLM Provider 实现 (更新后) ---
interface LlmProvider {
    fun generateResponse(prompt: String, model: LlmModel): Flow<String>
}

class DefaultLlmProvider : LlmProvider {

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

    override fun generateResponse(prompt: String, model: LlmModel): Flow<String> {
        return when (model.type) {
            ModelType.GEMINI -> {
                // 确保 Gemini 有 modelId
                val modelId = model.modelId ?: "gemini-pro"
                val generativeModel = GenerativeModel(modelName = modelId, apiKey = model.apiKey)
                generativeModel.generateContentStream(prompt)
                    .map { it.text ?: "" }
                    .flowOn(Dispatchers.IO)
            }
            ModelType.OPENAI_COMPATIBLE -> {
                // 确保 baseUrl 和 modelId 不为空
                val baseUrl = model.baseUrl
                val modelId = model.modelId
                requireNotNull(baseUrl) { "Base URL must not be null for OpenAI compatible models" }
                requireNotNull(modelId) { "Model ID must not be null for OpenAI compatible models" }
                generateOpenAiResponse(prompt, model.apiKey, baseUrl, modelId)
            }
        }
    }

    private fun generateOpenAiResponse(
        prompt: String,
        apiKey: String,
        baseUrl: String,
        modelId: String
    ): Flow<String> = flow {
        val apiService = getOpenAiApiClient(baseUrl)
        val request = OpenAiChatRequest(
            model = modelId,
            messages = listOf(OpenAiMessage(role = "user", content = prompt))
        )

        try {
            val response = apiService.streamChatCompletions(request, "Bearer $apiKey").execute()

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
                        } catch (_: Exception) {
                            System.err.println("SSE parsing error for line: $line")
                        }
                    }
            }
        } catch (e: Exception) {
            throw IOException("LLM request failed: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO)
}