/*
 * Test support — reads the Android string resources of the repository the way the app does on a phone :
 * the folder of the requested language first, then the default folder. No label is written in test code.
 */
package be.heyman.android.jemmapassdemo.testsupport

import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver
import be.heyman.android.jemmapassdemo.qr.codeLabelResourceName
import java.io.File

class ResXmlCodeLabels(private val res: File) : CodeLabelResolver {

    private val cache = HashMap<String, Map<String, String>>()
    private val entry = Regex("<string\\s+name=\"([A-Za-z0-9_]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

    private fun folder(name: String): Map<String, String> = cache.getOrPut(name) {
        val dir = File(res, name)
        if (!dir.isDirectory) emptyMap()
        else dir.listFiles { f -> f.extension == "xml" }.orEmpty().flatMap { f ->
            entry.findAll(f.readText()).map { m -> m.groupValues[1] to unescape(m.groupValues[2]) }.toList()
        }.toMap()
    }

    private fun unescape(raw: String): String = raw.trim()
        .replace("\\'", "'").replace("\\\"", "\"")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    override fun getLabel(system: String, code: String, lang: String): String? {
        val name = codeLabelResourceName(system, code) ?: return null
        return folder("values-${lang.lowercase().take(2)}")[name] ?: folder("values")[name]
    }

    companion object {
        fun locate(): File? = listOf("src/main/res", "app/src/main/res", "JemmaPassAndroidDemo/app/src/main/res")
            .map { File(it) }.firstOrNull { it.isDirectory }
    }
}
