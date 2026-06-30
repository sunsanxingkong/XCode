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
                    "path" to mapOf("type" to "string", "description" to "文件路径，相对于项目根目录")
                ),
                "required" to listOf("path")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "write_file",
            description = "写入内容到项目中的指定文件（自动创建父目录）",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "文件路径，相对于项目根目录"),
                    "content" to mapOf("type" to "string", "description" to "要写入的文件内容")
                ),
                "required" to listOf("path", "content")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "create_file",
            description = "创建新文件并写入初始内容",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "文件路径，相对于项目根目录"),
                    "content" to mapOf("type" to "string", "description" to "文件初始内容")
                ),
                "required" to listOf("path", "content")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "delete_file",
            description = "删除指定的文件",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "文件路径，相对于项目根目录")
                ),
                "required" to listOf("path")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "list_files",
            description = "列出指定目录中的文件和文件夹",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "dir" to mapOf("type" to "string", "description" to "目录路径，相对于项目根目录；空字符串表示项目根目录")
                ),
                "required" to listOf("dir")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "search_files",
            description = "在项目中按文件名模式搜索文件，支持通配符如 *.kt、*.cpp、main*",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "pattern" to mapOf("type" to "string", "description" to "搜索模式，如 *.kt、main.cpp、*test*")
                ),
                "required" to listOf("pattern")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "grep_files",
            description = "在项目文件中搜索文本内容，可按文件扩展名过滤",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "pattern" to mapOf("type" to "string", "description" to "要搜索的文本或正则表达式"),
                    "extension" to mapOf("type" to "string", "description" to "可选，文件扩展名过滤，如 .kt、.cpp、.h，不传则搜索所有文件")
                ),
                "required" to listOf("pattern")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "rename_file",
            description = "重命名或移动文件到新位置",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "old_path" to mapOf("type" to "string", "description" to "原文件路径，相对于项目根目录"),
                    "new_path" to mapOf("type" to "string", "description" to "新文件路径，相对于项目根目录")
                ),
                "required" to listOf("old_path", "new_path")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "get_project_info",
            description = "获取项目概览信息：总文件数、目录结构、各类型文件统计",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf<String, Any?>(
                    "deep" to mapOf("type" to "boolean", "description" to "可选，是否深度扫描（默认true），false只统计根目录")
                ),
                "required" to listOf()
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "run_command",
            description = "在项目根目录执行终端命令（如编译、运行、git操作等）",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "command" to mapOf("type" to "string", "description" to "要执行的终端命令")
                ),
                "required" to listOf("command")
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


    private fun execute_tool(name: String, args: Map<String, String>, root: String): String {
        return try {
            when (name) {        } catch (e: Exception) {
            Result.failure(e)
        }
    }


                "search_files" -> {
                    val pattern = args["pattern"] ?: return "错误: 缺少 pattern 参数"
                    val rootDir = java.io.File(root)
                    val results = mutableListOf<String>()
                    rootDir.walkTopDown().maxDepth(10).forEach { f ->
                        val relPath = f.relativeTo(rootDir).path
                        if (f.isFile && relPath.contains(pattern.replace("*", "").replace("?", ""), ignoreCase = true)) {
                            results.add(relPath)
                        }
                    }
                    if (results.isEmpty()) "未找到匹配的文件"
                    else results.joinToString("
")
                }
                "grep_files" -> {
                    val pattern = args["pattern"] ?: return "错误: 缺少 pattern 参数"
                    val ext = args["extension"] ?: ""
                    val rootDir = java.io.File(root)
                    val results = mutableListOf<String>()
                    rootDir.walkTopDown().maxDepth(10).forEach { f ->
                        if (f.isFile && (ext.isBlank() || f.name.endsWith(ext))) {
                            try {
                                val content = f.readText()
                                if (content.contains(pattern, ignoreCase = true)) {
                                    val relPath = f.relativeTo(rootDir).path
                                    val lines = content.lines()
                                    val matchingLines = lines.filterIndexed { i, l -> l.contains(pattern, ignoreCase = true) }
                                    results.add("$relPath: ${matchingLines.size} 行匹配")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    if (results.isEmpty()) "未找到匹配的内容"
                    else results.joinToString("
")
                }
                "rename_file" -> {
                    val oldPath = args["old_path"] ?: return "错误: 缺少 old_path 参数"
                    val newPath = args["new_path"] ?: return "错误: 缺少 new_path 参数"
                    val oldFile = java.io.File(root, oldPath.trimStart('/'))
                    val newFile = java.io.File(root, newPath.trimStart('/'))
                    if (!oldFile.exists()) return "错误: 源文件不存在 $oldPath"
                    newFile.parentFile?.mkdirs()
                    if (oldFile.renameTo(newFile)) "成功重命名: $oldPath -> $newPath"
                    else "错误: 重命名失败"
                }
                "get_project_info" -> {
                    val rootDir = java.io.File(root)
                    if (!rootDir.isDirectory) return "错误: 项目目录不存在"
                    val allFiles = rootDir.walkTopDown().maxDepth(15).filter { it.isFile }.toList()
                    val totalSize = allFiles.sumOf { it.length() }
                    val extensions = allFiles.groupBy { it.extension.ifEmpty { "(无扩展名)" } }.mapValues { it.value.size }
                    val dirCount = rootDir.walkTopDown().maxDepth(15).filter { it.isDirectory }.count()
                    val topDirs = rootDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                    buildString {
                        appendLine("📁 项目: ${rootDir.name}")
                        appendLine("📂 路径: $root")
                        appendLine("📄 文件总数: ${allFiles.size}")
                        appendLine("📏 总大小: ${totalSize / 1024} KB")
                        appendLine("📁 目录数: $dirCount")
                        appendLine("")
                        appendLine("📊 文件类型分布:")
                        extensions.entries.sortedByDescending { it.value }.take(15).forEach { (ext, count) ->
                            appendLine("  .$ext: $count 个")
                        }
                        appendLine("")
                        appendLine("📂 顶层目录:")
                        topDirs.forEach { appendLine("  📁 $it") }
                    }
                }
                "run_command" -> {
                    val cmd = args["command"] ?: return "错误: 缺少 command 参数"
                    try {
                        val proc = ProcessBuilder()
                            .directory(java.io.File(root))
                            .command("sh", "-c", cmd)
                            .redirectErrorStream(true)
                            .start()
                        val output = proc.inputStream.bufferedReader().readText()
                        val code = proc.waitFor()
                        if (code == 0) "✅ 命令执行成功 (exit=$code):
$output"
                        else "⚠️ 命令完成但有错误 (exit=$code):
$output"
                    } catch (e: Exception) {
                        "错误: 执行命令失败: ${e.message}"
                    }
                }
                else -> "未知工具: $name"
            }
        } catch (e: Exception) {
            "执行 $name 出错: ${e.message}"
        }
    }
}