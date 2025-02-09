/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.naming.remote;

import java.io.IOException;
import java.util.Hashtable;
import java.util.function.Function;
import javax.naming.Context;
import org.jboss.as.naming.NamingContext;
import org.jboss.as.naming.NamingStore;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client.remote.RemoteNamingService;

/**
 * @author John Bailey
 */
public class RemoteNamingServerService implements Service<RemoteNamingService> {
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("naming", "remote");
    // The only classes that need to be unmarshalled are the Name impls in javax.naming. We don't allow remote bind/rebind
    // so no need for arbitrary classes.
    private static final Function<String, Boolean> NAME_ONLY_CLASS_RESOLUTION_FILTER = name -> name.startsWith("javax.naming.");
    private final InjectedValue<Endpoint> endpoint = new InjectedValue<Endpoint>();
    private final InjectedValue<NamingStore> namingStore = new InjectedValue<NamingStore>();
    private RemoteNamingService remoteNamingService;

    public synchronized void start(StartContext context) throws StartException {
        try {
            final Context namingContext = new NamingContext(namingStore.getValue(), new Hashtable<String, Object>());
            remoteNamingService = new RemoteNamingService(namingContext, NAME_ONLY_CLASS_RESOLUTION_FILTER);
            remoteNamingService.start(endpoint.getValue());
        } catch (Exception e) {
            throw new StartException("Failed to start remote naming service", e);
        }
    }

    public synchronized void stop(StopContext context) {
        try {
            remoteNamingService.stop();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stop remote naming service", e);
        }
    }

    public synchronized RemoteNamingService getValue() throws IllegalStateException, IllegalArgumentException {
        return remoteNamingService;
    }

    public Injector<Endpoint> getEndpointInjector() {
        return endpoint;
    }

    public Injector<NamingStore> getNamingStoreInjector() {
        return namingStore;
    }
}
