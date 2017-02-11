package be.nabu.eai.module.rest.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.module.rest.SPIBindingProvider;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.eai.module.rest.api.BindingProvider;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.libs.authentication.api.Authenticator;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.DeviceValidator;
import be.nabu.libs.authentication.api.PermissionHandler;
import be.nabu.libs.authentication.api.RoleHandler;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.api.TokenValidator;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.impl.VariableOperation;
import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.libs.evaluator.types.operations.TypesOperationProvider;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.HTTPCodes;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.server.AuthenticationHeader;
import be.nabu.libs.http.api.server.Session;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.http.glue.impl.ResponseMethods;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.binding.api.MarshallableBinding;
import be.nabu.libs.types.binding.api.UnmarshallableBinding;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.form.FormBinding;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiableHeader;
import be.nabu.utils.mime.impl.FormatException;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.MimeUtils;
import be.nabu.utils.mime.impl.PlainMimeContentPart;

public class RESTFragmentListener implements EventHandler<HTTPRequest, HTTPResponse> {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private PathAnalysis pathAnalysis;
	private String serverPath;
	private DefinedService service;
	private Charset charset;
	private boolean allowEncoding;
	private RESTInterfaceArtifact webArtifact;
	private WebApplication webApplication;
	private ComplexContent configuration;
	private BindingProvider bindingProvider;
	
	private Map<String, TypeOperation> analyzedOperations = new HashMap<String, TypeOperation>();

	public RESTFragmentListener(WebApplication webApplication, String serverPath, RESTInterfaceArtifact webArtifact, DefinedService service, Charset charset, boolean allowEncoding) throws IOException {
		this.webApplication = webApplication;
		this.serverPath = serverPath;
		this.webArtifact = webArtifact;
		this.service = service;
		this.charset = charset;
		this.allowEncoding = allowEncoding;
		String path = webArtifact.getConfiguration().getPath();
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		this.pathAnalysis = GlueListener.analyzePath(path);
		DefinedType configurationType = webArtifact.getConfiguration().getConfigurationType();
		if (configurationType != null) {
			String configurationPath = serverPath == null ? "/" : serverPath;
			if (!configurationPath.endsWith("/")) {
				configurationPath += "/";
			}
			configurationPath += path;
			this.configuration = webApplication.getConfigurationFor(configurationPath, (ComplexType) configurationType);
		}
	}
	
	protected TypeOperation getOperation(String query) throws ParseException {
		if (!analyzedOperations.containsKey(query)) {
			synchronized(analyzedOperations) {
				if (!analyzedOperations.containsKey(query))
					analyzedOperations.put(query, (TypeOperation) new PathAnalyzer<ComplexContent>(new TypesOperationProvider()).analyze(QueryParser.getInstance().parse(query)));
			}
		}
		return analyzedOperations.get(query);
	}
	
