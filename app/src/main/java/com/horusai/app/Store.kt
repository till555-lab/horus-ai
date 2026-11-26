package com.horusai.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject

enum class Prov(
    val id: String,
    val title: String,
    val free: Boolean,
    val defaultModel: String,
    val models: List<String>,
    val tiers: List<Int>,
    val keyUrl: String
) {
    GEMINI("gemini", "Gemini", true, "gemini-3.5-flash-lite", listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"), listOf(0, 1), "https://aistudio.google.com/apikey"),
    GROQ("groq", "Groq", true, "openai/gpt-oss-120b", listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.8-27b"), listOf(0, 1, 1), "https://console.groq.com/keys"),
    CEREBRAS("cerebras", "Cerebras", true, "gpt-oss-120b", listOf("gpt-oss-120b", "qwen-3.8-27b"), listOf(1, 1), "https://cloud.cerebras.ai"),
    OPENROUTER("openrouter", "OpenRouter", true, "openrouter/free", listOf("openrouter/free"), listOf(1), "https://openrouter.ai/keys"),
    MISTRAL("mistral", "Mistral", true, "mistral-small-latest", listOf("mistral-small-latest", "mistral-large-latest"), listOf(0, 2), "https://console.mistral.ai/api-keys"),
    OPENAI("openai", "ChatGPT", false, "gpt-5.4-mini", listOf("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.4"), listOf(0, 1, 2), "https://platform.openai.com/api-keys"),
    CLAUDE("claude", "Claude", false, "claude-haiku-5-5", listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"), listOf(0, 1, 2), "https://console.anthropic.com/settings/keys"),
    DEEPSEEK("deepseek", "DeepSeek", false, "deepseek-chat", listOf("deepseek-chat", "deepseek-reasoner"), listOf(0, 2), "https://platform.deepseek.com/api_keys");

    val aliases: List<String>
        get() = when (this) {
            OPENAI -> listOf("chatgpt", "chat gpt", "gpt", "чатгпт", "чат гпт", "чат gpt", "чат джіпіті")
            GEMINI -> listOf("gemini", "джемини", "джеміні", "гемини")
            GROQ -> listOf("groq", "grok", "грок")
            CEREBRAS -> listOf("cerebras", "церебрас")
            OPENROUTER -> listOf("openrouter", "опенроутер")
            MISTRAL -> listOf("mistral", "мистраль", "містраль")
            CLAUDE -> listOf("claude", "клод", "клауд")
            DEEPSEEK -> listOf("deepseek", "дипсик", "діпсік")
        }
}

class Msg(val role: String, val text: String, val from: String = "", val note: String = "")

class Chat(val id: Long, title: String) {
    var title by mutableStateOf(title)
    val msgs = mutableStateListOf<Msg>()
}

class AppState(ctx: Context) {
    private val sp = ctx.getSharedPreferences("horus", Context.MODE_PRIVATE)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val keys = mutableStateMapOf<String, String>()
    val enabled = mutableStateMapOf<String, Boolean>()
    val models = mutableStateMapOf<String, String>()
    val used = mutableStateMapOf<String, Int>()
    val blocked = mutableStateMapOf<String, Boolean>()
    val locked = mutableStateMapOf<String, Boolean>()
    val limits = mutableStateMapOf<String, Int>()
    val chats = mutableStateListOf<Chat>()
    var current by mutableStateOf(Chat(0L, ""))
    var discuss by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var peekTick by mutableStateOf(0)

    init {
        load()
    }

    fun key(p: Prov): String = keys[p.id] ?: ""
    fun model(p: Prov): String = models[p.id]?.takeIf { it.isNotBlank() } ?: p.defaultModel
    fun active(p: Prov): Boolean = (enabled[p.id] ?: true) && key(p).isNotBlank() && locked[p.id] != true
    fun anyActive(): Boolean = Prov.values().any { active(it) }

    fun setKey(p: Prov, k: String) { keys[p.id] = k; locked[p.id] = false; blocked[p.id] = false; save() }
    fun setEnabled(p: Prov, v: Boolean) { if (locked[p.id] == true) return; enabled[p.id] = v; save() }
    fun setModel(p: Prov, m: String) { models[p.id] = m; save() }
    fun updateDiscuss(v: Boolean) { discuss = v; save() }
    fun setLocked(p: Prov, v: Boolean) { locked[p.id] = v; enabled[p.id] = !v; if (!v) blocked[p.id] = false; if (v) peekTick++; save() }
    fun setLimit(p: Prov, n: Int) { limits[p.id] = n; save() }
    fun resetUsed(p: Prov) { used[p.id] = 0; save() }
    fun pickModel(p: Prov, m: String) { models[p.id] = m; save() }

    fun nextModel(p: Prov) {
        val l = p.models
        val i = l.indexOf(model(p))
        models[p.id] = l[(i + 1) % l.size]
        save()
    }

    fun newChat() {
        if (current.msgs.isEmpty() && chats.contains(current)) return
        val c = Chat(System.currentTimeMillis(), "")
        chats.add(0, c)
        current = c
        save()
    }

    fun deleteChat(c: Chat) {
        chats.remove(c)
        if (chats.isEmpty()) chats.add(Chat(System.currentTimeMillis(), ""))
        if (!chats.contains(current)) current = chats.first()
        save()
    }

    private fun load() {
        try {
            val o = JSONObject(sp.getString("s", "{}") ?: "{}")
            o.optJSONObject("keys")?.let { j -> j.keys().forEach { keys[it] = j.getString(it) } }
            o.optJSONObject("on")?.let { j -> j.keys().forEach { enabled[it] = j.getBoolean(it) } }
            o.optJSONObject("model")?.let { j -> j.keys().forEach { models[it] = j.getString(it) } }
            models["groq"]?.let { if (it.startsWith("llama")) models.remove("groq") }
            o.optJSONObject("used")?.let { j -> j.keys().forEach { used[it] = j.getInt(it) } }
            o.optJSONObject("locked")?.let { j -> j.keys().forEach { locked[it] = j.getBoolean(it) } }
            o.optJSONObject("limits")?.let { j -> j.keys().forEach { limits[it] = j.getInt(it) } }
            discuss = o.optBoolean("discuss2", false)
            val arr = o.optJSONArray("chats") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val ch = Chat(c.getLong("id"), c.getString("title"))
                val ms = c.getJSONArray("m")
                for (k in 0 until ms.length()) {
                    val m = ms.getJSONObject(k)
                    ch.msgs.add(Msg(m.getString("r"), m.getString("t"), m.optString("f"), m.optString("n")))
                }
                chats.add(ch)
            }
        } catch (e: Exception) {
        }
        if (chats.isEmpty()) chats.add(Chat(System.currentTimeMillis(), ""))
        current = chats.first()
    }

    fun save() {
        try {
            val o = JSONObject()
            o.put("keys", JSONObject(keys.toMap()))
            o.put("on", JSONObject(enabled.toMap()))
            o.put("model", JSONObject(models.toMap()))
            o.put("used", JSONObject(used.toMap()))
            o.put("locked", JSONObject(locked.toMap()))
            o.put("limits", JSONObject(limits.toMap()))
            o.put("discuss2", discuss)
            val arr = JSONArray()
            for (c in chats) {
                val ms = JSONArray()
                for (m in c.msgs) {
                    ms.put(JSONObject().put("r", m.role).put("t", m.text).put("f", m.from).put("n", m.note))
                }
                arr.put(JSONObject().put("id", c.id).put("title", c.title).put("m", ms))
            }
            o.put("chats", arr)
            sp.edit().putString("s", o.toString()).apply()
        } catch (e: Exception) {
        }
    }
}
