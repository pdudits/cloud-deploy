/*
 * Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.cloud.deployer.process;

import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Kind of change to Deployment process state.
 */
public enum ChangeKind {
    /**
     * Process just started.
     */
    PROCESS_STARTED,

    /**
     * Inspection process started.
     */
    INSPECTION_STARTED,

    /**
     * Configuration was added by inspection.
     */
    CONFIGURATION_ADDED,

    /**
     * Inspection process finished.
     */
    INSPECTION_FINISHED,

    /**
     * Process is ready for configuration.
     */
    CONFIGURATION_STARTED,

    /**
     * Configuration was updated.
     */
    CONFIGURATION_SET,

    /**
     * Configuration was submitted as complete.
     */
    CONFIGURATION_FINISHED,

    /**
     * Artifact was stored into persistent storage.
     */
    ARTIFACT_STORED,
    /**
     * Provisioning process started.
     */
    PROVISION_STARTED,

    /**
     * Application container was created.
     */
    POD_CREATED,

    /**
     * Application container started.
     */
    DEPLOYMENT_READY,

    /**
     * Log output was captured
     */
    OUTPUT_LOGGED,

    /**
     * HTTP Mapping was created
     */
    INGRESS_CREATED,

    /**
     * HTTP Mapping is created
     */
    INGRESS_READY,

    /**
     * Provisioning finished, process complete.
     */
    PROVISION_FINISHED,

    /**
     * Process failed.
     */
    FAILED,

    /**
     * Cleanup of temporary resources started.
     */
    CLEANUP_STARTED,
    /**
     * Cleanup of temporary resources finished.
     */
    CLEANUP_FINISHED,
    
    /**
     * Deletion of deployment started
     */
    DELETE_STARTED;

    public Filter asFilter() {
        return new FilterLiteral(this);
    }

    public boolean isTerminal() {
        return this == FAILED || this == PROVISION_FINISHED;
    }

    StateChanged createEvent(DeploymentProcessState state) {
        return new StateChanged(state, this);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    public @interface Filter {
        ChangeKind value();
    }

    static class FilterLiteral implements Filter {
        private final ChangeKind value;

        FilterLiteral(ChangeKind value) {
            this.value = value;
        }

        @Override
        public ChangeKind value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Filter.class;
        }
    }
}
