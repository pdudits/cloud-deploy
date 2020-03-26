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


package fish.payara.cloud.deployer.kubernetes.crd;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebAppSpec {
    private URI artifactUrl;

    private List<WebAppSpecConfiguration> _configuration = null;

    private UUID deploymentProcessId;


    public WebAppSpec artifactUrl(URI artifactUrl) {
        this.artifactUrl = artifactUrl;
        return this;
    }

    public URI getArtifactUrl() {
        return artifactUrl;
    }

    public void setArtifactUrl(URI artifactUrl) {
        this.artifactUrl = artifactUrl;
    }


    public WebAppSpec _configuration(List<WebAppSpecConfiguration> _configuration) {

        this._configuration = _configuration;
        return this;
    }

    public WebAppSpec addConfigurationItem(WebAppSpecConfiguration _configurationItem) {
        if (this._configuration == null) {
            this._configuration = new ArrayList<>();
        }
        this._configuration.add(_configurationItem);
        return this;
    }

    public WebAppSpec addConfigurationItem(String kind, String id, Map<String,String> values) {
        var config = new WebAppSpecConfiguration();
        config.setKind(kind);
        config.setId(id);
        config.setValues(values);
        return addConfigurationItem(config);
    }

    public List<WebAppSpecConfiguration> getConfiguration() {
        return _configuration;
    }


    public void setConfiguration(List<WebAppSpecConfiguration> _configuration) {
        this._configuration = _configuration;
    }


    public WebAppSpec deploymentProcessId(UUID deploymentProcessId) {

        this.deploymentProcessId = deploymentProcessId;
        return this;
    }

    public UUID getDeploymentProcessId() {
        return deploymentProcessId;
    }


    public void setDeploymentProcessId(UUID deploymentProcessId) {
        this.deploymentProcessId = deploymentProcessId;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WebAppSpec webAppSpec = (WebAppSpec) o;
        return Objects.equals(this.artifactUrl, webAppSpec.artifactUrl) &&
                Objects.equals(this._configuration, webAppSpec._configuration) &&
                Objects.equals(this.deploymentProcessId, webAppSpec.deploymentProcessId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactUrl, _configuration, deploymentProcessId);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class WebAppSpec {\n");
        sb.append("    artifactUrl: ").append(toIndentedString(artifactUrl)).append("\n");
        sb.append("    _configuration: ").append(toIndentedString(_configuration)).append("\n");
        sb.append("    deploymentProcessId: ").append(toIndentedString(deploymentProcessId)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

