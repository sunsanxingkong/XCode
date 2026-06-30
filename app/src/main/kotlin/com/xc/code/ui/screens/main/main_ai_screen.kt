package com.xc.code.ui.screens.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xc.code.ui.theme.app_theme_provider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun main_ai_screen(
    on_back: () -> Unit
) {
    val colors = app_theme_provider.colors
    
    var api_url by remember { mutableStateOf("") }
    var api_key by remember { mutableStateOf("") }
    var model_name by remember { mutableStateOf("") }
    var show_api_key by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { padding_values ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding_values)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(35.dp),
                    shape = CircleShape,
                    color = colors.top_button_bg,
                    onClick = on_back
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.top_button_icon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(35.dp))
            }
            Spacer(modifier = Modifier.height(30.dp))
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Text(
                    text = "接入 AI",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.title_large
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "代码智能助手",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.title_highlight
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "配置 OpenAI 兼容 API 以在编辑器中获得 AI 辅助",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = colors.subtitle
                )
            }
            Spacer(modifier = Modifier.height(30.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Text(
                    text = "API 配置",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.title_highlight,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    // API URL
                    OutlinedTextField(
                        value = api_url,
                        onValueChange = { api_url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API 地址", fontSize = 12.sp) },
                        placeholder = { Text("https://api.openai.com/v1", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.dialog_input_border,
                            unfocusedBorderColor = colors.dialog_input_hint.copy(alpha = 0.45f),
                            focusedTextColor = colors.dialog_input_text,
                            unfocusedTextColor = colors.dialog_input_text,
                            cursorColor = colors.dialog_input_border,
                            focusedContainerColor = colors.dialog_input_bg,
                            unfocusedContainerColor = colors.dialog_input_bg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // API Key
                    OutlinedTextField(
                        value = api_key,
                        onValueChange = { api_key = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API 密钥", fontSize = 12.sp) },
                        placeholder = { Text("sk-...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { show_api_key = !show_api_key }) {
                                Icon(
                                    if (show_api_key) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (show_api_key) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.dialog_input_border,
                            unfocusedBorderColor = colors.dialog_input_hint.copy(alpha = 0.45f),
                            focusedTextColor = colors.dialog_input_text,
                            unfocusedTextColor = colors.dialog_input_text,
                            cursorColor = colors.dialog_input_border,
                            focusedContainerColor = colors.dialog_input_bg,
                            unfocusedContainerColor = colors.dialog_input_bg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Model Name
                    OutlinedTextField(
                        value = model_name,
                        onValueChange = { model_name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型名称", fontSize = 12.sp) },
                        placeholder = { Text("gpt-4o / deepseek-chat / qwen-plus", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Android, null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.dialog_input_border,
                            unfocusedBorderColor = colors.dialog_input_hint.copy(alpha = 0.45f),
                            focusedTextColor = colors.dialog_input_text,
                            unfocusedTextColor = colors.dialog_input_text,
                            cursorColor = colors.dialog_input_border,
                            focusedContainerColor = colors.dialog_input_bg,
                            unfocusedContainerColor = colors.dialog_input_bg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            // Presets Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Text(
                    text = "快速选择",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.title_highlight,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    val presets = listOf(
                        Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o"),
                        Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
                        Triple("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
                        Triple("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus"),
                        Triple("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
                        Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
                        Triple("Claude (OpenAI 兼容)", "https://api.openai.com/v1", "claude-3-haiku-20240307"),
                        Triple("本地 Ollama", "http://localhost:11434/v1", "qwen2.5-coder:7b")
                    )
                    presets.forEachIndexed { index, (name, url, model) ->
                        val isTop = index == 0
                        val isBottom = index == presets.lastIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    api_url = url
                                    model_name = model
                                },
                            shape = when {
                                isTop && isBottom -> RoundedCornerShape(12.dp)
                                isTop -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                                isBottom -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                                else -> RoundedCornerShape(0.dp)
                            },
                            color = colors.card_bg
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = colors.card_icon_bg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.card_text_title
                                    )
                                    Text(
                                        text = model,
                                        fontSize = 10.sp,
                                        color = colors.card_text_subtitle
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.card_chevron,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (!isBottom) {
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            // Save Button
            Button(
                onClick = {
                    // Save to SharedPreferences
                    val prefs = ctx.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("api_url", api_url.trim())
                        .putString("api_key", api_key.trim())
                        .putString("model_name", model_name.trim())
                        .apply()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.dialog_clone_bg,
                    contentColor = colors.dialog_clone_text
                )
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存配置", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
