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

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.setup.AzureStorage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;

@AzureStorage
@ApplicationScoped
class AzureBlobStorage implements ArtifactStorage {

    static final String CONFIG_CONNECTIONSTRING = "artifactstorage.azure.connectionstring";
    static final String CONFIG_CONTAINER = "artifactstorage.azure.container";

    @Inject
    @ConfigProperty(name= CONFIG_CONNECTIONSTRING)
    String azureConnectionString;

    @Inject
    @ConfigProperty(name= CONFIG_CONTAINER)
    String blobContainer;

    private CloudStorageAccount storageAccount;
    CloudBlobContainer container;

    @Override
    public URI storeArtifact(DeploymentProcessState deploymentProcess) throws IOException {
        try {
            var dir = container.getDirectoryReference(deploymentProcess.getNamespace().getProject());
            var blob = dir.getBlockBlobReference(deploymentProcess.getId()+"/"+deploymentProcess.getName());
            blob.uploadFromFile(deploymentProcess.getTempLocation().getAbsolutePath());
            return blob.getUri();
        } catch (URISyntaxException e) {
            throw new IOException("The name in deployment process is invalid", e);
        } catch (StorageException e) {
            throw new IOException("Storage error", e);
        }
    }

    @Override
    public void deleteArtifact(DeploymentProcessState deploymentProcess) throws IOException {
        var uri = deploymentProcess.getPersistentLocation();
        if (uri != null) {
            var base = container.getStorageUri().getPrimaryUri();
            var path = base.relativize(uri);
            if (!path.isAbsolute()) {
                // we likely matched the prefix
                try {
                    var blob = container.getBlockBlobReference(path.toString());
                    blob.delete();

                } catch (URISyntaxException | StorageException e) {
                    throw new IOException("Could not delete artifact "+uri,e);
                }
            }
        }

    }

    @PostConstruct
    void initAccount()  {
        try {
            storageAccount = CloudStorageAccount.parse(azureConnectionString);
            var client = storageAccount.createCloudBlobClient();
            container = client.getContainerReference(blobContainer);
            // the contents of the blob container will be accessible, so that instances can read deployment artifacts,
            // but cannot list them.
            container.createIfNotExists(BlobContainerPublicAccessType.BLOB, new BlobRequestOptions(), new OperationContext());
        } catch (URISyntaxException | InvalidKeyException | StorageException e) {
            throw new IllegalArgumentException("Azure Connection not configured properly. Check config artifactstorage.azure.connectionstring", e);
        }
    }


}
