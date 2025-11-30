package com.bytedance.persona.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.persona.llm.LlmModel
import com.bytedance.persona.llm.ModelType
import com.bytedance.persona.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    // 用于控制对话框显示的状态，null 表示不显示，非 null 表示显示并编辑对应的模型
    var editingModel by remember { mutableStateOf<LlmModel?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Models") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { isCreatingNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add new model")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.llmModels) { model ->
                ModelListItem(
                    model = model,
                    onEdit = { editingModel = it },
                    onDelete = { viewModel.removeLlm(it) }
                )
            }
        }
    }

    // 对话框：用于编辑模型
    if (editingModel != null) {
        ModelEditorDialog(
            model = editingModel!!,
            onDismiss = { editingModel = null },
            onSave = { updatedModel ->
                viewModel.updateLlm(updatedModel)
                editingModel = null
            }
        )
    }

    // 对话框：用于创建新模型
    if (isCreatingNew) {
        ModelEditorDialog(
            model = null, // 传入 null 表示是创建模式
            onDismiss = { isCreatingNew = false },
            onSave = { newModel ->
                viewModel.addLlm(newModel)
                isCreatingNew = false
            }
        )
    }
}

@Composable
fun ModelListItem(
    model: LlmModel,
    onEdit: (LlmModel) -> Unit,
    onDelete: (LlmModel) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(model.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onEdit(model) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { onDelete(model) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun ModelEditorDialog(
    model: LlmModel?,
    onDismiss: () -> Unit,
    onSave: (LlmModel) -> Unit
) {
    val isNew = model == null
    var name by remember { mutableStateOf(model?.name ?: "") }
    var apiKey by remember { mutableStateOf(model?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(model?.baseUrl ?: "") }
    var modelId by remember { mutableStateOf(model?.modelId ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add New Model" else "Edit Model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Model Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") }
                )
                // Gemini 类型不需要 Base URL 和 Model ID
                if (model?.type != ModelType.GEMINI) {
                     OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") }
                    )
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("Model ID") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedModel = model?.copy(
                    name = name,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    modelId = modelId
                ) ?: LlmModel(
                    name = name,
                    apiKey = apiKey,
                    type = ModelType.OPENAI_COMPATIBLE, // 新增的默认为 OpenAI 兼容类型
                    baseUrl = baseUrl,
                    modelId = modelId
                )
                onSave(updatedModel)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
