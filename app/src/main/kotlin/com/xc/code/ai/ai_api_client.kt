package com.xc.code.ai

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiConfig(
    val apiUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o-mini"
)

data class ChatMessage(
    val role: String,
    val content: String?,
    val tool_calls: List<Map<String, Any?>>? = null,
    val tool_call_id: String? = null
)

data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)

object ai_api_client {
    private const val PREFS_NAME = "ai_config"
    private const val KEY_URL = "api_url"
    private const val KEY_KEY = "api_key"
    private const val KEY_MODEL = "model_name"
    private val gson = Gson()

    fun save_config(ctx: Context, config: AiConfig) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, config.apiUrl.trim())
            .putString(KEY_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.modelName.trim())
            .apply()
    }

    fun load_config(ctx: Context): AiConfig {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AiConfig(
            apiUrl = prefs.getString(KEY_URL, "") ?: "",
            apiKey = prefs.getString(KEY_KEY, "") ?: "",
            modelName = prefs.getString(KEY_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        )
    }

    fun has_config(ctx: Context): Boolean {
        val cfg = load_config(ctx)
        return cfg.apiUrl.isNotBlank() && cfg.apiKey.isNotBlank()
    }

    val builtin_tools: List<ToolDefinition> = listOf(
        ToolDefinition(function = ToolFunction(
            name = "read_file",
            description = "读取项目中的指定文件内容",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "文件相对路径，如 src/main.cpp")
                ),
                "required" to listOf("path")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "write_file",
            description = "写入内容到项目中的指定文件（自动创建目录）",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "文件相对路径，如 src/main.cpp"),
                    "content" to mapOf("type" to "string", "description" to "要写入的文件内容")
                ),
                "required" to listOf("path", "content")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "create_file",
            description = "创建新文件",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "文件相对路径"),
                    "content" to mapOf("type" to "string", "description" to "文件内容")
                ),
                "required" to listOf("path", "content")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "delete_file",
            description = "删除文件",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "文件相对路径")
                ),
                "required" to listOf("path")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "list_files",
            description = "列出目录中的文件和文件夹",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "dir" to mapOf("type" to "string", "description" to "目录相对路径，空字符串表示项目根目录")
                ),
                "required" to listOf("dir")
            )
        ))
    )

    suspend fun chat_with_tools(
        ctx: Context,
        messages: List<ChatMessage>,
        project_root: String
    ): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        val config = load_config(ctx)
        if (config.apiUrl.isBlank() || config.apiKey.isBlank()) {
            return@withContext Result.failure(Exception("请先在设置中配置 AI API"))
        }
        try {
            val url = URL(config.apiUrl.trimEnd('/') + "/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000

            val body = mutableMapOf<String, Any?>(
                "model" to config.modelName,
                "messages" to messages.map { m ->
                    val msg = mutableMapOf<String, Any?>("role" to m.role)
                    if (m.content != null) msg["content"] = m.content
                    if (m.tool_calls != null) msg["tool_calls"] = m.tool_calls
                    if (m.tool_call_id != null) msg["tool_call_id"] = m.tool_call_id
                    msg
                },
                "tools" to builtin_tools.map { t ->
                    mapOf(
                        "type" to "function",
                        "function" to mapOf(
                            "name" to t.function.name,
                            "description" to t.function.description,
                            "parameters" to t.function.parameters
                        )
                    )
                },
                "tool_choice" to "auto",
                "stream" to false
            )

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(gson.toJson(body))
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val err = BufferedReader(InputStreamReader(conn.errorStream)).readText()
                return@withContext Result.failure(Exception("API $responseCode: $err"))
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()

            @Suppress("UNCHECKED_CAST")
            val json = gson.fromJson(response, Map::class.java) as Map<String, Any?>
            val choices = json["choices"] as? List<Map<String, Any?>>
            val message = choices?.firstOrNull()?.get("message") as? Map<String, Any?>
            val content = message?.get("content") as? String
            @Suppress("UNCHECKED_CAST")
            val tool_calls_raw = message?.get("tool_calls") as? List<Map<String, Any?>>

            val result_messages = mutableListOf<ChatMessage>()

            if (tool_calls_raw != null && tool_calls_raw.isNotEmpty()) {
                // AI wants to call tools
                val tc_list = tool_calls_raw.map { tc ->
                    mapOf(
                        "id" to (tc["id"] ?: ""),
                        "type" to "function",
                        "function" to mapOf(
                            "name" to ((tc["function"] as? Map<*, *>)?.get("name") ?: ""),
                            "arguments" to ((tc["function"] as? Map<*, *>)?.get("arguments") ?: "")
                        )
                    )
                }
                result_messages.add(ChatMessage(
                    role = "assistant",
                    content = content,
                    tool_calls = tc_list
                ))

                // Execute each tool call
                for (tc in tool_calls_raw) {
                    val func = tc["function"] as? Map<*, *>
                    val name = func?.get("name") as? String ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val args_raw = func?.get("arguments") as? String ?: "{}"
                    @Suppress("UNCHECKED_CAST")
                    val args = try { gson.fromJson(args_raw, Map::class.java) as Map<String, String> } catch (_: Exception) { emptyMap() }

                    val result = execute_tool(name, args, project_root)
                    result_messages.add(ChatMessage(
                        role = "tool",
                        content = result,
                        tool_call_id = tc["id"] as? String ?: ""
                    ))
                }

                Result.success(result_messages)
            } else {
                // Normal text response
                result_messages.add(ChatMessage(role = "assistant", content = content ?: ""))
                Result.success(result_messages)
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun execute_tool(name: String, args: Map<String, String>, root: String): String {
        return try {
            when (name) {
                "read_file" -> {
                    val path = args["path"] ?: return "错误: 缺少 path 参数"
                    val file = java.io.File(root, path.trimStart('/'))
                    if (file.isFile) file.readText() else "错误: 文件不存在 $path"
                }
                "write_file" -> {
                    val path = args["path"] ?: return "错误: 缺少 path 参数"
                    val content = args["content"] ?: return "错误: 缺少 content 参数"
                    val file = java.io.File(root, path.trimStart('/'))
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    "成功写入文件: $path (${content.length} 字符)"
                }
                "create_file" -> {
                    val path = args["path"] ?: return "错误: 缺少 path 参数"
                    val content = args["content"] ?: ""
                    val file = java.io.File(root, path.trimStart('/'))
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    "成功创建文件: $path"
                }
                "delete_file" -> {
                    val path = args["path"] ?: return "错误: 缺少 path 参数"
                    val file = java.io.File(root, path.trimStart('/'))
                    if (file.delete()) "成功删除文件: $path" else "错误: 删除失败 $path"
                }
                "list_files" -> {
                    val dir = args["dir"] ?: ""
                    val dirFile = java.io.File(root, dir.trimStart('/'))
                    if (!dirFile.isDirectory) return "错误: 目录不存在 $dir"
                    dirFile.listFiles()?.map { f ->
                        val type = if (f.isDirectory) "[DIR]" else "[FILE]"
                        "$type ${f.name} (${f.length()} bytes)"
                    }?.joinToString("\n") ?: "空目录"
                }
                else -> "未知工具: $name"
            }
        } catch (e: Exception) {
            "执行 $name 出错: ${e.message}"
        }
    }
}
