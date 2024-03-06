package be.nabu.eai.module.rest.provider;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.authorization.vm.VMAuthorizationService;
import be.nabu.eai.module.authorization.vm.VMServiceAuthorizer;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.module.web.application.RateLimitImpl;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.application.WebFragmentConfiguration;
import be.nabu.eai.module.web.application.api.PermissionWithRole;
import be.nabu.eai.module.web.application.api.RateLimit;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.artifacts.container.BaseContainerArtifact;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.artifacts.api.ArtifactWithExceptions;
import be.nabu.libs.artifacts.api.ArtifactWithTodo;
import be.nabu.libs.artifacts.api.ExceptionDescription;
import be.nabu.libs.artifacts.api.Feature;
import be.nabu.libs.artifacts.api.FeaturedArtifact;
import be.nabu.libs.artifacts.api.Todo;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.libs.evaluator.types.operations.TypesOperationProvider;
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
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.Throw;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.structure.Structure;

public class RESTService extends BaseContainerArtifact implements WebFragment, DefinedService, ServiceAuthorizerProvider, FeaturedArtifact, ArtifactWithExceptions, ArtifactWithTodo {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
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
	
	@Deprecated
	private void getAdditionalCodes(StepGroup group, List<Integer> codes) {
		for (Step step : group.getChildren()) {
			if (step instanceof Throw) {
				String code = ((Throw) step).getCode();
				if (code != null && code.matches("^[0-9]{3}$")) {
					int parsed = Integer.parseInt(code);
					if (!codes.contains(parsed)) {
						codes.add(parsed);
					}
				}
			}
			if (step instanceof StepGroup) {
				getAdditionalCodes((StepGroup) step, codes);
			}
		}
	}
	@Deprecated
	public List<Integer> getAdditionalCodes() {
		SimpleVMServiceDefinition service = getArtifact("implementation");
		List<Integer> codes = new ArrayList<Integer>();
		getAdditionalCodes(service.getRoot(), codes);
		return codes;
	}
	
	private Map<String, TypeOperation> analyzedOperations = new HashMap<String, TypeOperation>();
	public TypeOperation getOperation(String query) throws ParseException {
		if (!analyzedOperations.containsKey(query)) {
			synchronized(analyzedOperations) {
				if (!analyzedOperations.containsKey(query))
					analyzedOperations.put(query, (TypeOperation) new PathAnalyzer<ComplexContent>(new TypesOperationProvider()).analyze(QueryParser.getInstance().parse(query)));
			}
		}
		return analyzedOperations.get(query);
	}
	
	private void getAdditionalResponseCodes(StepGroup group, Map<Integer, Type> codes) {
		for (Step step : group.getChildren()) {
			if (step instanceof Throw) {
				String code = ((Throw) step).getCode();
				int parsed = code == null || !code.matches("^[0-9]{3}$") ? 500 : Integer.parseInt(code);
				String data = ((Throw) step).getData();
				if (data != null && data.startsWith("=") && ((Throw) step).isWhitelist() && codes.get(parsed) == null) {
					data = data.substring(1);
					try {
						TypeOperation operation = getOperation(data);
						ComplexType pipeline = group.getPipeline(EAIResourceRepository.getInstance().getServiceContext());
						Type dataType = operation.getReturnType(pipeline);
						codes.put(parsed, dataType);
					}
					catch (Exception e) {
						logger.error("Could not parse data query for throw: " + data, e);
					}
				}
				else if (!codes.containsKey(parsed)) {
					codes.put(parsed, null);
				}
			}
			if (step instanceof StepGroup) {
				getAdditionalResponseCodes((StepGroup) step, codes);
			}
		}
	}
	
	public Map<Integer, Type> getAdditionalResponseCodes() {
		SimpleVMServiceDefinition service = getArtifact("implementation");
		Map<Integer, Type> codes = new HashMap<Integer, Type>();
		getAdditionalResponseCodes(service.getRoot(), codes);
		return codes;
	}

	private Map<String, EventSubscription<?, ?>> subscriptions = new HashMap<String, EventSubscription<?, ?>>();
	
	public RESTService(String id) {
		super(id);
	}

	private String getKey(WebApplication artifact, String path) {
		return artifact.getId() + ":" + path;
	}
	
	public RESTInterfaceArtifact getInterface() {
		return getArtifact(RESTInterfaceArtifact.class);
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

	@Override
	public List<RateLimit> getRateLimits(WebApplication artifact, String path) {
		List<RateLimit> limits = new ArrayList<RateLimit>();
		RESTInterfaceArtifact iface = getArtifact(RESTInterfaceArtifact.class);
		if (iface != null) {
			limits.add(new RateLimitImpl(
				iface.getConfig().getRateLimitAction() == null ? getId() : iface.getConfig().getRateLimitAction(),
				iface.getConfig().getRateLimitContext()));
		}
		return limits;
	}

	@Override
	public List<Feature> getAvailableFeatures() {
		SimpleVMServiceDefinition artifact = getArtifact(SimpleVMServiceDefinition.class);
		return artifact != null ? artifact.getAvailableFeatures() : new ArrayList<Feature>();
	}

	@Override
	public List<ExceptionDescription> getExceptions() {
		SimpleVMServiceDefinition service = getArtifact("implementation");
		return service == null ? null : service.getExceptions();
	}
	@Override
	public List<Todo> getTodos() {
		SimpleVMServiceDefinition service = getArtifact("implementation");
		return service == null ? null : service.getTodos();
	}

}
