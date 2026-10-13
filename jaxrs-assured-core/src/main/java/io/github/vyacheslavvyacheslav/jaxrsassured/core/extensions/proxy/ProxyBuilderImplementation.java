package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.client.jaxrs.ProxyConfig;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ClientInvokerFactory;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ResteasyClientProxy;
import org.jboss.resteasy.util.IsHttpMethod;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.AssuredCall;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.ConfigurableRequest;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Set;

public class ProxyBuilderImplementation<T> extends ProxyBuilder<T> {

    private static final Class<?>[] cClassArgArray = {Class.class};
    private final Class<T> iface;
    private final WebTarget webTarget;
    private ClassLoader loader;
    private MediaType serverConsumes;
    private MediaType serverProduces;
    private final ClientProxyFactory clientProxyFactory;
    private final ResourceInvokerFactory invokerFactory;


    public ProxyBuilderImplementation(final Class<T> iface, final WebTarget webTarget,
                                      ClientProxyFactory clientProxyFactory,
                                      ResourceInvokerFactory invokerFactory) {
        this.loader = Thread.currentThread().getContextClassLoader();
        this.iface = iface;
        this.webTarget = webTarget;
        this.clientProxyFactory = clientProxyFactory;
        this.invokerFactory = invokerFactory;
    }

    /**
     * Локатор подресурса: без HTTP-метода, возвращает интерфейс, а путь задан на самом методе
     * или на возвращаемом интерфейсе (как в RestEasy, плюс второй вариант). Методы с HTTP-аннотацией
     * локатором не считаются, даже если их возвращаемый тип помечен @Path.
     */
    private static boolean isSubResourceLocator(Method method, Set<String> httpMethods) {
        Class<?> returnType = method.getReturnType();
        return (httpMethods == null || httpMethods.isEmpty())
                && returnType.isInterface()
                && (method.isAnnotationPresent(Path.class) || returnType.isAnnotationPresent(Path.class));
    }

    public ProxyBuilderImplementation<T> classloader(ClassLoader cl) {
        this.loader = cl;
        return this;
    }

    public ProxyBuilderImplementation<T> defaultProduces(MediaType type) {
        this.serverProduces = type;
        return this;
    }

    public ProxyBuilderImplementation<T> defaultConsumes(MediaType type) {
        this.serverConsumes = type;
        return this;
    }

    public ProxyBuilderImplementation<T> defaultProduces(String type) {
        this.serverProduces = MediaType.valueOf(type);
        return this;
    }

    public ProxyBuilderImplementation<T> defaultConsumes(String type) {
        this.serverConsumes = MediaType.valueOf(type);
        return this;
    }

    public T build() {
        return build(new ProxyConfig(loader, serverConsumes, serverProduces));
    }

    public T build(final ProxyConfig config) {
        return build(config, RequestConfiguration.EMPTY);
    }

    @SuppressWarnings("unchecked")
    public T build(final ProxyConfig config, RequestConfiguration requestConfiguration) {
        // на «чужом» target запросы шли бы через обычные инвокеры RestEasy: setHeader и AssuredResponse молча не работали бы
        if (!(webTarget instanceof ClientInvokerFactory)) {
            throw new IllegalStateException("Ресурс " + iface.getSimpleName()
                    + " нужно строить на клиенте из HttpClientUtil.restEasyClient(): "
                    + webTarget.getClass().getName() + " не создает Assured-инвокеры");
        }
        ProxyContext context = new ProxyContext(config, clientProxyFactory, invokerFactory);
        WebTarget base = webTarget;
        if (iface.isAnnotationPresent(Path.class)) {
            Path path = iface.getAnnotation(Path.class);
            if (!path.value().equals("") && !path.value().equals("/")) {
                base = base.path(path.value());
            }
        }
        HashMap<Method, MethodInvoker> methodMap = new HashMap<>();
        for (Method method : iface.getMethods()) {
            // ignore the as method to allow declaration in client interfaces
            if ("as".equals(method.getName()) && Arrays.equals(method.getParameterTypes(), cClassArgArray)) {
                continue;
            }
            MethodInvoker invoker;
            Set<String> httpMethods = IsHttpMethod.getHttpMethods(method);
            if (isSubResourceLocator(method, httpMethods)) {
                invoker = invokerFactory.createSubInvoker((ResteasyWebTarget) base, method, context);
            } else if (method.isAnnotationPresent(ConfigurableRequest.class) || method.isAnnotationPresent(AssuredCall.class)) {
                continue;
            } else if (httpMethods == null) {
                // ignore methods without http method annotations
                continue;
            } else {
                invoker = ((ClientInvokerFactory) base).createClientInvoker(iface, method, config);
            }
            methodMap.put(method, invoker);
        }

        Class<?>[] intfs = {iface, ResteasyClientProxy.class};

        ClientProxyInvocationHandler clientProxy = clientProxyFactory.createClientInvoker(methodMap, base, context,
                requestConfiguration);
        // this is done so that equals and hashCode work ok. Adding the proxy to a
        // Collection will cause equals and hashCode to be invoked. The Spring
        // infrastructure had some problems without this.
        clientProxy.setClazz(iface);

        ClassLoader cl = config.getLoader();
        try {
            cl.loadClass(iface.getName());
        } catch (Throwable t) {
            cl = new DelegateClassLoader(iface.getClassLoader(), cl);
        }

        return (T) Proxy.newProxyInstance(cl, intfs, clientProxy);
    }

    public class DelegateClassLoader extends java.security.SecureClassLoader {
        private final ClassLoader delegate;

        private final ClassLoader parent;

        public DelegateClassLoader(final ClassLoader delegate, final ClassLoader parent) {
            super(parent);
            this.delegate = delegate;
            this.parent = parent;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<?> loadClass(final String className) throws ClassNotFoundException {
            if (parent != null) {
                try {
                    return parent.loadClass(className);
                } catch (ClassNotFoundException cnfe) {
                    //NOOP, use delegate
                }
            }
            return delegate.loadClass(className);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public URL getResource(final String name) {
            URL url = null;
            if (parent != null) {
                url = parent.getResource(name);
            }
            return (url == null) ? delegate.getResource(name) : url;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            final java.util.ArrayList<Enumeration<URL>> foundResources = new java.util.ArrayList<>();

            foundResources.add(delegate.getResources(name));
            if (parent != null) {
                foundResources.add(parent.getResources(name));
            }

            return new Enumeration<URL>() {
                private int position = foundResources.size() - 1;

                public boolean hasMoreElements() {
                    while (position >= 0) {
                        if (foundResources.get(position).hasMoreElements()) {
                            return true;
                        }
                        position--;
                    }
                    return false;
                }

                public URL nextElement() {
                    while (position >= 0) {
                        try {
                            return foundResources.get(position).nextElement();
                        } catch (NoSuchElementException e) {
                        }
                        position--;
                    }
                    throw new NoSuchElementException();
                }
            };
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream getResourceAsStream(final String name) {
            InputStream is = null;
            if (parent != null) {
                is = parent.getResourceAsStream(name);
            }
            return (is == null) ? delegate.getResourceAsStream(name) : is;
        }
    }

}
