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

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * File value. One of following ways of defining a file can be chosen:
 *
 * <p>Literal value is interpreted as file path relative to defining configuration file</p>
 * <p>Property {@link #getPath() path} evaluates file path as a {@link StringValueUnion}</p>
 * <p>Property {@link #getHttpGet() httpGet} will download the file via HTTP</p>
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
public class FileValueUnion implements FileValue {
    private String file;

    private Path fileContext;

    private StringValue path;
    private StringValue httpGet;

    @JsonIgnore
    private FileValue delegate;

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public Path getFileContext() {
        return fileContext;
    }

    @JacksonInject("fileContext")
    public void setFileContext(Path fileContext) {
        this.fileContext = fileContext;
    }

    public StringValue getPath() {
        return path;
    }

    public void setPath(StringValue path) {
        this.path = path;
    }

    public StringValue getHttpGet() {
        return httpGet;
    }

    public void setHttpGet(StringValue httpGet) {
        this.httpGet = httpGet;
    }

    public String fileName() {
        return delegate().fileName();
    }

    public String contents() {
        return delegate().contents();
    }

    private FileValue delegate() {
        if (delegate == null) {
            var options = Stream.of(file, path, httpGet).filter(c -> c != null).count();
            if (options != 1) {
                throw new IllegalArgumentException("Zero or more than one value specified");
            }
            if (file != null) {
                delegate = new LocalFile(file, fileContext);
            } else if (path != null) {
                delegate = new LocalFile(path.value(), fileContext);
            } else if (httpGet != null) {
                delegate = new RemoteFile(httpGet.value());
            }
        }
        return delegate;
    }

    static class LocalFile implements FileValue {
        private final String path;
        private final Path context;

        LocalFile(String path, Path context) {
            this.path = path;
            this.context = context;
        }

        @Override
        public String fileName() {
            return resolve().toAbsolutePath().toString();
        }

        private Path resolve() {
            return context.resolveSibling(path);
        }

        @Override
        public String contents() {
            try {
                return Files.readString(resolve());
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read referenced file", e);
            }

        }
    }

    static class RemoteFile implements FileValue {

        private final String uri;

        public RemoteFile(String uri) {
            this.uri = uri;
        }

        @Override
        public String fileName() {
            throw new UnsupportedOperationException("Not yet supported");
        }

        @Override
        public String contents() {
            throw new UnsupportedOperationException("Not yet supported");
        }
    }

    static class Deserializer extends StdDeserializer<FileValueUnion> {
        static JavaType type = TypeFactory.defaultInstance().constructType(FileValueUnion.class);

        public Deserializer() {
            super(FileValueUnion.class);
        }

        @Override
        public FileValueUnion deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                FileValueUnion result = new FileValueUnion();
                result.setFile(p.getText());
                result.setFileContext((Path) ctxt.findInjectableValue("fileContext", null, null));
                return result;
            } else {
                return (FileValueUnion) ctxt.findRootValueDeserializer(type).deserialize(p, ctxt);
            }
        }
    }

}
