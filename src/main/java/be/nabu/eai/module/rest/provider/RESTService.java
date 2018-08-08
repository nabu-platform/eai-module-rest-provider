package be.nabu.eai.module.rest.provider;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.eai.module.authorization.vm.VMAuthorizationService;
import be.nabu.eai.module.authorization.vm.VMServiceAuthorizer;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.application.WebFragmentConfiguration;
import be.nabu.eai.module.web.application.api.PermissionWithRole;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.artifacts.container.BaseContainerArtifact;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.server.HTTPServerUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceAuthorizer;
import be.nabu.libs.services.api.ServiceAuthorizerProvider;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInstanceWithPipeline;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.vm.VMServiceInstance;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;

public class RESTService extends BaseContainerArtifact implements WebFragment, DefinedService, ServiceAuthorizerProvider {

	public static class PermissionImplementation implements PermissionWithRole {
		
		private String context;
		private String action;
		private List<String> roles;

		public PermissionImplementation() {
			// auto
		}
		
		public PermissionImplementation(String context, String action, List<String> roles) {
			this.context = context;
			this.action = action;
			this.roles = roles;
		}
		
		@Override
		public String getContext() {
			return context;
		}

		public void setContext(String context) {
			this.context = context;
		}

		@Override
		public String getAction() {
			return action;
		}
		public void setAction(String action) {
			this.action = action;
		}

		@Override
		public List<String> getRoles() {
			return roles;
		}
		public void setRoles(List<String> roles) {
			this.roles = roles;
		}
	}

	private Map<String, EventSubscription<?, ?>> subscriptions = new HashMap<String, EventSubscription<?, ?>>();
	
	public RESTService(String id) {
		super(id);
	}

	private String getKey(WebApplication artifact, String path) {
		return artifact.getId() + ":" + path;
	}
	
	@Override
	public void start(WebApplication artifact, String path) throws IOException {
		String key = getKey(artifact, path);
		if (subscriptions.containsKey(key)) {
			stop(artifact, path);
		}
		String restPath = artifact.getServerPath();
		if (path != null && !path.isEmpty() && !path.equals("/")) {
			if (!restPath.endsWith("/")) {
				restPath += "/";
			}
			restPath += path.replaceFirst("^[/]+", "");
		}
		synchronized(subscriptions) {
			RESTFragmentListener listener = new RESTFragmentListener(
				artifact,
				restPath, 
				getArtifact(RESTInterfaceArtifact.class),
				this, 
				artifact.getConfiguration().getCharset() == null ? Charset.defaultCharset() : Charset.forName(artifact.getConfiguration().getCharset()), 
				!EAIResourceRepository.isDevelopment(),
				(DefinedService) getArtifact("cache")
			);
			EventSubscription<HTTPRequest, HTTPResponse> subscription = artifact.getDispatcher().subscribe(HTTPRequest.class, listener);
			subscription.filter(HTTPServerUtils.limitToPath(restPath));
			subscriptions.put(key, subscription);
		}
	}

	@Override
	public void stop(WebApplication artifact, String path) {
		String key = getKey(artifact, path);
		if (subscriptions.containsKey(key)) {
			synchronized(subscriptions) {
				if (subscriptions.containsKey(key)) {
					subscriptions.get(key).unsubscribe();
					subscriptions.remove(key);
				}
			}
		}
	}
	
	@SuppressWarnings("unused")
	private String getPath(String parent) throws IOException {
		RESTInterfaceArtifact artifact = getArtifact(RESTInterfaceArtifact.class);
		if (artifact.getConfiguration().getPath() == null || artifact.getConfiguration().getPath().isEmpty() || artifact.getConfiguration().getPath().trim().equals("/")) {
			return parent;
		}
		else {
			if (parent == null) {
				return artifact.getConfiguration().getPath().trim();
			}
			else {
				return parent.replaceFirst("[/]+$", "") + "/" + artifact.getConfiguration().getPath().trim().replaceFirst("^[/]+", "");
			}
		}
	}

	@Override
	public List<Permission> getPermissions(WebApplication webArtifact, String path) {
		List<Permission> permissions = new ArrayList<Permission>();
		RESTInterfaceArtifact artifact = getArtifact(RESTInterfaceArtifact.class);
		if (artifact.getConfig().getPermissionAction() != null || artifact.getConfig().getRoles() != null) {
			permissions.add(new PermissionImplementation(artifact.getConfig().getPermissionContext(), artifact.getConfig().getPermissionAction(), artifact.getConfig().getRoles()));
		}
		return permissions;
	}

	@Override
	public boolean isStarted(WebApplication artifact, String path) {
		return subscriptions.containsKey(getKey(artifact, path));
	}

	@Override
	public ServiceInterface getServiceInterface() {
		SimpleVMServiceDefinition artifact = getArtifact(SimpleVMServiceDefinition.class);
		return artifact.getServiceInterface();
	}

	@Override
	public ServiceInstance newInstance() {
		SimpleVMServiceDefinition artifact = getArtifact(SimpleVMServiceDefinition.class);
		final VMServiceInstance newInstance = artifact.newInstance();
		return new ServiceInstanceWithPipeline() {
			@Override
			public Service getDefinition() {
				return RESTService.this;
			}
			@Override
			public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
				return newInstance.execute(executionContext, input);
			}
			@Override
			public ComplexContent getPipeline() {
				return newInstance.getPipeline();
			}
		};
	}

	@Override
	public Set<String> getReferences() {
		return new HashSet<String>();
	}

	@Override
	public ServiceAuthorizer getAuthorizer(ServiceRuntime runtime) {
		// only run the authorization if it is a root service
		if (runtime.getParent() == null && (runtime.getService().equals(this) || runtime.getService().equals(getArtifact("implementation")))) {
			VMAuthorizationService artifact = getArtifact("security");
			if (artifact != null) {
				return new VMServiceAuthorizer(artifact);
			}
		}
		return null;
	}

	@Override
	public List<WebFragmentConfiguration> getFragmentConfiguration() {
		List<WebFragmentConfiguration> configuration = new ArrayList<WebFragmentConfiguration>();
		final RESTInterfaceArtifact artifact = getArtifact(RESTInterfaceArtifact.class);
		if (artifact != null) {
			try {
				final String path = artifact.getConfiguration().getPath();
				final DefinedType configurationType = artifact.getConfiguration().getConfigurationType();
				if (configurationType != null) {
					configuration.add(new WebFragmentConfiguration() {
						@Override
						public ComplexType getType() {
							return (ComplexType) configurationType;
						}
						@Override
						public String getPath() {
							return path;
						}
					});
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return configuration;
	}

	@Override
	public Map<String, String> getConfiguration(Artifact child) {
		Map<String, String> configuration = super.getConfiguration(child);
		if (child instanceof VMService) {
			configuration.put("actualId", getId());
		}
		return configuration;
	}

}
