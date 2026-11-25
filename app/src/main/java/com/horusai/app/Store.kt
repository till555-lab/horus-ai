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
    val defaultModel: String,
    val models: List<String>,
    val keyUrl: String
) {
    OPENAI("openai", "ChatGPT", "gpt-5.4-mini", listOf("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.4"), "https://platform.openai.com/api-keys"),
    GEMINI("gemini", "Gemini", "gemini-3.8-flash", listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"), "https://aistudio.google.com/apikey"),
    CLAUDE("claude", "Claude", "claude-haiku-5-5", listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"), "https://console.anthropic.com/settings/keys"),
    DEEPSEEK("deepseek", "DeepSeek", "deepseek-chat", listOf("deepseek-chat", "deepseek-reasoner"), "https://platform.deepseek.com/api_keys");

    val aliases: List<String>
        get() = when (this) {
            OPENAI -> listOf("chatgpt", "chat gpt", "gpt", "чатгпт", "чат гпт", "чат gpt", "чат джіпіті")
            GEMINI -> listOf("gemini", "джемини", "джеміні", "гемини")
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
    val chats = mutableStateListOf<Chat>()
    var current by mutableStateOf(Chat(0L, ""))
    var discuss by mutableStateOf(true)
    var busy by mutableStateOf(false)

    init {
        load()
    }

    fun key(p: Prov): String = keys[p.id] ?: ""
    fun model(p: Prov): String = models[p.id]?.takeIf { it.isNotBlank() } ?: p.defaultModel
    fun active(p: Prov): Boolean = (enabled[p.id] ?: true) && key(p).isNotBlank()
    fun anyActive(): Boolean = Prov.values().any { active(it) }

    fun setKey(p: Prov, k: String) { keys[p.id] = k; save() }
    fun setEnabled(p: Prov, v: Boolean) { enabled[p.id] = v; save() }
    fun setModel(p: Prov, m: String) { models[p.id] = m; save() }
    fun updateDiscuss(v: Boolean) { discuss = v; save() }

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
            o.optJSONObject("used")?.let { j -> j.keys().forEach { used[it] = j.getInt(it) } }
            discuss = o.optBoolean("discuss", true)
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
            o.put("discuss", discuss)
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
