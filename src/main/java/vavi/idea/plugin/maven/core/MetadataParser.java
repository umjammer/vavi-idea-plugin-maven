/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * MetadataParser, parses maven-metadata.xml.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public final class MetadataParser {

    private MetadataParser() {}

    /** @return versions in the order of the document */
    public static List<String> parseVersions(InputStream in) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList nl = f.newDocumentBuilder().parse(in).getElementsByTagName("version");
            List<String> r = new ArrayList<>();
            for (int i = 0; i < nl.getLength(); i++) {
                // only <versioning><versions><version>, not <metadata><version>
                if (nl.item(i).getParentNode().getNodeName().equals("versions")) {
                    r.add(nl.item(i).getTextContent().trim());
                }
            }
            return r;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException(e);
        }
    }
}
