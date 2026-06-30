package com.xc.code.ui.screens.editor

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xc.code.ai.ChatMessage
import com.xc.code.ai.ai_api_client
import com.xc.code.ui.theme.app_theme_provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SYSTEM_PROMPT = "你是 XCode AI，一款 Android C/C++ IDE 中的智能编程助手。\n" +
    "核心能力：\n" +
    "1. 生成、解释、优化和调试 C/C++ 代码（也支持 Kotlin/Java/Python 等）\n" +
    "2. 可以直接读取和修改项目文件\n" +
    "3. 每次生成代码时，在代码块上方用注释标明目标文件路径\n" +
    "4. 如果需要先看文件内容再修改，请回复: read_file(\"相对路径\")\n" +
    "5. 自动帮用户完成项目代码编写任务\n\n" +
    "风格：直接给出完整可运行的代码，解释关键逻辑，遇到错误分析原因"

private const val PREFS_HISTORY = "ai_chat_history"
private const val KEY_MESSAGES = "saved_messages"
private val gson = Gson()

@Composable
fun editor_ai_panel(
    project_root_path: String = "",
    modifier: Modifier = Modifier
) {
    val colors = app_theme_provider.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var messages by remember { mutableStateOf(load_history(ctx)) }
    var input_text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var clear_confirm by remember { mutableStateOf(false) }
    val list_state = rememberLazyListState()
    val has_config = remember { ai_api_client.has_config(ctx) }
    var status_msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (messages.isEmpty() || messages.first().first != "system") {
            messages = listOf("system" to SYSTEM_PROMPT) + messages.filter { it.first != "system" }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            list_state.animateScrollToItem(messages.size - 1)
        }
        save_history(ctx, messages)
    }

    val user_messages = messages.filter { it.first != "system" }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = colors.editor_icon, modifier = Modifier.size(18.dp))
            Text("AI 助手", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.editor_text)
            if (has_config) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
            } else {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFFFA726), modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { clear_confirm = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, null, tint = colors.editor_hint, modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider(color = colors.editor_divider, thickness = 0.5.dp)

        if (clear_confirm) {
            AlertDialog(
                onDismissRequest = { clear_confirm = false },
                title = { Text("清空历史") },
                text = { Text("确定清空所有对话历史？") },
                confirmButton = { TextButton(onClick = {
                    messages = listOf("system" to SYSTEM_PROMPT)
                    save_history(ctx, messages)
                    clear_confirm = false
                }) { Text("确定") } },
                dismissButton = { TextButton(onClick = { clear_confirm = false }) { Text("取消") } }
            )
        }

        if (status_msg.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFF1B5E20).copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(text = status_msg, fontSize = 11.sp, color = Color(0xFF81C784), modifier = Modifier.padding(8.dp))
            }
        }

        LazyColumn(
            state = list_state,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (user_messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = colors.editor_hint, modifier = Modifier.size(36.dp))
                            Text("AI 代码助手", fontSize = 14.sp, color = colors.editor_text, fontWeight = FontWeight.Bold)
                            Text(
                                if (has_config) "输入问题开始对话" else "请先在设置中配置 AI API",
                                fontSize = 11.sp, color = colors.editor_hint
                            )
                            Text("支持：代码生成、文件读写、解释、优化、调试", fontSize = 10.sp, color = colors.editor_hint.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            items(user_messages) { (role, content) ->
                val is_user = role == "user"
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (is_user) Alignment.End else Alignment.Start) {
                    Surface(
                        modifier = Modifier.widthIn(max = 280.dp),
                        shape = RoundedCornerShape(12.dp, 12.dp, if (is_user) 12.dp else 4.dp, if (is_user) 4.dp else 12.dp),
                        color = if (is_user) colors.editor_button_bg else colors.card_bg
                    ) {
                        Text(text = content, fontSize = 12.sp, lineHeight = 16.sp, color = colors.editor_text, modifier = Modifier.padding(10.dp))
                    }
                    if (!is_user && project_root_path.isNotBlank()) {
                        val fp = extract_file_path(content)
                        val cd = extract_code(content)
                        if (fp != null && cd != null) {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        write_file(project_root_path, fp, cd)
                                        status_msg = "已写入: $fp"
                                    } catch (e: Exception) { status_msg = "写入失败: ${e.message}" }
                                }
                            }, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("写入 $fp", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Surface(modifier = Modifier.widthIn(max = 200.dp), shape = RoundedCornerShape(12.dp), color = colors.card_bg) {
                        Text("思考中...", fontSize = 12.sp, color = colors.editor_hint, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input_text, onValueChange = { input_text = it },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 80.dp),
                placeholder = { Text("输入问题...", fontSize = 12.sp, color = colors.editor_hint) },
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = colors.editor_text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.editor_divider, unfocusedBorderColor = colors.editor_divider.copy(alpha = 0.5f),
                    cursorColor = colors.editor_icon, focusedContainerColor = colors.editor_button_bg, unfocusedContainerColor = colors.editor_button_bg
                ),
                shape = RoundedCornerShape(10.dp), singleLine = false, maxLines = 3
            )
            IconButton(
                onClick = {
                    val text = input_text.trim()
                    if (text.isNotBlank() && !loading) {
                        input_text = ""
                        val project_context = if (project_root_path.isNotBlank()) "\n\n[项目路径: $project_root_path]" else ""
                        messages = messages + ("user" to text + project_context)
                        loading = true
                        status_msg = ""
                        scope.launch {
                            val msgs = messages.map { ChatMessage(it.first, it.second) }
                            val result = ai_api_client.chat_completion(ctx, msgs)
                            result.onSuccess { reply ->
                                val read_target = extract_read_path(reply)
                                if (read_target != null && project_root_path.isNotBlank()) {
                                    val file_content = read_file_content(project_root_path, read_target)
                                    messages = messages + ("assistant" to reply)
                                    messages = messages + ("user" to "[文件 $read_target 内容]:\n$file_content\n\n请根据需要修改")
                                    val msgs2 = messages.map { ChatMessage(it.first, it.second) }
                                    val r2 = ai_api_client.chat_completion(ctx, msgs2)
                                    r2.onSuccess { r -> messages = messages + ("assistant" to r) }
                                        .onFailure { e -> messages = messages + ("assistant" to "错误: ${e.message}") }
                                } else {
                                    messages = messages + ("assistant" to reply)
                                }
                            }.onFailure { err ->
                                messages = messages + ("assistant" to "错误: ${err.message}")
                            }
                            loading = false
                        }
                    }
                },
                enabled = input_text.isNotBlank() && !loading,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = colors.editor_button_bg)
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp), tint = colors.editor_icon)
            }
        }
    }
}

