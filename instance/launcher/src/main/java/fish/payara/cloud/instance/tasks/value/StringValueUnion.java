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

package fish.payara.cloud.instance.tasks.value;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * String value. One of following options can be used to specify the value:
 * <p>Literal value is a string constant</p>
 * <p>Property {@link #getFile() file} defines a file, contents of which will be used as value</p>
 * <p>Property {@link #getPropertyFile() propertyFile} will read specified key in a property file.
 * Property file needs to specify {@link PropertyValue#getFile() file} -- the property file and
 * {@link PropertyValue#getKey() key} -- the key to read.</p>
 */
public class StringValueUnion implements StringValue {
    private String literal;

    private FileValue file;
    private PropertyValue propertyFile;


    public String getLiteral() {
        return literal;
    }

    public void setLiteral(String literal) {
        this.literal = literal;
    }

    public FileValue getFile() {
        return file;
    }

    public void setFile(FileValue file) {
        this.file = file;
    }

    public PropertyValue getPropertyFile() {
        return propertyFile;
    }

    public void setPropertyFile(PropertyValue propertyFile) {
        this.propertyFile = propertyFile;
    }

    public String value() {
        var numSpecified = Stream.of(literal, file, propertyFile).filter(o -> o!= null).count();
        if (numSpecified != 1) {
            throw new IllegalArgumentException("Value not specified (or overspecified)");
        }
        if (literal != null) {
            return literal;
        } else if (file != null) {
            return file.contents();
        } else if (propertyFile != null) {
            return propertyFile.value();
        }
        throw new AssertionError("Unreachable statement");
    }

    @JsonCreator
    public static StringValueUnion fromString(String literal) {
        StringValueUnion result = new StringValueUnion();
        result.setLiteral(literal);
        return result;
    }

    public static class PropertyValue {
        private FileValue file;
        private StringValue key;
        private StringValue format;

        public FileValue getFile() {
            return file;
        }

        public void setFile(FileValue file) {
            this.file = file;
        }

        public StringValue getKey() {
            return key;
        }

        public void setKey(StringValue key) {
            this.key = key;
        }

        public StringValue getFormat() {
            return format;
        }

        public void setFormat(StringValue format) {
            this.format = format;
        }

        public String value() {
            if (format == null) {
                format = StringValueUnion.fromString("property");
            }
            switch (format.value()) {
                case "property":
                    return propertyValue();
                default:
                    throw new IllegalArgumentException("Unsupported property file format "+format.value());
            }
        }

        private String propertyValue() {
            Properties prop = new Properties();
            try (var reader = new FileReader(file.fileName(), StandardCharsets.UTF_8)) {
                prop.load(reader);
                var actualKey = key.value();
                if (!prop.containsKey(actualKey)) {
                    throw new IllegalArgumentException("Key "+actualKey+" in not present in "+file.fileName());
                }
                return prop.getProperty(key.value());
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read "+file.fileName(), e);
            }
        }
    }

    public static StringValueUnion literal(String literal) {
        StringValueUnion result = new StringValueUnion();
        result.literal = literal;
        return result;
    }
}
