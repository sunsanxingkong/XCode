package com.xc.code.ui.screens.editor

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xc.code.ai.ChatMessage
import com.xc.code.ai.ai_api_client
import com.xc.code.ui.theme.app_theme_provider
import kotlinx.coroutines.launch

@Composable
fun editor_ai_panel(
    modifier: Modifier = Modifier
) {
    val colors = app_theme_provider.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var messages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var input_text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val list_state = rememberLazyListState()
    val has_config = remember { ai_api_client.has_config(ctx) }
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            list_state.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(modifier = modifier) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.AutoAwesome, null, tint = colors.editor_icon, modifier = Modifier.size(18.dp))
            Text("AI 助手", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.editor_text)
            if (has_config) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
            } else {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFFFA726), modifier = Modifier.size(14.dp))
            }
        }
        
        HorizontalDivider(color = colors.editor_divider, thickness = 0.5.dp)
        
        // Messages
        LazyColumn(
            state = list_state,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.AutoAwesome, null, tint = colors.editor_hint, modifier = Modifier.size(36.dp))
                            Text("AI 代码助手", fontSize = 14.sp, color = colors.editor_text, fontWeight = FontWeight.Bold)
                            Text(
                                if (has_config) "输入问题开始对话" else "请先在设置中配置 AI API",
                                fontSize = 11.sp,
                                color = colors.editor_hint
                            )
                            Text(
                                "支持：代码生成、解释、优化、调试",
                                fontSize = 10.sp,
                                color = colors.editor_hint.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            items(messages) { (role, content) ->
                val is_user = role == "user"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (is_user) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 280.dp),
                        shape = RoundedCornerShape(
                            topStart = 12.dp, topEnd = 12.dp,
                            bottomStart = if (is_user) 12.dp else 4.dp,
                            bottomEnd = if (is_user) 4.dp else 12.dp
                        ),
                        color = if (is_user) colors.editor_button_bg else colors.card_bg
                    ) {
                        Text(
                            text = content,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = colors.editor_text,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
            if (loading) {
                item {
                    Surface(
                        modifier = Modifier.widthIn(max = 200.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.card_bg
                    ) {
                        Text(
                            text = "思考中...",
                            fontSize = 12.sp,
                            color = colors.editor_hint,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
        
        // Input
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = input_text,
                onValueChange = { input_text = it },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 80.dp),
                placeholder = { Text("输入问题...", fontSize = 12.sp, color = colors.editor_hint) },
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = colors.editor_text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.editor_divider,
                    unfocusedBorderColor = colors.editor_divider.copy(alpha = 0.5f),
                    cursorColor = colors.editor_icon,
                    focusedContainerColor = colors.editor_button_bg,
                    unfocusedContainerColor = colors.editor_button_bg
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = false,
                maxLines = 3
            )
            IconButton(
                onClick = {
                    val text = input_text.trim()
                    if (text.isNotBlank() && !loading) {
                        input_text = ""
                        messages = messages + ("user" to text)
                        loading = true
                        scope.launch {
                            val chat_messages = messages.map { ChatMessage(it.first, it.second) }
                            val result = ai_api_client.chat_completion(ctx, chat_messages)
                            result.onSuccess { reply ->
                                messages = messages + ("assistant" to reply)
                            }.onFailure { err ->
                                messages = messages + ("assistant" to "❌ 错误: ${err.message}")
                            }
                            loading = false
                        }
                    }
                },
                enabled = input_text.isNotBlank() && !loading,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = colors.editor_button_bg
                )
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp), tint = colors.editor_icon)
            }
        }
    }
}
