package com.padayflow.app

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.padayflow.app.data.TaskEntity
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TaskExport {
    private val headers = listOf("Judul", "Kategori", "Deskripsi", "Prioritas", "Status", "Tanggal Mulai", "Deadline", "Estimasi (menit)", "Catatan")
    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID"))

    private fun rows(tasks: List<TaskEntity>): List<List<String>> = tasks.map { task ->
        listOf(
            task.title, task.category, task.description, task.priority, task.status,
            task.startAt?.let(::formatDate).orEmpty(),
            task.deadlineAt?.let(::formatDate).orEmpty(),
            task.estimatedMinutes.toString(), task.notes
        )
    }

    private fun formatDate(value: Long): String =
        Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)

    fun share(context: Context, tasks: List<TaskEntity>, format: String) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(java.time.LocalDateTime.now())
        val (file, mime) = when (format.lowercase(Locale.ROOT)) {
            "pdf" -> File(exportDir, "PaDayFlow_Laporan_$stamp.pdf").also { writePdf(it, tasks) } to "application/pdf"
            "xlsx" -> File(exportDir, "PaDayFlow_Data_$stamp.xlsx").also { writeXlsx(it, tasks) } to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> File(exportDir, "PaDayFlow_Data_$stamp.csv").also { writeCsv(it, tasks) } to "text/csv"
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Laporan PaDayFlow")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan hasil ekspor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun writeCsv(file: File, tasks: List<TaskEntity>) {
        FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            (listOf(headers) + rows(tasks)).forEach { row ->
                writer.write(row.joinToString(",") { value -> "\"" + value.replace("\"", "\"\"") + "\"" })
                writer.newLine()
            }
        }
    }

    private fun writePdf(file: File, tasks: List<TaskEntity>) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
        val body = buildList {
            add("PaDayFlow — Laporan Tugas")
            add("Dibuat: " + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale("id", "ID"))))
            add("Jumlah tugas: ${tasks.size}")
            add("")
            tasks.forEachIndexed { index, task ->
                add("${index + 1}. ${task.title}")
                add("   Kategori: ${task.category} | Prioritas: ${task.priority} | Status: ${task.status}")
                add("   Mulai: ${task.startAt?.let(::formatDate) ?: "-"} | Deadline: ${task.deadlineAt?.let(::formatDate) ?: "-"}")
                if (task.description.isNotBlank()) add("   Deskripsi: ${task.description}")
                if (task.notes.isNotBlank()) add("   Catatan: ${task.notes}")
                add("")
            }
        }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 48f
        body.forEachIndexed { index, line ->
            if (y > pageHeight - 48) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 48f
            }
            canvas.drawText(line.take(105), 40f, y, if (index == 0) titlePaint else paint)
            y += if (index == 0) 30f else 17f
        }
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
    }

    private fun writeXlsx(file: File, tasks: List<TaskEntity>) {
        val allRows = listOf(headers) + rows(tasks)
        fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        fun columnName(index: Int): String {
            var n = index + 1
            var result = ""
            while (n > 0) {
                val rem = (n - 1) % 26
                result = ('A'.code + rem).toChar() + result
                n = (n - 1) / 26
            }
            return result
        }
        val sheetRows = allRows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexed { colIndex, value ->
                val ref = columnName(colIndex) + (rowIndex + 1)
                "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"
            }.joinToString("")
            "<row r=\"${rowIndex + 1}\">$cells</row>"
        }.joinToString("")
        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$sheetRows</sheetData></worksheet>"""
        val entries = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Daftar Tugas" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheet
        )
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