private fun extract_file_path(text: String): String? {
    Regex("""//\s*File:\s*(\S+)""", RegexOption.IGNORE_CASE).find(text)?.let { return it.groupValues[1] }
    Regex("""\*\*File:\*\*\s*`?(\S+)`?""", RegexOption.IGNORE_CASE).find(text)?.let { return it.groupValues[1] }
    return null
}

private fun extract_code(text: String): String? {
    return Regex("""```(?:\w+)?
(.+?)
```""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)).find(text)?.groupValues?.get(1)?.trim()
}

private fun extract_read_path(text: String): String? {
    return Regex("""read_file\s*\(?\s*['"](.+?)['"]\s*\)?""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
}

private fun read_file_content(root: String, path: String): String {
    return try {
        val f = File(root, path.trimStart('/'))
        if (f.isFile) f.readText() else "文件不存在: $path"
    } catch (e: Exception) { "读取失败: ${e.message}" }
}

private suspend fun write_file(root: String, path: String, content: String) = withContext(Dispatchers.IO) {
    val f = File(root, path.trimStart('/'))
    f.parentFile?.mkdirs()
    f.writeText(content)
}

private fun save_history(ctx: Context, messages: List<Pair<String, String>>) {
    try {
        val save = messages.filter { it.first != "system" }.takeLast(50)
        val json = gson.toJson(save)
        ctx.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE).edit().putString(KEY_MESSAGES, json).apply()
    } catch (_: Exception) {}
}

private fun load_history(ctx: Context): List<Pair<String, String>> {
    return try {
        val json = ctx.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE).getString(KEY_MESSAGES, null)
        if (json != null) {
            val type = object : TypeToken<List<Pair<String, String>>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else emptyList()
    } catch (_: Exception) { emptyList() }
}
