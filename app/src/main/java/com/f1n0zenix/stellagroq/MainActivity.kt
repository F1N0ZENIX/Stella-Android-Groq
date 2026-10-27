package com.f1n0zenix.stellagroq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Message(val role: String, val content: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("private", MODE_PRIVATE)
        setContent {
            val scope = rememberCoroutineScope()
            var key by remember { mutableStateOf(prefs.getString("groq_key", "") ?: "") }
            var draft by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var settings by remember { mutableStateOf(false) }
            var model by remember { mutableStateOf((prefs.getString("model", "Automático") ?: "Automático").let { if (it == "llama-3.3-70b-versatile") "Automático" else it }) }
            var error by remember { mutableStateOf("") }
            val messages = remember { mutableStateListOf<Message>() }
            LaunchedEffect(Unit) {
                val saved = prefs.getString("messages", "[]") ?: "[]"
                runCatching {
                    val arr = JSONArray(saved)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        messages.add(Message(obj.getString("role"), obj.getString("content")))
                    }
                }
            }
            fun persist() {
                val arr = JSONArray()
                messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
                prefs.edit().putString("messages", arr.toString()).apply()
            }
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color(0xFF151515), primary = Color.White, onPrimary = Color.Black)) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stella", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        TextButton(onClick = { settings = !settings }) { Text(if (settings) "Voltar" else "Configurações") }
                    }
                    if (settings) {
                        Text("Inteligência artificial", style = MaterialTheme.typography.titleLarge)
                        Text("Selecione o modelo usado nas conversas", color = Color.LightGray)
                        listOf("Automático", "llama-3.1-8b-instant", "openai/gpt-oss-20b", "openai/gpt-oss-120b").forEach { candidate ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                RadioButton(selected = model == candidate, onClick = { model = candidate; prefs.edit().putString("model", model).apply() })
                                Text(candidate)
                            }
                        }
                        Text("Conexão Groq", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(value = key, onValueChange = { key = it; prefs.edit().putString("groq_key", it).apply() },
                            label = { Text("Chave API Groq") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Text("Histórico salvo neste aparelho. Requer internet para a IA.", color = Color.LightGray)
                    } else {
                        Spacer(Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(messages.toList()) { msg ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(if (msg.role == "user") "Você" else "Stella", color = Color.LightGray)
                                    Text(msg.content)
                                }
                            }
                        }
                    }
                    if (key.isBlank()) Text("Abra Configurações e adicione sua chave Groq.", color = Color.LightGray)
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF8888))
                    Row {
                        OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Digite sua mensagem") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = !busy && draft.isNotBlank() && key.isNotBlank(), onClick = {
                            messages.add(Message("user", draft))
                            draft = ""
                            persist()
                            busy = true
                            error = ""
                            scope.launch {
                                try {
                                    val reply = withContext(Dispatchers.IO) { askGroqAutomatic(key, model, messages.toList()) }
                                    messages.add(Message("assistant", reply))
                                    persist()
                                } catch (e: Exception) { error = e.message ?: "Falha de conexão" }
                                busy = false
                            }
                        }) { Text(if (busy) "..." else "Enviar") }
                    }
                    }
                }
            }
        }
    }
}

private fun askGroq(key: String, model: String, messages: List<Message>): String {
    val conn = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Content-Type", "application/json")
        val history = JSONArray()
        history.put(JSONObject().put("role", "system").put("content", "Você é Stella, assistente pessoal em português brasileiro. Seja útil e clara. Não afirme ter criado lembretes ou executado ações externas."))
        messages.takeLast(20).forEach { history.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val payload = JSONObject().put("model", model).put("messages", history)
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val response = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("Groq HTTP $code: " + response.take(250))
        return JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    } finally { conn.disconnect() }
}

private fun askGroqAutomatic(key: String, selected: String, messages: List<Message>): String {
    if (selected != "Automático") return askGroq(key, selected, messages)
    val prompt = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
    val complex = prompt.length > 450 || listOf("analise", "compare", "planeje", "código", "explique detalhadamente").any { prompt.contains(it, ignoreCase = true) }
    val candidates = if (complex) listOf("openai/gpt-oss-20b", "llama-3.1-8b-instant") else listOf("llama-3.1-8b-instant", "openai/gpt-oss-20b")
    var last: Exception? = null
    for (candidate in candidates) {
        try { return askGroq(key, candidate, messages) }
        catch (e: Exception) {
            last = e
            val detail = e.message.orEmpty()
            if (!listOf("HTTP 404", "HTTP 429", "HTTP 503", "HTTP 502", "HTTP 500").any { detail.contains(it) }) throw e
        }
    }
    throw last ?: IllegalStateException("Nenhum modelo disponível")
}
