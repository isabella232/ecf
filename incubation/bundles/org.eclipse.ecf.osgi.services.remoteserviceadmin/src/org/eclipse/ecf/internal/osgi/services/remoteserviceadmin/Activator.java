package org.eclipse.ecf.internal.osgi.services.remoteserviceadmin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ecf.core.util.LogHelper;
import org.eclipse.ecf.core.util.SystemLogService;
import org.eclipse.ecf.discovery.IDiscoveryLocator;
import org.eclipse.ecf.discovery.IServiceInfo;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.AbstractEndpointDescriptionFactory;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.AbstractServiceInfoFactory;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.DefaultEndpointDescriptionFactory;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.DefaultServiceInfoFactory;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.IEndpointDescriptionFactory;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.IServiceInfoFactory;
import org.eclipse.equinox.concurrent.future.IExecutor;
import org.eclipse.equinox.concurrent.future.IProgressRunnable;
import org.eclipse.equinox.concurrent.future.ThreadsExecutor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class Activator implements BundleActivator {

	public static final String PLUGIN_ID = "org.eclipse.ecf.osgi.services.remoteserviceadmin";

	private static BundleContext context;
	private static Activator instance;

	static BundleContext getContext() {
		return context;
	}

	public static Activator getDefault() {
		return instance;
	}

	private AbstractServiceInfoFactory serviceInfoFactory;
	private ServiceRegistration serviceInfoFactoryRegistration;
	
	private AbstractEndpointDescriptionFactory endpointDescriptionFactory;
	private ServiceRegistration endpointDescriptionFactoryRegistration;
	
	private IExecutor executor;

	private LocatorServiceListener locatorServiceListener;
	private ServiceTracker locatorServiceTracker;

	private ServiceTracker endpointListenerServiceTracker;
	private Object endpointListenerServiceTrackerLock = new Object();

	private ServiceTracker logServiceTracker = null;
	private LogService logService = null;
	private Object logServiceTrackerLock = new Object();


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext bundleContext) throws Exception {
		Activator.context = bundleContext;
		Activator.instance = this;
		// For the service info factory and the endpointdescription factory
		// registration, set the service ranking property to Integer.MIN_VALUE so 
		// that any other registered factories will be preferred.
		final Properties properties = new Properties();
		properties.put(Constants.SERVICE_RANKING, new Integer(
				Integer.MIN_VALUE));
		serviceInfoFactory = new DefaultServiceInfoFactory();
		serviceInfoFactoryRegistration = context.registerService(IServiceInfoFactory.class.getName(), serviceInfoFactory, properties);
		// also endpoint description factory
		endpointDescriptionFactory = new DefaultEndpointDescriptionFactory();
		endpointDescriptionFactoryRegistration = context.registerService(IEndpointDescriptionFactory.class.getName(), endpointDescriptionFactory, properties);

		executor = new ThreadsExecutor();
		
		// Create service listener
		locatorServiceListener = new LocatorServiceListener();
		// Create locator service tracker
		locatorServiceTracker = new ServiceTracker(context,
				IDiscoveryLocator.class.getName(),
				new LocatorTrackerCustomizer());
		locatorServiceTracker.open();
		openLocators();
	}

	private void openLocators() {
		Object[] locators = locatorServiceTracker.getServices();
		if (locators != null) {
			for (int i = 0; i < locators.length; i++) {
				// Add service listener to locator
				openLocator((IDiscoveryLocator) locators[i]);
			}
		}
	}

	void openLocator(IDiscoveryLocator locator) {
		if (locator == null || context == null)
			return;
		locator.addServiceListener(locatorServiceListener);
		processInitialLocatorServices(locator);
	}

	void shutdownLocator(IDiscoveryLocator locator) {
		if (locator == null || context == null)
			return;
		locator.removeServiceListener(locatorServiceListener);
	}

	private void processInitialLocatorServices(final IDiscoveryLocator locator) {
		IProgressRunnable runnable = new IProgressRunnable() {
			public Object run(IProgressMonitor arg0) throws Exception {
				if (context == null)
					return null;
				IServiceInfo[] serviceInfos = locator.getServices();
				if (context == null)
					return null;
				for (int i = 0; i < serviceInfos.length; i++) {
					locatorServiceListener.handleService(serviceInfos[i], true);
				}
				return null;
			}
		};
		executor.execute(runnable, null);
	}

	void shutdownLocators() {
		Object[] locators = locatorServiceTracker.getServices();
		if (locators != null) {
			for (int i = 0; i < locators.length; i++) {
				// Add service listener to locator
				shutdownLocator((IDiscoveryLocator) locators[i]);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		if (endpointDescriptionFactoryRegistration != null) {
			endpointDescriptionFactoryRegistration.unregister();
			endpointDescriptionFactoryRegistration = null;
		}
		if (endpointDescriptionFactory != null) {
			endpointDescriptionFactory.close();
			endpointDescriptionFactory = null;
		}
		
		if (serviceInfoFactoryRegistration != null) {
			serviceInfoFactoryRegistration.unregister();
			serviceInfoFactoryRegistration = null;
		}
		if (serviceInfoFactory != null) {
			serviceInfoFactory.close();
			serviceInfoFactory = null;
		}
		
		synchronized (endpointListenerServiceTrackerLock) {
			if (endpointListenerServiceTracker != null) {
				endpointListenerServiceTracker.close();
				endpointListenerServiceTracker = null;
			}
		}
		synchronized (logServiceTrackerLock) {
			if (logServiceTracker != null) {
				logServiceTracker.close();
				logServiceTracker = null;
				logService = null;
			}
		}
		synchronized (endpointDescriptionFactoryServiceTrackerLock) {
			if (endpointDescriptionFactoryServiceTracker != null) {
				endpointDescriptionFactoryServiceTracker.close();
				endpointDescriptionFactoryServiceTracker = null;
			}
		}
		if (locatorServiceTracker != null) {
			locatorServiceTracker.close();
			locatorServiceTracker = null;
		}
		locatorServiceListener.close();
		executor = null;
		Activator.context = null;
		Activator.instance = null;
	}

	protected LogService getLogService() {
		if (context == null)
			return null;
		synchronized (logServiceTrackerLock) {
			if (logServiceTracker == null) {
				logServiceTracker = new ServiceTracker(context,
						LogService.class.getName(), null);
				logServiceTracker.open();
			}
			logService = (LogService) logServiceTracker.getService();
			if (logService == null)
				logService = new SystemLogService(PLUGIN_ID);
			return logService;
		}
	}

	public void log(IStatus status) {
		if (logService == null)
			logService = getLogService();
		if (logService != null)
			logService.log(null, LogHelper.getLogCode(status),
					LogHelper.getLogMessage(status), status.getException());
	}

	public void log(ServiceReference sr, IStatus status) {
		log(sr, LogHelper.getLogCode(status), LogHelper.getLogMessage(status),
				status.getException());
	}

	public void log(ServiceReference sr, int level, String message, Throwable t) {
		if (logService == null)
			logService = getLogService();
		if (logService != null)
			logService.log(sr, level, message, t);
	}

	private class LocatorTrackerCustomizer implements ServiceTrackerCustomizer {

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.osgi.util.tracker.ServiceTrackerCustomizer#addingService(org.
		 * osgi.framework.ServiceReference)
		 */
		public Object addingService(ServiceReference reference) {
			IDiscoveryLocator locator = (IDiscoveryLocator) context
					.getService(reference);
			openLocator(locator);
			return locator;
		}

		public void modifiedService(ServiceReference reference, Object service) {
		}

		public void removedService(ServiceReference reference, Object service) {
			shutdownLocator((IDiscoveryLocator) service);
		}
	}

	public EndpointListenerHolder[] getMatchingEndpointListenerHolders(
			EndpointDescription description) {
		synchronized (endpointListenerServiceTrackerLock) {
			if (context == null)
				return null;
			if (endpointListenerServiceTracker == null) {
				endpointListenerServiceTracker = new ServiceTracker(context,
						EndpointListener.class.getName(), null);
				endpointListenerServiceTracker.open();
			}
			return getMatchingEndpointListenerHolders(
					endpointListenerServiceTracker.getServiceReferences(),
					description);
		}
	}

	class EndpointListenerHolder {

		private EndpointListener listener;
		private EndpointDescription description;
		private String matchingFilter;

		public EndpointListenerHolder(EndpointListener l,
				EndpointDescription d, String f) {
			this.listener = l;
			this.matchingFilter = f;
		}

		public EndpointListener getListener() {
			return listener;
		}

		public EndpointDescription getDescription() {
			return description;
		}

		public String getMatchingFilter() {
			return matchingFilter;
		}
	}

	private EndpointListenerHolder[] getMatchingEndpointListenerHolders(
			ServiceReference[] refs, EndpointDescription description) {
		if (refs == null)
			return null;
		List results = new ArrayList();
		for (int i = 0; i < refs.length; i++) {
			EndpointListener listener = (EndpointListener) context
					.getService(refs[i]);
			if (listener == null)
				continue;
			List filters = getStringPlusProperty(
					EndpointListener.ENDPOINT_LISTENER_SCOPE,
					getMapFromProperties(refs[i]));
			String matchingFilter = isMatch(description, filters);
			if (matchingFilter != null)
				results.add(new EndpointListenerHolder(listener, description,
						matchingFilter));
		}
		return (EndpointListenerHolder[]) results
				.toArray(new EndpointListenerHolder[] {});
	}

	private String isMatch(EndpointDescription description, List filters) {
		for (Iterator j = filters.iterator(); j.hasNext();) {
			String filter = (String) j.next();
			if (description.matches(filter))
				return filter;
		}
		return null;
	}

	private Map getMapFromProperties(ServiceReference ref) {
		Map results = new HashMap();
		String[] keys = ref.getPropertyKeys();
		if (keys != null) {
			for (int i = 0; i < keys.length; i++) {
				results.put(keys[i], ref.getProperty(keys[i]));
			}
		}
		return results;
	}

	private List getStringPlusProperty(String key, Map properties) {
		Object value = properties.get(key);
		if (value == null) {
			return Collections.EMPTY_LIST;
		}

		if (value instanceof String) {
			return Collections.singletonList((String) value);
		}

		if (value instanceof String[]) {
			String[] values = (String[]) value;
			List result = new ArrayList(values.length);
			for (int i = 0; i < values.length; i++) {
				if (values[i] != null) {
					result.add(values[i]);
				}
			}
			return Collections.unmodifiableList(result);
		}

		if (value instanceof Collection) {
			Collection values = (Collection) value;
			List result = new ArrayList(values.size());
			for (Iterator iter = values.iterator(); iter.hasNext();) {
				Object v = iter.next();
				if (v instanceof String) {
					result.add((String) v);
				}
			}
			return Collections.unmodifiableList(result);
		}

		return Collections.EMPTY_LIST;
	}

	private Object endpointDescriptionFactoryServiceTrackerLock = new Object();
	private ServiceTracker endpointDescriptionFactoryServiceTracker;

	public IEndpointDescriptionFactory getEndpointDescriptionFactory() {
		synchronized (endpointDescriptionFactoryServiceTrackerLock) {
			if (context == null)
				return null;
			if (endpointDescriptionFactoryServiceTracker == null) {
				endpointDescriptionFactoryServiceTracker = new ServiceTracker(
						context, IEndpointDescriptionFactory.class.getName(),
						null);
				endpointDescriptionFactoryServiceTracker.open();
			}
			return (IEndpointDescriptionFactory) endpointDescriptionFactoryServiceTracker
					.getService();
		}
	}

	public String getFrameworkUUID() {
		if (context == null) return null;
		// code get and set the framework uuid property as specified in r2.enterprise.pdf pg 297
		synchronized ("org.osgi.framework.uuid") {
			String result = context.getProperty("org.osgi.framework.uuid");
			if (result == null) {
				UUID newUUID = UUID.randomUUID();
				result = newUUID.toString();
				System.setProperty("org.osgi.framework.uuid", newUUID.toString());
			}
			return result;
		}
	}

}
