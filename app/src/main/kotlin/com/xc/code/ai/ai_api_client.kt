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
                "properties" to mapOf(
                    "deep" to mapOf("type" to "boolean", "description" to "可选，是否深度扫描（默认true），false只统计根目录")
                ),
                "required" to emptyList<String>()
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
                    val path = args["path"] ?: return "\u9519\u8bef: \u7f3a\u5c11 path \u53c2\u6570"
                    val file = java.io.File(root, path.trimStart('/'))
                    if (file.isFile) file.readText() else "\u9519\u8bef: \u6587\u4ef6\u4e0d\u5b58\u5728 $path"
                }
                "write_file" -> {
                    val path = args["path"] ?: return "\u9519\u8bef: \u7f3a\u5c11 path \u53c2\u6570"
                    val text = args["content"] ?: return "\u9519\u8bef: \u7f3a\u5c11 content \u53c2\u6570"
                    val file = java.io.File(root, path.trimStart('/'))
                    file.parentFile?.mkdirs()
                    file.writeText(text)
                    "\u6210\u529f\u5199\u5165\u6587\u4ef6: $path (${text.length} \u5b57\u7b26)"
                }
                "create_file" -> {
                    val path = args["path"] ?: return "\u9519\u8bef: \u7f3a\u5c11 path \u53c2\u6570"
                    val text = args["content"] ?: ""
                    val file = java.io.File(root, path.trimStart('/'))
                    file.parentFile?.mkdirs()
                    file.writeText(text)
                    "\u6210\u529f\u521b\u5efa\u6587\u4ef6: $path"
                }
                "delete_file" -> {
                    val path = args["path"] ?: return "\u9519\u8bef: \u7f3a\u5c11 path \u53c2\u6570"
                    val file = java.io.File(root, path.trimStart('/'))
                    if (file.delete()) "\u6210\u529f\u5220\u9664\u6587\u4ef6: $path" else "\u9519\u8bef: \u5220\u9664\u5931\u8d25 $path"
                }
                "list_files" -> {
                    val dir = args["dir"] ?: ""
                    val dirFile = java.io.File(root, dir.trimStart('/'))
                    if (!dirFile.isDirectory) return "\u9519\u8bef: \u76ee\u5f55\u4e0d\u5b58\u5728 $dir"
                    dirFile.listFiles()?.map { f ->
                        val type = if (f.isDirectory) "[DIR]" else "[FILE]"
                        "$type ${f.name} (${f.length()} bytes)"
                    }?.joinToString("\n") ?: "\u7a7a\u76ee\u5f55"
                }
                "search_files" -> {
                    val pattern = args["pattern"] ?: return "\u9519\u8bef: \u7f3a\u5c11 pattern \u53c2\u6570"
                    val rootDir = java.io.File(root)
                    val results = mutableListOf<String>()
                    rootDir.walkTopDown().maxDepth(10).forEach { f ->
                        val relPath = f.relativeTo(rootDir).path
                        if (f.isFile && relPath.contains(pattern.replace("*", "").replace("?", ""), ignoreCase = true)) {
                            results.add(relPath)
                        }
                    }
                    if (results.isEmpty()) "\u672a\u627e\u5230\u5339\u914d\u7684\u6587\u4ef6"
                    else results.joinToString("\n")
                }
                "grep_files" -> {
                    val pattern = args["pattern"] ?: return "\u9519\u8bef: \u7f3a\u5c11 pattern \u53c2\u6570"
                    val ext = args["extension"] ?: ""
                    val rootDir = java.io.File(root)
                    val results = mutableListOf<String>()
                    rootDir.walkTopDown().maxDepth(10).forEach { f ->
                        if (f.isFile && (ext.isBlank() || f.name.endsWith(ext))) {
                            try {
                                val fc = f.readText()
                                if (fc.contains(pattern, ignoreCase = true)) {
                                    val relPath = f.relativeTo(rootDir).path
                                    val matchingLines = fc.lines().count { l -> l.contains(pattern, ignoreCase = true) }
                                    results.add("$relPath: $matchingLines \u884c\u5339\u914d")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    if (results.isEmpty()) "\u672a\u627e\u5230\u5339\u914d\u7684\u5185\u5bb9"
                    else results.joinToString("\n")
                }
                "rename_file" -> {
                    val oldPath = args["old_path"] ?: return "\u9519\u8bef: \u7f3a\u5c11 old_path \u53c2\u6570"
                    val newPath = args["new_path"] ?: return "\u9519\u8bef: \u7f3a\u5c11 new_path \u53c2\u6570"
                    val oldFile = java.io.File(root, oldPath.trimStart('/'))
                    val newFile = java.io.File(root, newPath.trimStart('/'))
                    if (!oldFile.exists()) return "\u9519\u8bef: \u6e90\u6587\u4ef6\u4e0d\u5b58\u5728 $oldPath"
                    newFile.parentFile?.mkdirs()
                    if (oldFile.renameTo(newFile)) "\u6210\u529f\u91cd\u547d\u540d: $oldPath -> $newPath"
                    else "\u9519\u8bef: \u91cd\u547d\u540d\u5931\u8d25"
                }
                "get_project_info" -> {
                    val rootDir = java.io.File(root)
                    if (!rootDir.isDirectory) return "\u9519\u8bef: \u9879\u76ee\u76ee\u5f55\u4e0d\u5b58\u5728"
                    val allFiles = rootDir.walkTopDown().maxDepth(15).filter { it.isFile }.toList()
                    val totalSize = allFiles.sumOf { it.length() }
                    val extensions = allFiles.groupBy { it.extension.ifEmpty { "(\u65e0\u6269\u5c55\u540d)" } }.mapValues { it.value.size }
                    val dirCount = rootDir.walkTopDown().maxDepth(15).filter { it.isDirectory }.count()
                    val topDirs = rootDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                    buildString {
                        appendLine("\ud83d\udcc1 \u9879\u76ee: ${rootDir.name}")
                        appendLine("\ud83d\udcc2 \u8def\u5f84: $root")
                        appendLine("\ud83d\udcc4 \u6587\u4ef6\u603b\u6570: ${allFiles.size}")
                        appendLine("\ud83d\udccf \u603b\u5927\u5c0f: ${totalSize / 1024} KB")
                        appendLine("\ud83d\udcc1 \u76ee\u5f55\u6570: $dirCount")
                        appendLine("")
                        appendLine("\ud83d\udcca \u6587\u4ef6\u7c7b\u578b\u5206\u5e03:")
                        extensions.entries.sortedByDescending { it.value }.take(15).forEach { (ext, count) ->
                            appendLine("  .$ext: $count \u4e2a")
                        }
                        appendLine("")
                        appendLine("\ud83d\udcc2 \u9876\u5c42\u76ee\u5f55:")
                        topDirs.forEach { appendLine("  \ud83d\udcc1 $it") }
                    }
                }
                "run_command" -> {
                    val cmd = args["command"] ?: return "\u9519\u8bef: \u7f3a\u5c11 command \u53c2\u6570"
                    try {
                        val proc = ProcessBuilder()
                            .directory(java.io.File(root))
                            .command("sh", "-c", cmd)
                            .redirectErrorStream(true)
                            .start()
                        val output = proc.inputStream.bufferedReader().readText()
                        val code = proc.waitFor()
                        if (code == 0) "\u2705 \u547d\u4ee4\u6267\u884c\u6210\u529f (exit=$code):\n$output"
                        else "\u26a0\ufe0f \u547d\u4ee4\u5b8c\u6210\u4f46\u6709\u9519\u8bef (exit=$code):\n$output"
                    } catch (e: Exception) {
                        "\u9519\u8bef: \u6267\u884c\u547d\u4ee4\u5931\u8d25: ${e.message}"
                    }
                }
                else -> "\u672a\u77e5\u5de5\u5177: $name"
            }
        } catch (e: Exception) {
            "\u6267\u884c $name \u51fa\u9519: ${e.message}"
        }
    }
}