/*
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */

package fish.payara.cloud.deployer.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;
import java.io.IOException;
import java.io.InputStream;

public class Xml {
    private Xml() {

    }

    public static Document parse(InputStream inputStream) throws SAXException, IOException {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setExpandEntityReferences(false);
        dbf.setNamespaceAware(true);
        try {
            dbf.setFeature( "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return dbf.newDocumentBuilder().parse(inputStream);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Cannot instantiate XML parser");
        }
    }

    public static XPathNodes xpath(Document document, String xpath)  {
        var factory = XPathFactory.newInstance();
        try {
            return factory.newXPath().evaluateExpression(xpath, document, XPathNodes.class);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Invalid xpath query "+xpath, e);
        }
    }

    public static XPathNodes xpath(Node document, String xpath)  {
        var factory = XPathFactory.newInstance();
        try {
            return factory.newXPath().evaluateExpression(xpath, document, XPathNodes.class);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Invalid xpath query "+xpath, e);
        }
    }

    public static String attr(Node element, String attribute, String defaultValue) {
        if (!element.hasAttributes()) {
            return defaultValue;
        }
        var attr = element.getAttributes().getNamedItem(attribute);
        return attr != null ? attr.getNodeValue() : defaultValue;
    }

    public static Node child(Node node, String ns, String elementName) {
        if (!node.hasChildNodes()) {
            return null;
        }
        var children = node.getChildNodes();
        for (int i=0; i < children.getLength(); i++) {
            var child = children.item(i);
            if ((ns == null || ns.equals(child.getNamespaceURI())) && elementName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

}
