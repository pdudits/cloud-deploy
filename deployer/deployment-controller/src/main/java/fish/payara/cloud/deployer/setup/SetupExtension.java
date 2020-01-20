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

package fish.payara.cloud.deployer.setup;

import fish.payara.cloud.deployer.provisioning.Provisioner;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterTypeDiscovery;
import javax.enterprise.inject.spi.Extension;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SetupExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(SetupExtension.class.getName());

    private Config config;

    void setup(@Observes AfterTypeDiscovery afterTypeDiscovery) {
        config = ConfigProvider.getConfig(getClass().getClassLoader());
        var storage = lookupOption("artifactstorage", StorageSetup.class, StorageSetup.MOCK);

        LOGGER.info("Selected artifact storage "+storage);
        afterTypeDiscovery.getAlternatives().add(storage.alternative);

        var provisioning = lookupOption("provisioning", ProvisioningSetup.class, ProvisioningSetup.MOCK);
        LOGGER.info("Selected provisioning kind "+provisioning);
        afterTypeDiscovery.getAlternatives().add(provisioning.alternative);
    }

    private <T extends Enum<T>> T lookupOption(String propertyName, Class<T> enumType, T defaultValue) {
        return config.getOptionalValue(propertyName, String.class)
                .flatMap(value -> safeConvert(propertyName, value, enumType))
                .orElse(defaultValue);
    }

    private <T extends Enum<T>> Optional<T> safeConvert(String propertyName, String value, Class<T> enumType) {
        try {
            return Optional.of(Enum.valueOf(enumType, value.toUpperCase()));
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Invalid value of property "+propertyName+" ["+value+"]. Will fallback to default");
        }
        return Optional.empty();
    }
}
