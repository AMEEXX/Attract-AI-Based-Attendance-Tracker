package com.attract.attendance.data.importexport

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal class XlsxRosterReader {
    fun read(input: InputStream): List<List<String>> {
        val entries = readZipEntries(input)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetXml = entries["xl/worksheets/sheet1.xml"]
            ?: throw RosterImportException("The XLSX file does not contain a first worksheet.")
        return parseSheet(sheetXml, sharedStrings)
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val output = linkedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in allowedEntries) {
                    val bytes = zip.readBytes()
                    if (bytes.size > maxEntryBytes) throw RosterImportException("The XLSX worksheet is too large.")
                    output[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        return output
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val document = secureFactory().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val nodes = document.getElementsByTagName("si")
        return List(nodes.length) { index ->
            val item = nodes.item(index) as Element
            item.getElementsByTagName("t").let { textNodes ->
                buildString {
                    for (i in 0 until textNodes.length) append(textNodes.item(i).textContent)
                }
            }
        }
    }

    private fun parseSheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val document = secureFactory().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val rowNodes = document.getElementsByTagName("row")
        if (rowNodes.length > maxRows + 1) throw RosterImportException("A roster can contain at most $maxRows students.")
        val rows = mutableListOf<List<String>>()
        for (rowIndex in 0 until rowNodes.length) {
            val row = rowNodes.item(rowIndex) as Element
            val cells = row.getElementsByTagName("c")
            val values = mutableMapOf<Int, String>()
            for (cellIndex in 0 until cells.length) {
                val cell = cells.item(cellIndex) as Element
                val column = columnIndex(cell.getAttribute("r"))
                if (column >= maxColumns) throw RosterImportException("A roster can contain at most $maxColumns columns.")
                values[column] = cellValue(cell, sharedStrings)
            }
            val width = (values.keys.maxOrNull() ?: -1) + 1
            rows += List(width) { index -> values[index].orEmpty() }
        }
        if (rows.isEmpty()) throw RosterImportException("The XLSX file is empty.")
        return rows
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val value = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
        return when (cell.getAttribute("t")) {
            "s" -> sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
            "inlineStr" -> cell.getElementsByTagName("t").item(0)?.textContent.orEmpty()
            else -> value
        }
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile(Char::isLetter).uppercase()
        if (letters.isBlank()) return 0
        var value = 0
        letters.forEach { char -> value = value * 26 + (char - 'A' + 1) }
        return value - 1
    }

    private fun secureFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isExpandEntityReferences = false
    }

    private companion object {
        const val maxRows = 2_000
        const val maxColumns = 50
        const val maxEntryBytes = 2 * 1024 * 1024
        val allowedEntries = setOf("xl/sharedStrings.xml", "xl/worksheets/sheet1.xml")
    }
}
