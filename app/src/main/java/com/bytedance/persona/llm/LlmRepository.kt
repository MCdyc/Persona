package com.bytedance.persona.llm

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Define the DataStore file name
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_models")

class LlmRepository(private val context: Context) {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private object PrefKeys {
        val MODELS_JSON = stringPreferencesKey("models_json")
    }

    private val _models = mutableStateListOf<LlmModel>()
    val models: List<LlmModel> = _models

    init {
        repositoryScope.launch {
            val storedModels = loadModelsFromDataStore()
            if (storedModels.isEmpty()) {
                // If no models are stored, add default ones and save them
                val defaultModels = getInitialModels()
                _models.addAll(defaultModels)
                saveModels()
            } else {
                _models.addAll(storedModels)
            }
        }
    }

    private suspend fun loadModelsFromDataStore(): List<LlmModel> {
        val json = context.dataStore.data.map { preferences ->
            preferences[PrefKeys.MODELS_JSON]
        }.first()
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        val type = object : TypeToken<List<LlmModel>>() {}.type
        return gson.fromJson(json, type)
    }

    private suspend fun saveModels() {
        val json = gson.toJson(models)
        context.dataStore.edit { preferences ->
            preferences[PrefKeys.MODELS_JSON] = json
        }
    }

    fun addModel(model: LlmModel) {
        _models.add(model)
        repositoryScope.launch {
            saveModels()
        }
    }

    fun removeModel(model: LlmModel) {
        _models.remove(model)
        repositoryScope.launch {
            saveModels()
        }
    }

    fun updateModel(model: LlmModel) {
        val index = _models.indexOfFirst { it.id == model.id }
        if (index != -1) {
            _models[index] = model
            repositoryScope.launch {
                saveModels()
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LlmRepository? = null

        fun getInstance(context: Context): LlmRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlmRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun getInitialModels(): List<LlmModel> {
            return listOf(
                LlmModel(
                    name = "Gemini",
                    apiKey = "YOUR_GEMINI_API_KEY", // Note: Remind user to replace this
                    type = ModelType.GEMINI,
                    modelId = "gemini-pro"
                ),
                LlmModel(
                    name = "OpenAI",
                    apiKey = "YOUR_OPENAI_API_KEY", // Note: Remind user to replace this
                    type = ModelType.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.openai.com/v1/",
                    modelId = "gpt-3.5-turbo"
                ),
                LlmModel(
                    name = "DeepSeek",
                    apiKey = "YOUR_DEEPSEEK_API_KEY", // Note: Remind user to replace this
                    type = ModelType.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.deepseek.com/v1/",
                    modelId = "deepseek-chat"
                )
            )
        }
    }
}
