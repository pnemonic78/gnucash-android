package org.gnucash.android.test.unit.export

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.xml.GncXmlExporter
import org.gnucash.android.test.unit.BookHelperTest
import org.junit.Test
import java.io.StringWriter
import java.nio.charset.StandardCharsets

class XmlExporterTest : BookHelperTest() {

    private fun readFile(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
        val bytes = stream.readAllBytes()
        return String(bytes, StandardCharsets.UTF_8)
    }

    @Test
    fun `the exported file is exactly like the imported file`() {
        val context = GnuCashApplication.getAppContext()
        val bookUID = importGnuCashXml("acctchrt_common.gnucash")
        assertThat(bookUID).isEqualTo("a7682e5d878e43cea216611401f08463")

        val exportParams = ExportParams(ExportFormat.XML).apply {
            //TODO isCompressed = false
        }
        val exporter = GncXmlExporter(context, exportParams, bookUID)

        val writer = StringWriter()
        exporter.export(bookUID, writer)
        val actual = writer.toString()
        val expected = readFile("expected.acctchrt_common.gnucash")
        assertThat(actual).isEqualTo(expected)
    }
}