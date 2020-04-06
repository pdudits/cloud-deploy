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

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith({
        WiremockResolver.class,
        WiremockUriResolver.class
})
public class HttpTest {

    @Test
    public void downloadsAsUtf8String(@WiremockResolver.Wiremock WireMockServer server) {
        server.stubFor(get(urlEqualTo("/utf8"))
            .willReturn(aResponse().withBody("áááá"))
        );

        var fileSource = new FileValueUnion();
        fileSource.setHttpGet(StringValueUnion.fromString(server.url("utf8")));

        assertEquals("áááá", fileSource.contents());
    }

    @Test
    public void respectsContentEncoding(@WiremockResolver.Wiremock WireMockServer server) {
        server.stubFor(get(urlEqualTo("/windows1250")).willReturn(
                aResponse()
                        .withBody("áááá".getBytes(Charset.forName("windows-1250")))
                        .withHeader("Content-Type", "text/plain;charset=windows-1250")));
        var fileSource = new FileValueUnion();
        fileSource.setHttpGet(StringValueUnion.fromString(server.url("windows1250")));

        assertEquals("áááá", fileSource.contents());
    }

    @Test
    public void downloadsIntoFile(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        var contents = new byte[256];
        for(int i=255; i>0; i--) {
            contents[i] = (byte) i;
        }

        server.stubFor(get(urlEqualTo("/files/file.war")).willReturn(
                aResponse()
                    .withBody(contents)
                    .withHeader("Content-Type", "application/octet-stream")
        ));

        var fileSource = new FileValueUnion();
        fileSource.setHttpGet(StringValueUnion.fromString(server.url("files/file.war")));

        var filename = fileSource.fileName();

        var fileContents = Files.readAllBytes(Paths.get(filename));
        assertArrayEquals(contents, fileContents);
    }
}
