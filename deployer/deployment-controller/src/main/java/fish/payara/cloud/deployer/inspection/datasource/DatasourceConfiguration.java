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

package fish.payara.cloud.deployer.inspection.datasource;

import fish.payara.cloud.deployer.configuration.ConfigurationSubfactory;
import fish.payara.cloud.deployer.process.Configuration;

import javax.enterprise.context.ApplicationScoped;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DatasourceConfiguration extends Configuration {
    static final String DEFAULT_URL = "<default>";
    public static final String DEFAULT_NAME = "java:comp/DefaultDataSource";
    public static final String KIND = "dataSource";

    private final EnumMap<Key, String> defaultValues = new EnumMap<>(Key.class);

    DatasourceConfiguration(String jndiName) {
        super(jndiName);
        for (Key key : Key.values()) {
            key.defaultValue.ifPresent(val -> defaultValues.put(key, val));
        }
        defaultValues.put(Key.poolName, jndiName.replaceAll("\\W","_"));
    }

    static DatasourceConfiguration createDefault() {
        var result = new DatasourceConfiguration(DEFAULT_NAME);
        result.defaultValues.put(Key.jdbcUrl, DEFAULT_URL);
        return result;
    }

    private void importDefaults(Map<String, String> defaultValues) {
        for (var defaultEntry : defaultValues.entrySet()) {
            try {
                Key key = Key.valueOf(defaultEntry.getKey());
                this.defaultValues.put(key, defaultEntry.getValue());
            } catch (IllegalArgumentException iae) {
                // probably old or user generated configuration object with a typo
            }
        }
    }

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Set<String> getKeys() {
        return Stream.of(Key.values()).map(Key::name).collect(Collectors.toSet());
    }

    @Override
    public boolean isRequired(String key) {
        return find(key).filter(Key::isRequired).isPresent();
    }

    @Override
    public Optional<String> getDefaultValue(String key) {
        return find(key).map(defaultValues::get);
    }

    @Override
    protected void checkUpdate(UpdateContext context) {
        super.checkUpdate(context);
        for (Key value : Key.values()) {
            if (context.updatedKeys().contains(value.name())) {
                value.validator.accept(context.key(value.name()));
            }
        }
        Optional<Integer> maxPoolSize = context.key(Key.steadyPoolSize.name()).convertIfValid(Integer::parseInt);
        Optional<Integer> steadyPoolSize = context.key(Key.steadyPoolSize.name()).convertIfValid(Integer::parseInt);
        if (maxPoolSize.isPresent() && steadyPoolSize.isPresent() && steadyPoolSize.get() > maxPoolSize.get()) {
            context.addValidationError("Steady pool size much be smaller or equal to maxPoolSize");
        }
        if (!context.updatedKeys().isEmpty()) {
            context.key(Key.jdbcUrl.name()).check(value -> {
                if (DEFAULT_URL.equals(value)) {
                    context.addValidationError("URL must be defined when any of the attributes are overriden");
                }
            });
        }
    }

    private Optional<Key> find(String key) {
        try {
            return Optional.of(Key.valueOf(key));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }


    enum Key {
        steadyPoolSize("1", Integer::parseInt, Key::assertNonNegative),
        maxPoolSize("10", Integer::parseInt, Key::assertPositive),
        maxWaitTime("30000", Integer::parseInt, Key::assertPositive),
        jdbcUrl,
        datasourceClass("org.h2.jdbcx.JdbcDataSource"),
        resourceType("javax.sql.DataSource"),
        user,
        password,
        poolName;

        private final Optional<String> defaultValue;
        Consumer<UpdateContext> validator;

        Key() {
            this.validator = (c) -> {};
            this.defaultValue = Optional.empty();
        }

        Key(String defaultValue) {
            this.validator = (c) -> {};
            this.defaultValue = Optional.of(defaultValue);
        }

        Key(String defaultValue, Consumer<String> checkFunction) {
            this.defaultValue = Optional.ofNullable(defaultValue);
            this.validator = (c) -> c.check(checkFunction);
        }

        <T> Key(String defaultValue, Function<String, T> converter, Consumer<T> check) {
            this.defaultValue = Optional.ofNullable(defaultValue);
            this.validator = (c) -> c.convertAndCheck(converter, check);
        }

        boolean isRequired() {
            return this != user && this != password;
        }

        private static void assertNonNegative(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Value must be non-negative integer");
            }
        }

        private static void assertPositive(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Value must be non-negative integer");
            }
        }

    }

    // extremely barebone mapper, will need more general separation between data storage, validation and interpretation
    static Value createValue(Configuration conf) {
        if (!conf.getKind().equals(KIND)) {
            throw new IllegalArgumentException("Configuration is not a Datasource configuration");
        }
        return (Value) Proxy.newProxyInstance(Value.class.getClassLoader(), new Class[]{Value.class}, (proxy, method, args) -> {
            Optional<String> value = conf.getValue(method.getName());
            if (method.getReturnType().equals(Optional.class)) {
                // at this time we only support Optional<String>
                return value;
            } else if (!value.isPresent()) {
                throw new IllegalArgumentException("required value for "+method.getName()+" is missing");
            } else if (method.getReturnType().equals(int.class)) {
                return Integer.parseInt(value.get());
            } else {
                return value.get();
            }
        });
    }

    public interface Value {
        int steadyPoolSize();
        int maxPoolSize();
        int maxWaitTime();
        String jdbcUrl();
        Optional<String> user();
        Optional<String> password();
        String poolName();
        String resourceType();
        String datasourceClass();

        static Value forConfiguration(Configuration c) {
            return createValue(c);
        }
    }

    @ApplicationScoped
    static class Subfactory implements ConfigurationSubfactory {
        @Override
        public boolean supportsKind(String kind) {
            return KIND.equals(kind);
        }

        @Override
        public Configuration importConfiguration(String kind, String id, Map<String, String> defaultValues) {
            var config = DEFAULT_NAME.equals(id) ? createDefault() : new DatasourceConfiguration(id);
            if (defaultValues != null) {
                config.importDefaults(defaultValues);
            }
            return config;
        }
    }


}
