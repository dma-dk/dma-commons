/*
 * Copyright (c) 2008 Kasper Nielsen.
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
package dk.dma.app.application;

import static java.util.Objects.requireNonNull;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import javax.management.DynamicMBean;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;

import dk.dma.app.management.Managements;

/**
 * 
 * @author Kasper Nielsen
 */
public abstract class AbstractDmaApplication {

    /** The name of the application. */
    final String applicationName;

    volatile Injector injector;

    private final List<Module> modules = new ArrayList<>();

    final CopyOnWriteArrayList<Service> services = new CopyOnWriteArrayList<>();

    AbstractDmaApplication() {
        applicationName = getClass().getSimpleName();
    }

    /**
     * Clients should use one of {@link AbstractCommandLineTool}, {@link AbstractSwingApplication} or
     * {@link AbstractWebApplication}.
     * 
     * @param applicationName
     *            the name of the application
     */
    AbstractDmaApplication(String applicationName) {
        this.applicationName = requireNonNull(applicationName);
    }

    protected synchronized void addModule(Module module) {
        modules.add(requireNonNull(module));
    }

    protected void addPropertyFile(String name) {}

    // A required properties
    protected void addPropertyFileOnClasspath(String name) {};

    protected void configure() {}

    private void defaultModule() {
        addModule(new AbstractModule() {

            @Override
            protected void configure() {
                Names.bindProperties(binder(), Collections.singletonMap("app.name", applicationName));
            }
        });
    }

    public final String getApplicationName() {
        return applicationName;
    }

    protected <T extends Service> T start(T service) {
        services.add(requireNonNull(service));
        service.startAndWait();
        return service;
    }

    protected abstract void run(Injector injector) throws Exception;

    void execute() throws Exception {
        defaultModule();
        Injector i = Guice.createInjector(modules);
        // Management
        tryManage(this);
        run(i);
        // Shutdown in reverse order
        Collections.reverse(services);
        for (Service s : services) {
            s.stopAndWait();
        }
    }

    protected void shutdown() {
        for (Service s : services) {
            s.stopAndWait();
        }
    }

    void awaitServiceStopped(Service s) throws InterruptedException {
        State st = s.state();
        CountDownLatch cdl = null;
        while (st == State.RUNNING || st == State.NEW) {
            if (cdl != null) {
                cdl.await();
            }
            final CountDownLatch c = cdl = new CountDownLatch(1);
            s.addListener(new Service.Listener() {
                public void terminated(State from) {
                    c.countDown();
                }

                @Override
                public void stopping(State from) {
                    c.countDown();
                }

                @Override
                public void starting() {
                    c.countDown();
                }

                @Override
                public void running() {
                    c.countDown();
                }

                @Override
                public void failed(State from, Throwable failure) {
                    c.countDown();
                }
            }, MoreExecutors.sameThreadExecutor());
        }
    }

    private void tryManage(Object o) throws Exception {
        DynamicMBean mbean = Managements.tryCreate(this);
        if (mbean != null) {
            MBeanServer mb = ManagementFactory.getPlatformMBeanServer();
            Class<?> c = o.getClass();
            ObjectName objectName = new ObjectName(c.getPackage().getName() + ":type=" + c.getSimpleName());
            mb.registerMBean(mbean, objectName);
        }
    }
}