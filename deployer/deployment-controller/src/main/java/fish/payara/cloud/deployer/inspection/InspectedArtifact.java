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

package fish.payara.cloud.deployer.inspection;

import fish.payara.cloud.deployer.process.Configuration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Represents a deployable artifact, that is being inspected.
 */
public class InspectedArtifact {

    private final String name;
    private final ZipFile zipFile;
    private final List<Inspection> inspections;
    private Inspection.ArtifactEntry parent;
    private List<Configuration> configurations = new ArrayList<>();

    InspectedArtifact(String name, ZipFile zipFile, List<Inspection> inspections) {
        this.name = name;
        this.zipFile = zipFile;
        this.inspections = new ArrayList<>(inspections);
    }

    void inspect() {
        inspections.forEach(ins -> ins.start(this));
        zipFile.stream().forEach(this::inspectEntry);
        inspections.forEach(ins -> ins.finish(this));
    }

    public void addConfiguration(Configuration conf) {
        this.configurations.add(conf);
    }

    public String getName() {
        return name;
    }

    public List<Configuration> getConfigurations() {
        return this.configurations;
    }

    private void inspectEntry(ZipEntry entry) {
        var inspectionEntry = new InspectionEntry(entry);
        inspections.forEach(ins -> {
            try {
                ins.inspect(inspectionEntry, this);
            } catch (IOException e) {
                throw new InspectionException("Failed to inspect artifact with "+ins.getClass().getSimpleName(), e);
            }
        });
    }

    public static InspectedArtifact inspect(String name, ZipFile file, List<Inspection> inspections) throws IOException {
        var inspectedArtifact = new InspectedArtifact(name, file, inspections);
        inspectedArtifact.inspect();
        return inspectedArtifact;
    }

    class InspectionEntry implements Inspection.ArtifactEntry {
        private final ZipEntry entry;
        private byte[] content;

        private InspectionEntry(ZipEntry entry) {
            this.entry = entry;
        }

        @Override
        public String getName() {
            return entry.getName();
        }

        @Override
        public byte[] getContent() throws IOException {
            if (content == null) {
                content = zipFile.getInputStream(entry).readAllBytes();
            }
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(getContent());
        }

        @Override
        public boolean isDirectory() {
            return entry.isDirectory();
        }

        @Override
        public Optional<Inspection.ArtifactEntry> getParent() {
            return Optional.ofNullable(parent);
        }

        @Override
        public boolean matches(String regExp) {
            return getName().matches(regExp);
        }
    }
}
