/**
 * Copyright 2011 Asakusa Framework Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.asakusafw.yaess.core;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.asakusafw.yaess.core.util.PropertiesUtil;

/**
 * A common service profile format for configurable instances.
<pre><code>
&lt;prefix&gt; = &lt;fully qualified class name which extends T&gt;
&lt;prefix&gt;.&lt;key1&gt; = &lt;value1&gt;
&lt;prefix&gt;.&lt;key2&gt; = &lt;value2&gt;
...
</code></pre>
 * @param <T> the base service class
 * @since 0.2.3
 */
public class ServiceProfile<T extends Service> {

    static final Logger LOG = LoggerFactory.getLogger(ServiceProfile.class);

    private final String prefix;

    private final Class<? extends T> serviceClass;

    private final Map<String, String> configuration;

    private final ClassLoader classLoader;

    /**
     * Creates a new instance.
     * @param prefix the key prefix of this profile
     * @param serviceClass the service class
     * @param configuration configuration for service class
     * @param classLoader the class loader which loaded the service class
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public ServiceProfile(
            String prefix,
            Class<? extends T> serviceClass,
            Map<String, String> configuration,
            ClassLoader classLoader) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix must not be null"); //$NON-NLS-1$
        }
        if (serviceClass == null) {
            throw new IllegalArgumentException("serviceClass must not be null"); //$NON-NLS-1$
        }
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null"); //$NON-NLS-1$
        }
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader must not be null"); //$NON-NLS-1$
        }
        this.prefix = prefix;
        this.serviceClass = serviceClass;
        this.configuration = Collections.unmodifiableMap(new TreeMap<String, String>(configuration));
        this.classLoader = classLoader;
    }

    /**
     * Returns the key prefix of this profile.
     * @return the prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns the service class.
     * @return the service class
     */
    public Class<? extends T> getServiceClass() {
        return serviceClass;
    }

    /**
     * Return the optional configuration for the service.
     * @return the configuration
     */
    public Map<String, String> getConfiguration() {
        return configuration;
    }

    /**
     * Returns the class loader which loaded this service class.
     * @return the class loader
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Creates a new instance.
     * The created service will automatically {@link Service#configure(ServiceProfile, VariableResolver) configured}
     * by using this profile.
     * @param variables the variable resolver
     * @return the created instance.
     * @throws InterruptedException if interrupted in configuring the target service
     * @throws IOException if failed to create or configure the service
     */
    public T newInstance(VariableResolver variables) throws InterruptedException, IOException {
        if (variables == null) {
            throw new IllegalArgumentException("variables must not be null"); //$NON-NLS-1$
        }
        LOG.debug("Creating new instance for {}: {}", prefix, serviceClass.getName());
        T instance;
        try {
            instance = serviceClass.newInstance();
        } catch (Exception e) {
            throw new IOException(MessageFormat.format(
                    "Failed to create a new service instance for {0}: {1}",
                    getPrefix(),
                    getServiceClass().getName()), e);
        }
        instance.configure(this, variables);
        return instance;
    }

    /**
     * Loads a service profile with the specified key prefix.
     * @param <T> the base class of target class
     * @param properties source properties
     * @param prefix the key prefix
     * @param serviceBaseClass the base class of service class
     * @param classLoader the class loader to load the service class
     * @return the loaded profile
     * @throws IllegalArgumentException if the target profile is invalid, or parameters contain {@code null}
     */
    public static <T extends Service> ServiceProfile<T> load(
            Properties properties,
            String prefix,
            Class<T> serviceBaseClass,
            ClassLoader classLoader) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null"); //$NON-NLS-1$
        }
        if (prefix == null) {
            throw new IllegalArgumentException("prefix must not be null"); //$NON-NLS-1$
        }
        if (serviceBaseClass == null) {
            throw new IllegalArgumentException("serviceBaseClass must not be null"); //$NON-NLS-1$
        }
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader must not be null"); //$NON-NLS-1$
        }
        String targetClassName = properties.getProperty(prefix);
        if (targetClassName == null) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "\"{0}\" is not defined in properties",
                    prefix));
        }
        Class<?> loaded;
        try {
            loaded = classLoader.loadClass(targetClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "Faild load a service class defined in \"{0}\": {1}",
                    prefix,
                    targetClassName));
        }
        if (serviceBaseClass.isAssignableFrom(loaded) == false) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "Invalid service class defined in \"{0}\", it must be subtype of {2}: {1}",
                    prefix,
                    targetClassName,
                    serviceBaseClass.getName()));
        }
        Class<? extends T> targetClass = loaded.asSubclass(serviceBaseClass);
        Map<String, String> conf = PropertiesUtil.createPrefixMap(properties, prefix + '.');
        return new ServiceProfile<T>(prefix, targetClass, conf, classLoader);
    }

    /**
     * Merges this profile into the specified properties.
     * If properties already contains entries related to this profile,
     * then this method will overwrite them.
     * @param properties target properties
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public void storeTo(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null"); //$NON-NLS-1$
        }
        properties.setProperty(prefix, getServiceClass().getName());
        for (Map.Entry<String, String> entry : getConfiguration().entrySet()) {
            properties.setProperty(prefix + '.' + entry.getKey(), entry.getValue());
        }
    }
}