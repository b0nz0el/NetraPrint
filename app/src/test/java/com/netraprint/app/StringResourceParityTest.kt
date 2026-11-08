package com.netraprint.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourceParityTest {
    private val placeholderPattern = Regex("%(?:\\d+\\$)?[a-zA-Z]")

    @Test
    fun IndonesianAndEnglishStringKeysAndFormatPlaceholdersMatch() {
        val indonesian = readStrings(File("src/main/res/values/strings.xml"))
        val english = readStrings(File("src/main/res/values-en/strings.xml"))

        assertEquals("Indonesian and English string keys differ", indonesian.keys, english.keys)
        for (key in indonesian.keys) {
            assertEquals(
                "Format placeholders differ for $key",
                placeholderPattern.findAll(indonesian.getValue(key)).map { it.value }.toList(),
                placeholderPattern.findAll(english.getValue(key)).map { it.value }.toList()
            )
        }
    }

    private fun readStrings(file: File): Map<String, String> {
        require(file.isFile) { "Missing string resource file: ${file.path}" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to element.textContent
        }
    }
}
