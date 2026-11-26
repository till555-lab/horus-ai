package com.horusai.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    class Reply(val text: String, val tokens: Int)
    class ApiError(val code: Int, msg: String) : Exception("HTTP $code: $msg")

    private fun errMsg(body: String): String {
        return try {
            JSONObject(body).getJSONObject("error").getString("message").take(250)
        } catch (e: Exception) {
            body.take(250)
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiError(code, errMsg(text))
            return text
        } finally {
            c.disconnect()
        }
    }

    private fun once(p: Prov, key: String, model: String, system: String, hist: List<Pair<String, String>>): Reply =
        when (p) {
            Prov.GEMINI -> gemini(key, model, system, hist)
            Prov.CLAUDE -> claude(key, model, system, hist)
            else -> openAi(base(p) + "/chat/completions", key, model, system, hist)
        }

    fun ask(p: Prov, key: String, model: String, system: String, hist: List<Pair<String, String>>): Reply {
        val r = try {
            once(p, key, model, system, hist)
        } catch (e: java.io.IOException) {
            Thread.sleep(1500)
            once(p, key, model, system, hist)
        } catch (e: ApiError) {
            if (e.code == 503 || e.code == 500 || e.code == 529) {
                Thread.sleep(1500)
                once(p, key, model, system, hist)
            } else {
                throw e
            }
        }
        if (r.tokens > 0) return r
        val chars = system.length + hist.sumOf { it.second.length } + r.text.length
        return Reply(r.text, chars / 4)
    }

    private fun base(p: Prov): String = when (p) {
        Prov.OPENAI -> "https://api.openai.com/v1"
        Prov.DEEPSEEK -> "https://api.deepseek.com"
        Prov.GROQ -> "https://api.groq.com/openai/v1"
        Prov.CEREBRAS -> "https://api.cerebras.ai/v1"
        Prov.OPENROUTER -> "https://openrouter.ai/api/v1"
        Prov.MISTRAL -> "https://api.mistral.ai/v1"
        else -> ""
    }

    private fun get(url: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 15000
            c.readTimeout = 30000
            for ((k, v) in headers) c.setRequestProperty(k, v)
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiError(code, errMsg(text))
            return text
        } finally {
            c.disconnect()
        }
    }

    fun models(p: Prov, key: String): List<String> {
        val out = ArrayList<String>()
        when (p) {
            Prov.GEMINI -> {
                val res = JSONObject(get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", mapOf("x-goog-api-key" to key)))
                val arr = res.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val methods = o.optJSONArray("supportedGenerationMethods") ?: continue
                    var ok = false
                    for (k in 0 until methods.length()) if (methods.getString(k) == "generateContent") ok = true
                    if (ok) out.add(o.getString("name").removePrefix("models/"))
                }
            }
            Prov.CLAUDE -> {
                val res = JSONObject(get("https://api.anthropic.com/v1/models?limit=100", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")))
                val arr = res.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) out.add(arr.getJSONObject(i).getString("id"))
            }
            else -> {
                val res = JSONObject(get(base(p) + "/models", mapOf("Authorization" to "Bearer $key")))
                val arr = res.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) out.add(arr.getJSONObject(i).getString("id"))
            }
        }
        val list = if (p == Prov.OPENROUTER) out.filter { it.endsWith(":free") || it == "openrouter/free" } else out
        return list.sorted().take(100)
    }

    private fun openAi(url: String, key: String, model: String, system: String, hist: List<Pair<String, String>>): Reply {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        for ((r, t) in hist) msgs.put(JSONObject().put("role", r).put("content", t))
        val body = JSONObject().put("model", model).put("messages", msgs)
        val res = JSONObject(post(url, mapOf("Authorization" to "Bearer $key"), body.toString()))
        val text = res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
        val tok = res.optJSONObject("usage")?.optInt("total_tokens") ?: 0
        return Reply(text.trim(), tok)
    }

    private fun claude(key: String, model: String, system: String, hist: List<Pair<String, String>>): Reply {
        val msgs = JSONArray()
        for ((r, t) in hist) msgs.put(JSONObject().put("role", r).put("content", t))
        val body = JSONObject().put("model", model).put("max_tokens", 2048).put("system", system).put("messages", msgs)
        val headers = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")
        val res = JSONObject(post("https://api.anthropic.com/v1/messages", headers, body.toString()))
        val content = res.getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) sb.append(content.getJSONObject(i).optString("text"))
        val u = res.optJSONObject("usage")
        val tok = (u?.optInt("input_tokens") ?: 0) + (u?.optInt("output_tokens") ?: 0)
        return Reply(sb.toString().trim(), tok)
    }

    private fun gemini(key: String, model: String, system: String, hist: List<Pair<String, String>>): Reply {
        val contents = JSONArray()
        for ((r, t) in hist) {
            contents.put(
                JSONObject().put("role", if (r == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", t)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val res = JSONObject(post(url, mapOf("x-goog-api-key" to key), body.toString()))
        val parts = res.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw Exception("Empty answer")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        val tok = res.optJSONObject("usageMetadata")?.optInt("totalTokenCount") ?: 0
        return Reply(sb.toString().trim(), tok)
    }
}
