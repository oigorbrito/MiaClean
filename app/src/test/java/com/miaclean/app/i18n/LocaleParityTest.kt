package com.miaclean.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Catches the most common i18n regressions at test time instead of crash time:
 *
 *  1. **Missing translations** — a string key declared in the EN base but absent from
 *     pt-rBR or es would fall back to EN silently, which the user won't notice on a device
 *     configured in pt-BR and which reviewers won't notice in code review.
 *  2. **Format-arg drift** — `Resources.getString("paywall_body_partial", a, b, c, d)` throws
 *     [IllegalFormatException] at runtime if a locale forgot one of the `%1$d`…`%4$d`
 *     placeholders or typed them in a different order. Each crash is silent until a specific
 *     user hits that exact path. Parsing every `%n$[sd]` in every locale and comparing to
 *     the EN base is cheap and catches it the moment a new string lands.
 *  3. **Missing plural quantities** — Android falls back to `other` if the matching quantity
 *     is missing, but the fallback masks a real translation bug. Require at least the
 *     quantities present in EN for every plural.
 *
 * The test reads the `app/src/main/res/values*` XML directly rather than using Robolectric —
 * pure-JVM, no AndroidX test deps, and stable against build flavour changes.
 */
class LocaleParityTest {

    private val baseDir = File("src/main/res/values")
    private val translations = listOf(
        File("src/main/res/values-pt-rBR"),
        File("src/main/res/values-es"),
    )

    @Test
    fun `every base string key is present in every translation with matching format args`() {
        val baseStrings = parseStrings(File(baseDir, "strings.xml"))
        for (translationDir in translations) {
            val other = parseStrings(File(translationDir, "strings.xml"))
            val missing = baseStrings.keys - other.keys
            if (missing.isNotEmpty()) {
                fail("${translationDir.name}/strings.xml is missing keys: $missing")
            }
            for ((key, baseValue) in baseStrings) {
                val translatedValue = other.getValue(key)
                val baseArgs = formatArgs(baseValue)
                val translatedArgs = formatArgs(translatedValue)
                assertEquals(
                    "Format args mismatch for '$key' in ${translationDir.name}: " +
                        "base=$baseArgs translated=$translatedArgs",
                    baseArgs,
                    translatedArgs,
                )
            }
        }
    }

    @Test
    fun `no translation declares extra string or plural keys that base is missing`() {
        val baseStringKeys = parseStrings(File(baseDir, "strings.xml")).keys
        for (translationDir in translations) {
            val translatedKeys = parseStrings(File(translationDir, "strings.xml")).keys
            val extra = translatedKeys - baseStringKeys
            if (extra.isNotEmpty()) {
                fail(
                    "${translationDir.name}/strings.xml has keys not in base: $extra. " +
                        "Add them to values/strings.xml first or delete from the translation.",
                )
            }
        }
        // Symmetric check for plurals: catches stale entries left behind after a base removal
        // (e.g. a plural renamed in EN but kept verbatim in pt-rBR, which would compile but
        // wouldn't be resolvable via R.plurals).
        for (fileName in listOf("plurals.xml", "widget_plurals.xml")) {
            val basePluralKeys = parsePlurals(File(baseDir, fileName)).keys
            for (translationDir in translations) {
                val translatedKeys = parsePlurals(File(translationDir, fileName)).keys
                val extra = translatedKeys - basePluralKeys
                if (extra.isNotEmpty()) {
                    fail(
                        "${translationDir.name}/$fileName has plurals not in base: $extra. " +
                            "Add them to values/$fileName first or delete from the translation.",
                    )
                }
            }
        }
    }

    @Test
    fun `every base plural name and quantity is present in every translation with matching format args`() {
        val baseFiles = listOf("plurals.xml", "widget_plurals.xml")
        for (fileName in baseFiles) {
            val base = parsePlurals(File(baseDir, fileName))
            for (translationDir in translations) {
                val translated = parsePlurals(File(translationDir, fileName))
                for ((name, baseQuantities) in base) {
                    val translatedQuantities = translated[name]
                        ?: fail("Missing plural '$name' in ${translationDir.name}/$fileName")
                            .let { emptyMap<String, String>() }
                    val missingQuantities = baseQuantities.keys - translatedQuantities.keys
                    if (missingQuantities.isNotEmpty()) {
                        fail(
                            "Plural '$name' in ${translationDir.name}/$fileName missing " +
                                "quantities: $missingQuantities",
                        )
                    }
                    // Validate format-arg parity within each quantity item. Without this, a
                    // translator dropping `%d` from `one` or `other` would crash with
                    // `MissingFormatArgumentException` on the unlucky user whose count triggered
                    // the broken branch, instead of failing CI here.
                    for ((quantity, baseItem) in baseQuantities) {
                        val translatedItem = translatedQuantities.getValue(quantity)
                        assertEquals(
                            "Format args mismatch for plural '$name' quantity='$quantity' " +
                                "in ${translationDir.name}/$fileName",
                            formatArgs(baseItem) + nonPositionalArgs(baseItem),
                            formatArgs(translatedItem) + nonPositionalArgs(translatedItem),
                        )
                    }
                }
            }
        }
    }

    private fun parseStrings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return buildMap {
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }

    private fun parsePlurals(file: File): Map<String, Map<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("plurals")
        return buildMap {
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                val name = element.getAttribute("name")
                val items = element.getElementsByTagName("item")
                val quantities = buildMap {
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        put(item.getAttribute("quantity"), item.textContent)
                    }
                }
                put(name, quantities)
            }
        }
    }

    /**
     * Extracts positional format args (`%1$s`, `%2$d`, `%3$f`, etc.) from a resource string,
     * returning them as a sorted set so the comparison is order-independent (translators may
     * rearrange placeholders for natural phrasing) but presence-sensitive. Accepts any
     * single-letter conversion so floats / chars / hex don't silently bypass the check.
     * Literal escaped percent signs (`%%`) are stripped beforehand so they don't misregister —
     * without that pre-pass, `%%1$d` (a literal `%1$d`) would match from the second `%`.
     */
    private fun formatArgs(value: String): Set<String> {
        val pattern = Regex("%(\\d+)\\$[a-zA-Z]")
        return pattern.findAll(value.replace("%%", "")).map { it.value }.toSet()
    }

    /**
     * Extracts non-positional format args (`%d`, `%s`, `%f`, …) used primarily inside
     * `<plurals>` items, where Android conventionally omits the positional `n$` because each
     * item has exactly one count argument. Returned as a set of "%d"/"%s" etc. so a translator
     * who dropped the `%d` from `one` while keeping it in `other` fails the test. Literal
     * `%%` is stripped beforehand for the same reason as in [formatArgs].
     */
    private fun nonPositionalArgs(value: String): Set<String> {
        val pattern = Regex("%(?!\\d+\\$)[a-zA-Z]")
        return pattern.findAll(value.replace("%%", "")).map { it.value }.toSet()
    }
}
