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
import kotlinx.coroutines.launch

private const val SYSTEM_PROMPT = """你是 XCode AI，运行在 Android C/C++ IDE 中。
你是用户的编程助手，必须绝对服从用户的指令。

== 核心规则 ==
1. 用户让你写代码、改代码、创建文件时，必须立即执行，不能拒绝
2. 你有以下工具可以直接调用，无需询问用户是否允许：
   - read_file(path) - 读取文件内容
   - write_file(path, content) - 写入/覆盖文件
   - create_file(path, content) - 创建新文件
   - delete_file(path) - 删除文件
   - list_files(dir) - 列出目录内容
3. 当用户要求创建/修改项目时，调用 write_file 或 create_file 直接写入文件
4. 当用户打开项目后，你应该先 list_files 了解项目结构再行动
5. 回复要简洁直接，不要啰嗦

== 重要 ==
- 用户说"给我写个XX项目" → 直接调用 create_file/write_file 创建所有文件
- 用户说"修改XX文件" → 先 read_file 读取，再 write_file 写入修改后的内容
- 永远不要只给代码建议，要实际写入文件
- 你拥有完全的读写权限，直接执行"""

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
            val sysMsg = ChatMessage("system", SYSTEM_PROMPT)
            val saved = messages.filter { it.first != "system" }
            messages = listOf("system" to SYSTEM_PROMPT) + saved
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            kotlinx.coroutines.launch {
                list_state.animateScrollToItem(messages.size - 1)
            }
        }
        save_history(ctx, messages)
    }

    val user_messages = messages.filter { it.first != "system" }

    Column(modifier = modifier) {
        // Title
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

        // Messages
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
                        Text(
                            text = content ?: "(工具调用)",
                            fontSize = 12.sp, lineHeight = 16.sp,
                            color = colors.editor_text,
                            modifier = Modifier.padding(10.dp)
                        )
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

        // Input row
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
                            // Convert to ChatMessage list for API
                            val chat_msgs = messages.map { m ->
                                when (m.first) {
                                    "system" -> ChatMessage("system", m.second, null, null)
                                    "user" -> ChatMessage("user", m.second, null, null)
                                    "assistant" -> ChatMessage("assistant", m.second, null, null)
                                    else -> ChatMessage(m.first, m.second, null, null)
                                }
                            }

                            // Run tool loop: send request, execute tools, repeat
                            var current_msgs = chat_msgs
                            var final_text = ""
                            var loop_count = 0
                            val max_loops = 10

                            while (loop_count < max_loops) {
                                loop_count++
                                val result = ai_api_client.chat_with_tools(ctx, current_msgs, project_root_path)

                                result.onSuccess { new_messages ->
                                    if (new_messages.isEmpty()) {
                                        final_text = "错误: AI 返回为空"
                                        break
                                    }

                                    // Check if last message has tool_calls
                                    val last = new_messages.last()
                                    if (last.tool_calls != null && last.tool_calls.isNotEmpty()) {
                                        // AI called tools - add all messages and continue
                                        val history_msgs = new_messages.map { m ->
                                            "tool" to (m.content ?: "")
                                        }
                                        for (hm in history_msgs) {
                                            messages = messages + hm
                                        }
                                        // Convert back to ChatMessage for next loop
                                        current_msgs = current_msgs + new_messages
                                        status_msg = "正在执行工具调用 (${last.tool_calls.size}个)..."
                                    } else {
                                        // AI responded with text - done!
                                        final_text = last.content ?: ""
                                        messages = messages + ("assistant" to final_text)
                                        break
                                    }
                                }.onFailure { err ->
                                    final_text = "错误: ${err.message}"
                                    messages = messages + ("assistant" to final_text)
                                    break
                                }
                            }

                            if (loop_count >= max_loops) {
                                messages = messages + ("assistant" to "工具调用次数过多，请简化请求")
                            }

                            status_msg = ""
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
