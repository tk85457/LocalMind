package com.localmind.app.core.utils

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

class DocumentHelper(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    fun extractTextFromUri(uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            val inputStream: InputStream? = contentResolver.openInputStream(uri)

            if (mimeType == "application/pdf") {
                extractTextFromPdf(inputStream)
            } else {
                extractTextFromPlainText(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractTextFromPlainText(inputStream: InputStream?): String? {
        return inputStream?.bufferedReader()?.use { it.readText() }
    }

    private fun extractTextFromPdf(inputStream: InputStream?): String? {
        if (inputStream == null) return null
        return try {
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                stripper.getText(document)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
