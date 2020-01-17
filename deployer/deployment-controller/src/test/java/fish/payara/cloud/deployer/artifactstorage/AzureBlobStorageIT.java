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

package fish.payara.cloud.deployer.artifactstorage;

import com.microsoft.azure.storage.StorageException;
import fish.payara.cloud.deployer.DockerTest;
import fish.payara.cloud.deployer.process.ProcessAccessor;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;

@Category(DockerTest.class)
public class AzureBlobStorageIT {
    @Test
    public void artifactIsUploadedAndDeleted() throws IOException {
        var storage = new AzureBlobStorage();
        storage.azureConnectionString = System.getProperty(AzureBlobStorage.CONFIG_CONNECTIONSTRING);
        storage.blobContainer = System.getProperty(AzureBlobStorage.CONFIG_CONTAINER);
        storage.initAccount();

        var source = new File("pom.xml");
        var process = ProcessAccessor.createProcess(source);
        var uri = storage.storeArtifact(process);

        // we should be able to fetch that URI now
        try (var input = uri.toURL().openStream()) {
            var content = input.readAllBytes();
            assertEquals("Downloaded and uploaded sizes should match", source.length(), content.length);
        }

        ProcessAccessor.setPersistentLocation(process, uri);
        storage.deleteArtifact(process);
    }

    @Test(expected = IOException.class)
    public void artbitraryArtifactIsNotDeleted() throws IOException, URISyntaxException, StorageException {
        var storage = new AzureBlobStorage();
        storage.azureConnectionString = System.getProperty(AzureBlobStorage.CONFIG_CONNECTIONSTRING);
        storage.blobContainer = System.getProperty(AzureBlobStorage.CONFIG_CONTAINER);
        storage.initAccount();

        var process = ProcessAccessor.createProcess();
        ProcessAccessor.setPersistentLocation(process, storage.container.getBlockBlobReference("random/stuff.txt").getUri());

        storage.deleteArtifact(process);
    }

    @Test
    public void artifactsFromDifferentStorageAreIgnored() throws IOException, URISyntaxException, StorageException {
        var storage = new AzureBlobStorage();
        storage.azureConnectionString = System.getProperty(AzureBlobStorage.CONFIG_CONNECTIONSTRING);
        storage.blobContainer = System.getProperty(AzureBlobStorage.CONFIG_CONTAINER);
        storage.initAccount();

        var process = ProcessAccessor.createProcess();
        // this effectively replaces container name with random, therefore the uri represents something outside the
        // container
        var foreignUri = storage.container.getUri().resolve(URI.create("./random/stuff.txt"));
        ProcessAccessor.setPersistentLocation(process, foreignUri);

        storage.deleteArtifact(process);
    }
}