	protected Object getVariable(ComplexContent pipeline, String query) throws ServiceException {
		VariableOperation.registerRoot();
		try {
			return getOperation(query).evaluate(pipeline);
		}
		catch (EvaluationException e) {
			throw new ServiceException(e);
		}
		catch (ParseException e) {
			throw new ServiceException(e);
		}
		finally {
			VariableOperation.unregisterRoot();
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public HTTPResponse handle(HTTPRequest request) {
		try {
			ServiceRuntime.setGlobalContext(new HashMap<String, Object>());
			// stop fast if wrong method
			if (webArtifact.getConfiguration().getMethod() != null && !webArtifact.getConfiguration().getMethod().toString().equalsIgnoreCase(request.getMethod())) {
				return null;
			}
			URI uri = HTTPUtils.getURI(request, false);
			String path = URIUtils.normalize(uri.getPath());
			// not in this web artifact
			if (!path.startsWith(serverPath)) {
				return null;
			}
			path = path.substring(serverPath.length());
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			Map<String, String> analyzed = pathAnalysis.analyze(path);
			logger.debug("Analyzed: " + analyzed + " in path: " + webArtifact.getConfiguration().getPath());
			// not in this rest path
			if (analyzed == null) {
				return null;
			}
			Map<String, List<String>> cookies = HTTPUtils.getCookies(request.getContent().getHeaders());
			String originalSessionId = GlueListener.getSessionId(cookies);
			Session session = originalSessionId == null ? null : webApplication.getSessionProvider().getSession(originalSessionId);
			
			// authentication tokens in the request get precedence over session-based authentication
			AuthenticationHeader authenticationHeader = HTTPUtils.getAuthenticationHeader(request);
			Token token = authenticationHeader == null ? null : authenticationHeader.getToken();
			// but likely we'll have to check the session for tokens
			if (token == null && session != null) {
				token = (Token) session.get(GlueListener.buildTokenName(webApplication.getRealm()));
			}
			else if (token != null && session != null) {
				session.set(GlueListener.buildTokenName(webApplication.getRealm()), token);
			}
			
			// check validity of token
			TokenValidator tokenValidator = webApplication.getTokenValidator();
			if (tokenValidator != null) {
				if (token != null && !tokenValidator.isValid(token)) {
					session.destroy();
					originalSessionId = null;
					session = null;
					token = null;
				}
			}
			
			DeviceValidator deviceValidator = webApplication.getDeviceValidator();
			String deviceId = null;
			boolean isNewDevice = false;
			// check validity of device
			Device device = request.getContent() == null ? null : GlueListener.getDevice(webApplication.getRealm(), request.getContent().getHeaders());
			if (device == null && (deviceValidator != null || (webArtifact.getConfig().getDevice() != null && webArtifact.getConfig().getDevice()))) {
				device = GlueListener.newDevice(webApplication.getRealm(), request.getContent().getHeaders());
				deviceId = device.getDeviceId();
				isNewDevice = true;
			}
			
			if (deviceValidator != null && !deviceValidator.isAllowed(token, device)) {
				throw new HTTPException(token == null ? 401 : 403, "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' is using an unauthorized device '" + device.getDeviceId() + "' for service: " + service.getId());
			}
			
			// check role
			RoleHandler roleHandler = webApplication.getRoleHandler();
			if (roleHandler != null && webArtifact.getConfiguration().getRoles() != null) {
				boolean hasRole = false;
				for (String role : webArtifact.getConfiguration().getRoles()) {
					if (roleHandler.hasRole(token, role)) {
						hasRole = true;
						break;
					}
				}
				if (!hasRole) {
					throw new HTTPException(token == null ? 401 : 403, "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have one of the allowed roles '" + webArtifact.getConfiguration().getRoles() + "' for service: " + service.getId());
				}
			}

			boolean sanitizeInput = webArtifact.getConfiguration().getSanitizeInput() != null && webArtifact.getConfiguration().getSanitizeInput();
			
			Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
			
			ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
			if (input.getType().get("configuration") != null) {
				input.set("configuration", configuration);
			}
			if (input.getType().get("query") != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("query").getType())) {
					input.set("query/" + element.getName(), sanitize(queryProperties.get(element.getName()), sanitizeInput));
				}
			}
			if (input.getType().get("header") != null && request.getContent() != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("header").getType())) {
					int counter = 0;
					for (Header header : MimeUtils.getHeaders(RESTUtils.fieldToHeader(element.getName()), request.getContent().getHeaders())) {
						input.set("header/" + element.getName() + "[" + counter++ + "]", sanitize(MimeUtils.getFullHeaderValue(header), sanitizeInput));
					}
				}
			}
			if (session != null && input.getType().get("session") != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("session").getType())) {
					input.set("session/" + element.getName(), sanitize(session.get(element.getName()), sanitizeInput));
				}
			}
			if (input.getType().get("acceptedLanguages") != null && request.getContent() != null) {
				input.set("acceptedLanguages", MimeUtils.getAcceptedLanguages(request.getContent().getHeaders()));
			}

			for (String key : analyzed.keySet()) {
				input.set("path/" + key, sanitize(analyzed.get(key), sanitizeInput));
			}
			if (input.getType().get("cookie") != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("cookie").getType())) {
					input.set("cookie/" + element.getName(), sanitize(cookies.get(element.getName()), sanitizeInput));
				}
			}
			
			if (input.getType().get("device") != null) {
				input.set("device", device);
			}
			
			if (input.getType().get("content") != null && request.getContent() instanceof ContentPart) {
				ReadableContainer<ByteBuffer> readable = ((ContentPart) request.getContent()).getReadable();
				// the readable can be null (e.g. empty part)
				if (readable != null) {
					// we want the stream
					if (input.getType().get("content").getType() instanceof SimpleType) {
						input.set("content", IOUtils.toInputStream(readable));
					}
					else {
						String contentType = MimeUtils.getContentType(request.getContent().getHeaders());
						UnmarshallableBinding binding;
						if (contentType == null) {
							throw new HTTPException(400, "Unknown request content type");
						}
						else if (contentType.equalsIgnoreCase("application/xml") || contentType.equalsIgnoreCase("text/xml")) {
							binding = new XMLBinding((ComplexType) input.getType().get("content").getType(), charset);
						}
						else if (contentType.equalsIgnoreCase("application/json") || contentType.equalsIgnoreCase("application/javascript")) {
							binding = new JSONBinding((ComplexType) input.getType().get("content").getType(), charset);
						}
						else if (contentType.equalsIgnoreCase(WebResponseType.FORM_ENCODED.getMimeType())) {
							binding = new FormBinding((ComplexType) input.getType().get("content").getType(), charset);
						}
						else {
							binding = getBindingProvider().getUnmarshallableBinding((ComplexType) input.getType().get("content").getType(), charset, request.getContent().getHeaders());
							if (binding == null) {
								throw new HTTPException(400, "Unsupported request content type: " + contentType);
							}
						}
						try {
							input.set("content", sanitize(binding.unmarshal(IOUtils.toInputStream(readable), new Window[0]), sanitizeInput));
						}
						catch (IOException e) {
							throw new HTTPException(500, e);
						}
						catch (ParseException e) {
							throw new HTTPException(400, "Message can not be parsed using specification: " + input.getType().get("content").getType(),e);
						}
					}
				}
			}
			
			// check permissions
			PermissionHandler permissionHandler = webApplication.getPermissionHandler();
			if (permissionHandler != null) {
				String context = null;
				String action = null;
				if (webArtifact.getConfig().getPermissionContext() != null) {
					if (webArtifact.getConfig().getPermissionContext().startsWith("=")) {
						// we replace any "input/" references as you likely copy pasted it from the interface, it should work the same as the pipeline
						Object result = getVariable(input, webArtifact.getConfig().getPermissionContext().substring(1).replaceAll("\\binput/", ""));
						context = result == null ? null : result.toString();
					}
					else {
						context = webArtifact.getConfig().getPermissionContext();
					}
				}
				if (webArtifact.getConfig().getPermissionAction() != null) {
					if (webArtifact.getConfig().getPermissionAction().startsWith("=")) {
						// we replace any "input/" references as you likely copy pasted it from the interface, it should work the same as the pipeline
						Object result = getVariable(input, webArtifact.getConfig().getPermissionAction().substring(1).replaceAll("\\binput/", ""));
						action = result == null ? null : result.toString();
					}
					else {
						action = webArtifact.getConfig().getPermissionAction();
					}
				}
				if (action != null && !permissionHandler.hasPermission(token, context, action)) {
					throw new HTTPException(token == null ? 401 : 403, "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have permission to '" + request.getMethod().toLowerCase() + "' on '" + path + "' with service: " + service.getId());
				}
			}
			
			if (webArtifact.getConfiguration().getAsynchronous() != null && webArtifact.getConfiguration().getAsynchronous()) {
				webApplication.getRepository().getServiceRunner().run(service, webApplication.getRepository().newExecutionContext(token), input);
				return HTTPUtils.newEmptyResponse(request);
			}
			else {
				ServiceRuntime runtime = new ServiceRuntime(service, webApplication.getRepository().newExecutionContext(token));
				runtime.getContext().put("session", session);
				// we set the service context to the web application, rest services can be mounted in multiple applications
				ServiceUtils.setServiceContext(runtime, webApplication.getId());
				ComplexContent output = runtime.run(input);
				List<Header> headers = new ArrayList<Header>();
				if (output != null && output.get("header") != null) {
					ComplexContent header = (ComplexContent) output.get("header");
					for (Element<?> element : header.getType()) {
						String value = (String) header.get(element.getName());
						headers.add(new MimeHeader(RESTUtils.fieldToHeader(element.getName()), value));
					}
				}
				if (isNewDevice) {
					ModifiableHeader cookieHeader = HTTPUtils.newSetCookieHeader(
						"Device-" + webApplication.getRealm(), 
						deviceId,
						new Date(new Date().getTime() + 1000l*60*60*24*365*100),
						webApplication.getCookiePath(),
						// domain
						null, 
						// secure TODO?
						false,
						// http only
						true
					);
					headers.add(cookieHeader);
				}
				// if there is no content to respond with, just send back an empty response
				if (output == null || output.get("content") == null) {
					return HTTPUtils.newEmptyResponse(request, headers.toArray(new Header[headers.size()]));
				}
				else if (output.get("content") instanceof InputStream) {
					// no size given, set chunked
					if (MimeUtils.getHeader("Content-Length", headers.toArray(new Header[headers.size()])) == null) {
						headers.add(new MimeHeader("Transfer-Encoding", "chunked"));
					}
					// no type given, set default
					if (MimeUtils.getHeader("Content-Type", headers.toArray(new Header[headers.size()])) == null) {
						headers.add(new MimeHeader("Content-Type", "application/octet-stream"));
					}
					PlainMimeContentPart part = new PlainMimeContentPart(null,
						IOUtils.wrap((InputStream) output.get("content")),
						headers.toArray(new Header[headers.size()])
					);
					if (allowEncoding) {
						HTTPUtils.setContentEncoding(part, request.getContent().getHeaders());
					}
					return new DefaultHTTPResponse(request, 200, HTTPCodes.getMessage(200), part);
				}
				else {
					Object object = output.get("content");
					output = object instanceof ComplexContent ? (ComplexContent) object : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object);
					MarshallableBinding binding = request.getContent() == null ? null : getBindingProvider().getMarshallableBinding(output.getType(), charset, request.getContent().getHeaders());
					String contentType;
					if (binding != null) {
						contentType = getBindingProvider().getContentType(binding);
					}
					else {
						List<String> acceptedContentTypes = request.getContent() != null
							? MimeUtils.getAcceptedContentTypes(request.getContent().getHeaders())
							: new ArrayList<String>();
						acceptedContentTypes.retainAll(ResponseMethods.allowedTypes);
						WebResponseType preferredResponseType = webArtifact.getConfiguration().getPreferredResponseType();
						if (preferredResponseType == null) {
							preferredResponseType = WebResponseType.JSON;
						}
						contentType = acceptedContentTypes.isEmpty() ? preferredResponseType.getMimeType() : acceptedContentTypes.get(0);
						if (contentType.equalsIgnoreCase(WebResponseType.XML.getMimeType())) {
							binding = new XMLBinding(output.getType(), charset);
						}
						else if (contentType.equalsIgnoreCase(WebResponseType.JSON.getMimeType())) {
							binding = new JSONBinding(output.getType(), charset);
						}
						else if (contentType.equalsIgnoreCase(WebResponseType.FORM_ENCODED.getMimeType())) {
							binding = new FormBinding(output.getType(), charset);
						}
						else {
							throw new HTTPException(500, "Unsupported response content type: " + contentType);
						}
					}
					if (contentType == null) {
						contentType = "application/octet-stream";
					}
					ByteArrayOutputStream content = new ByteArrayOutputStream();
					binding.marshal(content, (ComplexContent) output);
					byte[] byteArray = content.toByteArray();
					headers.add(new MimeHeader("Content-Length", "" + byteArray.length));
					headers.add(new MimeHeader("Content-Type", contentType + "; charset=" + charset.name()));
					PlainMimeContentPart part = new PlainMimeContentPart(null,
						IOUtils.wrap(byteArray, true),
						headers.toArray(new Header[headers.size()])
					);
					if (allowEncoding) {
						HTTPUtils.setContentEncoding(part, request.getContent().getHeaders());
					}
					return new DefaultHTTPResponse(request, 200, HTTPCodes.getMessage(200), part);
				}
			}
		}
		catch (FormatException e) {
			throw new HTTPException(500, e);
		}
		catch (IOException e) {
			throw new HTTPException(500, e);
		}
		catch (ServiceException e) {
			if (ServiceRuntime.NO_AUTHORIZATION.equals(e.getCode())) {
				throw new HTTPException(403, e);
			}
			else if (ServiceRuntime.NO_AUTHENTICATION.equals(e.getCode())) {
				throw new HTTPException(401, e);
			}
			else {
				throw new HTTPException(500, e);
			}
		}
		finally {
			ServiceRuntime.setGlobalContext(null);
		}
	}

	private static Object sanitize(Object value, boolean sanitize) {
		return sanitize ? GlueListener.sanitize(value) : value;
	}

	public BindingProvider getBindingProvider() {
		if (bindingProvider == null) {
			bindingProvider = SPIBindingProvider.getInstance();
		}
		return bindingProvider;
	}

	public void setBindingProvider(BindingProvider bindingProvider) {
		this.bindingProvider = bindingProvider;
	}
	
}
