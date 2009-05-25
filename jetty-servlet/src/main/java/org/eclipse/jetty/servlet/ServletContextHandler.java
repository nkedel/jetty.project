// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.eclipse.jetty.servlet;

import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.session.SessionHandler;


/* ------------------------------------------------------------ */
/** Servlet Context.
 * This extension to the ContextHandler allows for
 * simple construction of a context with ServletHandler and optionally
 * session and security handlers, et.<pre>
 *   new ServletContext("/context",Context.SESSIONS|Context.NO_SECURITY);
 * </pre>
 * <p/>
 * This class should have been called ServletContext, but this would have
 * cause confusion with {@link ServletContext}.
 */
public class ServletContextHandler extends ContextHandler
{   
    public final static int SESSIONS=1;
    public final static int SECURITY=2;
    public final static int NO_SESSIONS=0;
    public final static int NO_SECURITY=0;
    
    protected Class<? extends SecurityHandler> _defaultSecurityHandlerClass=org.eclipse.jetty.security.ConstraintSecurityHandler.class;
    protected SessionHandler _sessionHandler;
    protected SecurityHandler _securityHandler;
    protected ServletHandler _servletHandler;
    protected int _options;
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler()
    {
        this(null,null,null,null,null);
    }
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler(int options)
    {
        this(null,null,options);
    }
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, String contextPath)
    {
        this(parent,contextPath,null,null,null,null);
    }
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, String contextPath, int options)
    {
        this(parent,contextPath,null,null,null,null);
        _options=options;
    }
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, String contextPath, boolean sessions, boolean security)
    {
        this(parent,contextPath,(sessions?SESSIONS:0)|(security?SECURITY:0));
    }

    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler)
    {   
        this(parent,null,sessionHandler,securityHandler,servletHandler,errorHandler);
    }

    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, String contextPath, SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler)
    {   
        super((ContextHandler.Context)null);
        _scontext = new Context();
        _sessionHandler = sessionHandler;
        _securityHandler = securityHandler;
        _servletHandler = servletHandler;
            
        if (errorHandler!=null)
            setErrorHandler(errorHandler);

        if (contextPath!=null)
            setContextPath(contextPath);

        if (parent instanceof HandlerWrapper)
            ((HandlerWrapper)parent).setHandler(this);
        else if (parent instanceof HandlerCollection)
            ((HandlerCollection)parent).addHandler(this);
    }    

    
    /* ------------------------------------------------------------ */
    /** Get the defaultSecurityHandlerClass.
     * @return the defaultSecurityHandlerClass
     */
    public Class<? extends SecurityHandler> getDefaultSecurityHandlerClass()
    {
        return _defaultSecurityHandlerClass;
    }

    /* ------------------------------------------------------------ */
    /** Set the defaultSecurityHandlerClass.
     * @param defaultSecurityHandlerClass the defaultSecurityHandlerClass to set
     */
    public void setDefaultSecurityHandlerClass(Class<? extends SecurityHandler> defaultSecurityHandlerClass)
    {
        _defaultSecurityHandlerClass = defaultSecurityHandlerClass;
    }

    /* ------------------------------------------------------------ */
    protected SessionHandler newSessionHandler()
    {
        return new SessionHandler();
    }
    
    /* ------------------------------------------------------------ */
    protected SecurityHandler newSecurityHandler()
    {
        try
        {
            return (SecurityHandler)_defaultSecurityHandlerClass.newInstance();
        }
        catch(Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /* ------------------------------------------------------------ */
    protected ServletHandler newServletHandler()
    {
        return new ServletHandler();
    }

    /* ------------------------------------------------------------ */
    /**
     * Finish constructing handlers and link them together.
     * 
     * @see org.eclipse.jetty.server.handler.ContextHandler#startContext()
     */
    protected void startContext() throws Exception
    {
        // force creation of missing handlers.
        getSessionHandler();
        getSecurityHandler();
        getServletHandler();
        
        Handler handler = _servletHandler;
        if (_securityHandler!=null)
        {
            _securityHandler.setHandler(handler);
            handler=_securityHandler;
        }
        
        if (_sessionHandler!=null)
        {
            _sessionHandler.setHandler(handler);
            handler=_sessionHandler;
        }
        
        setHandler(handler);
        
    	super.startContext();

    	// OK to Initialize servlet handler now
    	if (_servletHandler != null && _servletHandler.isStarted())
    		_servletHandler.initialize();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the securityHandler.
     */
    public SecurityHandler getSecurityHandler()
    {
        if (_securityHandler==null && (_options&SECURITY)!=0 && !isStarted()) 
            _securityHandler=newSecurityHandler();
        
        return _securityHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the servletHandler.
     */
    public ServletHandler getServletHandler()
    {
        if (_servletHandler==null && !isStarted()) 
            _servletHandler=newServletHandler();
        return _servletHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the sessionHandler.
     */
    public SessionHandler getSessionHandler()
    {
        if (_sessionHandler==null && (_options&SESSIONS)!=0 && !isStarted()) 
            _sessionHandler=newSessionHandler();
        return _sessionHandler;
    }

    /* ------------------------------------------------------------ */
    /** conveniance method to add a servlet.
     */
    public ServletHolder addServlet(String className,String pathSpec)
    {
        return getServletHandler().addServletWithMapping(className, pathSpec);
    }

    /* ------------------------------------------------------------ */
    /** conveniance method to add a servlet.
     */
    public ServletHolder addServlet(Class<? extends Servlet> servlet,String pathSpec)
    {
        return getServletHandler().addServletWithMapping(servlet.getName(), pathSpec);
    }
    
    /* ------------------------------------------------------------ */
    /** conveniance method to add a servlet.
     */
    public void addServlet(ServletHolder servlet,String pathSpec)
    {
        getServletHandler().addServletWithMapping(servlet, pathSpec);
    }

    /* ------------------------------------------------------------ */
    /** conveniance method to add a filter
     */
    public void addFilter(FilterHolder holder,String pathSpec,int dispatches)
    {
        getServletHandler().addFilterWithMapping(holder,pathSpec,dispatches);
    }

    /* ------------------------------------------------------------ */
    /** convenience method to add a filter
     */
    public FilterHolder addFilter(Class<? extends Filter> filterClass,String pathSpec,int dispatches)
    {
        return getServletHandler().addFilterWithMapping(filterClass,pathSpec,dispatches);
    }

    /* ------------------------------------------------------------ */
    /** convenience method to add a filter
     */
    public FilterHolder addFilter(String filterClass,String pathSpec,int dispatches)
    {
        return getServletHandler().addFilterWithMapping(filterClass,pathSpec,dispatches);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param sessionHandler The sessionHandler to set.
     */
    public void setSessionHandler(SessionHandler sessionHandler)
    {
        if (isStarted())
            throw new IllegalStateException("STARTED");
        
        _sessionHandler = sessionHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param securityHandler The {@link org.eclipse.jetty.server.handler.SecurityHandler} to set on this context.
     */
    public void setSecurityHandler(SecurityHandler securityHandler)
    {
        if (isStarted())
            throw new IllegalStateException("STARTED");
        
        _securityHandler = securityHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param servletHandler The servletHandler to set.
     */
    public void setServletHandler(ServletHandler servletHandler)
    {
        if (isStarted())
            throw new IllegalStateException("STARTED");
        
        _servletHandler = servletHandler;
    }

    /* ------------------------------------------------------------ */
    public class Context extends ContextHandler.Context
    {

        /* ------------------------------------------------------------ */
        /* 
         * @see javax.servlet.ServletContext#getNamedDispatcher(java.lang.String)
         */
        public RequestDispatcher getNamedDispatcher(String name)
        {
            ContextHandler context=org.eclipse.jetty.servlet.ServletContextHandler.this;
            if (_servletHandler==null || _servletHandler.getServlet(name)==null)
                return null;
            return new Dispatcher(context, name);
        }


        /* ------------------------------------------------------------ */
        public void addFilterMappingForServletNames(String filterName, EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames)
        {
            if (isStarted())
                throw new IllegalStateException();
            ServletHandler handler = ServletContextHandler.this.getServletHandler();
            FilterMapping mapping = new FilterMapping();
            mapping.setFilterName(filterName);
            mapping.setServletNames(servletNames);
            mapping.setDispatcherTypes(dispatcherTypes);
            handler.addFilterMapping(mapping);
        }

        /* ------------------------------------------------------------ */
        public void addServletMapping(String servletName, String[] urlPatterns)
        {
            if (isStarted())
                throw new IllegalStateException();
            ServletHandler handler = ServletContextHandler.this.getServletHandler();
            ServletHolder holder= handler.newServletHolder();
            holder.setName(servletName);
            handler.addServlet(holder);
        }
        
        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addFilter(java.lang.String, java.lang.Class)
         */
        public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass)
        {
            if (isStarted())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final FilterHolder holder= handler.newFilterHolder();
            holder.setName(filterName);
            holder.setHeldClass(filterClass);
            handler.addFilter(holder);
            return holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addFilter(java.lang.String, java.lang.String)
         */
        public FilterRegistration.Dynamic addFilter(String filterName, String className)
        {
            if (isStarted())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final FilterHolder holder= handler.newFilterHolder();
            holder.setName(filterName);
            holder.setClassName(className);
            handler.addFilter(holder);
            return holder.getRegistration();
        }


        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addFilter(java.lang.String, javax.servlet.Filter)
         */
        public FilterRegistration.Dynamic addFilter(String filterName, Filter filter)
        {
            if (isStarted())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final FilterHolder holder= handler.newFilterHolder();
            holder.setName(filterName);
            holder.setFilter(filter);
            handler.addFilter(holder);
            return holder.getRegistration();
        }
        
        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addServlet(java.lang.String, java.lang.Class)
         */
        public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass)
        {
            if (!isStarting())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final ServletHolder holder= handler.newServletHolder();
            holder.setName(servletName);
            holder.setHeldClass(servletClass);
            handler.addServlet(holder);
            return holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addServlet(java.lang.String, java.lang.String)
         */
        public ServletRegistration.Dynamic addServlet(String servletName, String className)
        {
            if (!isStarting())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final ServletHolder holder= handler.newServletHolder();
            holder.setName(servletName);
            holder.setClassName(className);
            handler.addServlet(holder);
            return holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addServlet(java.lang.String, javax.servlet.Servlet)
         */
        public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet)
        {
            if (!isStarting())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final ServletHolder holder= handler.newServletHolder();
            holder.setName(servletName);
            holder.setServlet(servlet);
            handler.addServlet(holder);
            return holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#findFilterRegistration(java.lang.String)
         */
        public FilterRegistration findFilterRegistration(String filterName)
        {
            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final FilterHolder holder=handler.getFilter(filterName);
            return (holder==null)?null:holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#findServletRegistration(java.lang.String)
         */
        public ServletRegistration findServletRegistration(String servletName)
        {
            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final ServletHolder holder=handler.getServlet(servletName);
            return (holder==null)?null:holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#createFilter(java.lang.Class)
         */
        public <T extends Filter> T createFilter(Class<T> c) throws ServletException
        {
            // TODO Not implemented
            return null;
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#createServlet(java.lang.Class)
         */
        public <T extends Servlet> T createServlet(Class<T> c) throws ServletException
        {
            // TODO Not implemented
            return null;
        }


    }
}
