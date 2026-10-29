package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class VerifiedLexiconEntry(
    val word: String,
    val partOfSpeech: String,
    val definitions: List<String>,
    val ipaPhonetic: String?, // Null if absent — never invented
    val audioUrl: String?, // Null if absent — never faked
    val examples: List<String>,
    val synonyms: List<String>,
    val antonyms: List<String>
)

/**
 * Queries the public Free Dictionary API (dictionaryapi.dev) for verified English lexical entries,
 * authentic IPA transcriptions, and real human audio recordings when available.
 */
class DictionaryApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchVerifiedEnglishEntry(word: String): VerifiedLexiconEntry? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(word.trim().lowercase(), "UTF-8")
            val req = Request.Builder()
                .url("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null

                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext null
                val first = arr.getJSONObject(0)

                var ipa: String? = first.optString("phonetic").takeIf { it.isNotBlank() }
                var audio: String? = null
                val phonetics = first.optJSONArray("phonetics") ?: JSONArray()
                for (i in 0 until phonetics.length()) {
                    val p = phonetics.optJSONObject(i) ?: continue
                    if (ipa == null) {
                        ipa = p.optString("text").takeIf { it.isNotBlank() }
                    }
                    if (audio == null) {
                        val candidateUrl = p.optString("audio").trim()
                        if (candidateUrl.startsWith("https://") || candidateUrl.startsWith("http://")) {
                            audio = candidateUrl
                        }
                    }
                }

                val meanings = first.optJSONArray("meanings") ?: JSONArray()
                var pos = ""
                val definitions = mutableListOf<String>()
                val examples = mutableListOf<String>()
                val synonyms = mutableSetOf<String>()
                val antonyms = mutableSetOf<String>()

                for (mIdx in 0 until meanings.length()) {
                    val m = meanings.optJSONObject(mIdx) ?: continue
                    if (pos.isBlank()) pos = m.optString("partOfSpeech")
                    val synArr = m.optJSONArray("synonyms") ?: JSONArray()
                    for (s in 0 until synArr.length()) {
                        synArr.optString(s).takeIf { it.isNotBlank() }?.let { synonyms.add(it) }
                    }
                    val antArr = m.optJSONArray("antonyms") ?: JSONArray()
                    for (a in 0 until antArr.length()) {
                        antArr.optString(a).takeIf { it.isNotBlank() }?.let { antonyms.add(it) }
                    }
                    val defsArr = m.optJSONArray("definitions") ?: JSONArray()
                    for (d in 0 until defsArr.length()) {
                        val defObj = defsArr.optJSONObject(d) ?: continue
                        defObj.optString("definition").takeIf { it.isNotBlank() }?.let {
                            if (definitions.size < 5) definitions.add(it)
                        }
                        defObj.optString("example").takeIf { it.isNotBlank() }?.let {
                            if (examples.size < 4) examples.add(it)
                        }
                    }
                }

                if (definitions.isEmpty()) return@withContext null

                VerifiedLexiconEntry(
                    word = first.optString("word", word.trim()),
                    partOfSpeech = pos.ifBlank { "entry" },
                    definitions = definitions,
                    ipaPhonetic = ipa,
                    audioUrl = audio,
                    examples = examples,
                    synonyms = synonyms.take(6),
                    antonyms = antonyms.take(6)
                )
            }
        }.getOrNull()
    }
}
