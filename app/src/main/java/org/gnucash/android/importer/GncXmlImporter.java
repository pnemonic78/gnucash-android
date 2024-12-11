/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.importer;

import static java.util.zip.GZIPInputStream.GZIP_MAGIC;

import androidx.annotation.NonNull;

import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Book;
import org.gnucash.android.util.PreferencesHelper;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import timber.log.Timber;

/**
 * Importer for Gnucash XML files and GNCA (GnuCash Android) XML files
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class GncXmlImporter {

    /**
     * Parse GnuCash XML input and populates the database
     *
     * @param gncXmlInputStream InputStream source of the GnuCash XML file
     * @return GUID of the book into which the XML was imported
     */
    public static String parse(InputStream gncXmlInputStream) throws ParserConfigurationException, SAXException, IOException {
        return parseBook(gncXmlInputStream).getUID();
    }

    /**
     * Parse GnuCash XML input and populates the database
     *
     * @param gncXmlInputStream InputStream source of the GnuCash XML file
     * @return the book into which the XML was imported
     */
    public static Book parseBook(@NonNull InputStream gncXmlInputStream) throws ParserConfigurationException, SAXException, IOException {
        //TODO: Set an error handler which can log errors
        Timber.d("Start import");
        final InputStream input = openInputStream(gncXmlInputStream);
        GncXmlHandler handler = new GncXmlHandler();
        XMLReader reader = createXMLReader(handler);

        long startTime = System.nanoTime();
        reader.parse(new InputSource(input));
        long endTime = System.nanoTime();
        Timber.d("%d ns spent on importing the file", endTime - startTime);

        Book book = handler.getImportedBook();
        String bookUID = book.getUID();
        PreferencesHelper.setLastExportTime(
                TransactionsDbAdapter.getInstance().getTimestampOfLastModification(),
                bookUID
        );

        return book;
    }

    private static InputStream openInputStream(InputStream gncXmlInputStream) throws IOException {
        final InputStream bis = new BufferedInputStream(gncXmlInputStream);
        bis.mark(0);
        int signatureLo = bis.read();
        if (signatureLo == -1) throw new EOFException("book too small");
        int signatureHi = bis.read();
        if (signatureHi == -1) throw new EOFException("book too small");
        int signature = ((signatureHi & 0xFF) << 8) | (signatureLo & 0xFF);
        bis.reset(); //push back the signature to the stream
        //check if standard gzip magic header
        return (signature == GZIP_MAGIC) ? new GZIPInputStream(bis) : bis;
    }

    private static XMLReader createXMLReader(GncXmlHandler handler) throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser sp = spf.newSAXParser();
        XMLReader xr = sp.getXMLReader();
        xr.setContentHandler(handler);
        return xr;
    }
}
