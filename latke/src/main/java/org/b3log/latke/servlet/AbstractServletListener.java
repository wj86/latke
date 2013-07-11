/*
 * Copyright (c) 2009, 2010, 2011, 2012, 2013, B3log Team
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
package org.b3log.latke.servlet;


import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import org.b3log.latke.Latkes;
import org.b3log.latke.cron.CronService;
import org.b3log.latke.ioc.LatkeBeanManager;
import org.b3log.latke.ioc.Lifecycle;
import org.b3log.latke.ioc.bean.LatkeBean;
import org.b3log.latke.ioc.config.Discoverer;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.util.Stopwatchs;


/**
 * Abstract servlet listener.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.3.0, Apr 5, 2012
 */
public abstract class AbstractServletListener implements ServletContextListener, ServletRequestListener, HttpSessionListener {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(AbstractServletListener.class.getName());

    /**
     * Web root.
     */
    private static String webRoot;

    static {
        final URL resource = ClassLoader.class.getResource("/");

        if (null != resource) { // Running unit tests
            try {
                webRoot = URLDecoder.decode(resource.getPath(), "UTF-8");
                LOGGER.log(Level.INFO, "Classpath [{0}]", webRoot);
            } catch (final Exception e) {
                throw new RuntimeException("Decodes web root path failed", e);
            }
        }
    }

    /**
     * Initializes context, {@linkplain #webRoot web root}, locale and runtime environment.
     *
     * @param servletContextEvent servlet context event
     */
    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        Latkes.initRuntimeEnv();
        LOGGER.info("Initializing the context....");

        Latkes.setLocale(Locale.SIMPLIFIED_CHINESE);
        LOGGER.log(Level.INFO, "Default locale [{0}]", Latkes.getLocale());

        final ServletContext servletContext = servletContextEvent.getServletContext();

        webRoot = servletContext.getRealPath("") + File.separator;
        LOGGER.log(Level.INFO, "Server [webRoot={0}, contextPath={1}]",
            new Object[] {webRoot, servletContextEvent.getServletContext().getContextPath()});

        Stopwatchs.start("Init Latke IoC container");
        try {
            Stopwatchs.start("Discover bean classes");
            final Collection<Class<?>> beanClasses = Discoverer.discover(Latkes.getScanPath());

            Stopwatchs.end();

            Stopwatchs.start("Create beans");
            Lifecycle.startApplication(beanClasses); // Starts Latke IoC container
            Stopwatchs.end();

            final LatkeBeanManager beanManager = Lifecycle.getBeanManager();

            // Build processors
            final Set<LatkeBean<?>> processBeans = beanManager.getBeans(RequestProcessor.class);

            Stopwatchs.start("Build processor methods");
            RequestProcessors.buildProcessorMethods(processBeans);
            Stopwatchs.end();
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Initializes request processors failed", e);
            
            throw new IllegalStateException("Initializes request processors failed");
        } finally {
            Stopwatchs.end();
            
            LOGGER.debug(Stopwatchs.getTimingStat());
            
            Stopwatchs.release();
        }
        
        CronService.start();
    }

    /**
     * Destroys the context, unregisters remote JavaScript services.
     *
     * @param servletContextEvent servlet context event
     */
    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        LOGGER.info("Destroying the context....");
        Latkes.shutdown();
        // TODO: Stop cron jobs
    }

    @Override
    public void requestDestroyed(final ServletRequestEvent servletRequestEvent) {
        if (Latkes.runsWithJDBCDatabase()) {
            JdbcRepository.dispose();
        }
    }

    @Override
    public abstract void requestInitialized(final ServletRequestEvent servletRequestEvent);

    @Override
    public abstract void sessionCreated(final HttpSessionEvent httpSessionEvent);

    @Override
    public abstract void sessionDestroyed(final HttpSessionEvent httpSessionEvent);

    /**
     * Gets the absolute file path of web root directory on the server's file system.
     *
     * @return the directory file path(tailing with {@link File#separator}).
     */
    public static String getWebRoot() {
        return webRoot;
    }
}
