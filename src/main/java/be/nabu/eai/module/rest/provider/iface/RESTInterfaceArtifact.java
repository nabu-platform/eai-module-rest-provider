package be.nabu.eai.module.rest.provider.iface;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.module.http.virtual.api.Source;
import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.eai.web.api.RESTInterface;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.AliasProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;
import nabu.utils.types.Coordinate;

public class RESTInterfaceArtifact extends JAXBArtifact<RESTInterfaceConfiguration> implements RESTInterface {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private Structure input, output;
	private Structure query, header, session, cookie, path, responseHeader;
	
	public RESTInterfaceArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "webrestartifact.xml", RESTInterfaceConfiguration.class);
	}
	
	@Override
	public void save(ResourceContainer<?> directory) throws IOException {
		synchronized(this) {
			rebuildInterface();
		}
		super.save(directory);
	}
	
	@Override
	public ComplexType getInputDefinition() {
		if (input == null) {
			synchronized(this) {
				if (input == null) {
					rebuildInterface();
				}
			}
		}
		return input;
	}

	@Override
	public ComplexType getOutputDefinition() {
		if (output == null) {
			synchronized(this) {
				if (output == null) {
					rebuildInterface();
				}
			}
		}
		return output;
	}

	@Override
	public ServiceInterface getParent() {
		return getConfig().getParent();
	}
	
	private void rebuildInterface() {
		// reuse references so everything gets auto-updated
		Structure input = this.input == null ? new Structure() : RESTUtils.clean(this.input);
		Structure output = this.output == null ? new Structure() : RESTUtils.clean(this.output);
		RESTInterface parent = getParent() instanceof RESTInterface ? (RESTInterface) getParent() : null;
		Structure query = getQueryParameters();
		Structure header = getRequestHeaderParameters();
		Structure session = getSession();
		Structure cookie = getCookie();
		Structure path = getPathParameters();
		Structure responseHeader = getResponseHeaderParameters();
		if (parent != null) {
			query.setSuperType(parent.getQueryParameters());
			header.setSuperType(parent.getRequestHeaderParameters());
			path.setSuperType(parent.getPathParameters());
			responseHeader.setSuperType(parent.getResponseHeaderParameters());
		}
		try {
			if (getConfiguration().getConfigurationType() != null) {
				input.add(new ComplexElementImpl("configuration", (ComplexType) getConfiguration().getConfigurationType(), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			if (getConfiguration().getQueryParameters() != null && !getConfiguration().getQueryParameters().trim().isEmpty()) {
				List<String> names = new ArrayList<String>();
				for (String single : getConfiguration().getQueryParameters().split("[\\s,]+")) {
					names.add(NamingConvention.LOWER_CAMEL_CASE.apply(single));
				}
				List<String> available = removeUnused(query, names);
				for (String name : names) {
					List<Value<?>> values = new ArrayList<Value<?>>();
					String actualName = NamingConvention.LOWER_CAMEL_CASE.apply(name);
					if (!actualName.equals(name)) {
						values.add(new ValueImpl<String>(AliasProperty.getInstance(), name));
						name = actualName;
					}
					if (!available.contains(name)) {
						values.add(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0));
						values.add(new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
						query.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), query, values.toArray(new Value[0])));
					}
				}
			}
			else {
				removeAll(query);
			}
			
			boolean hasAny = false;
			boolean required = false;
			for (Element<?> child : TypeUtils.getAllChildren(query)) {
				hasAny = true;
				Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
				if (property == null || property.getValue() > 0) {
					required = true;
					break;
				}
			}
			if (hasAny) {
				input.add(new ComplexElementImpl("query", query, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			
			if (getConfiguration().getHeaderParameters() != null && !getConfiguration().getHeaderParameters().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getHeaderParameters().split("[\\s,]+"));
				for (int i = 0; i < names.size(); i++) {
					names.set(i, RESTUtils.headerToField(names.get(i)));
				}
				List<String> available = removeUnused(header, names);
				for (String name : names) {
					if (!available.contains(name)) {
						header.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), header, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				}
			}
			else {
				removeAll(header);
			}
			
			required = true;
			hasAny = false;
			for (Element<?> child : TypeUtils.getAllChildren(header)) {
				hasAny = true;
				Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
				if (property != null && property.getValue() == 0) {
					required = false;
					break;
				}
			}
			if (hasAny) {
				input.add(new ComplexElementImpl("header", header, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			
			if (getConfiguration().getSessionParameters() != null && !getConfiguration().getSessionParameters().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getSessionParameters().split("[\\s,]+"));
				List<String> available = removeUnused(session, names);
				for (String name : names) {
					if (!available.contains(name)) {
						//session.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), session, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
						session.add(new ComplexElementImpl(name, (ComplexType) BeanResolver.getInstance().resolve(Object.class), session, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				}
			}
			else {
				removeAll(session);
			}
			
			hasAny = false;
			required = true;
			for (Element<?> child : TypeUtils.getAllChildren(session)) {
				Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
				if (property != null && property.getValue() == 0) {
					required = false;
					break;
				}
			}
			if (hasAny) {
				input.add(new ComplexElementImpl("session", session, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			
			if (getConfiguration().getCookieParameters() != null && !getConfiguration().getCookieParameters().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getCookieParameters().split("[\\s,]+"));
				List<String> available = removeUnused(cookie, names);
				for (String name : names) {
					if (!available.contains(name)) {
						cookie.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), cookie, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				}
			}
			else {
				removeAll(cookie);
			}
			
			required = true;
			hasAny = false;
			for (Element<?> child : TypeUtils.getAllChildren(cookie)) {
				hasAny = true;
				Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
				if (property != null && property.getValue() == 0) {
					required = false;
					break;
				}
			}
			if (hasAny) {
				input.add(new ComplexElementImpl("cookie", cookie, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			
			if (getConfiguration().getPath() != null) {
				List<String> names = GlueListener.analyzePath(getConfiguration().getPath()).getParameters();
				List<String> available = removeUnused(path, names);
				for (String name : names) {
					if (!available.contains(name)) {
						path.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), path));
					}
				}
			}
			else {
				removeAll(path);
			}
			
			if (!TypeUtils.getAllChildren(path).isEmpty()) {
				input.add(new ComplexElementImpl("path", path, input));
			}
			
			// in some cases, even though the parent has a defined input, you may want to use streams anyway (to prevent parsing)
			if ((parent != null && parent.isInputAsStream()) || (getConfiguration().getInputAsStream() != null && getConfiguration().getInputAsStream())) {
				input.add(new SimpleElementImpl<InputStream>("content", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(InputStream.class), input));
				Structure meta = new Structure();
				meta.setName("meta");
				meta.add(new SimpleElementImpl<String>("contentType", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				meta.add(new SimpleElementImpl<String>("fileName", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				input.add(new ComplexElementImpl("meta", meta, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			// if you define a local input, do it at your own risk if the parent also has one, it should probably extend the original...
			else if (getConfiguration().getInput() != null) {
				input.add(new ComplexElementImpl("content", (ComplexType) getConfiguration().getInput(), input));
			}
			else if (parent != null && parent.getRequestBody() != null) {
				input.add(new ComplexElementImpl("content", parent.getRequestBody(), input));
			}
			if (getConfiguration().getAcceptedLanguages() != null && getConfiguration().getAcceptedLanguages()) {
				input.add(new SimpleElementImpl<String>("acceptedLanguages", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			
			if (getConfig().isLanguage()) {
				input.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			
			if (getConfig().getDevice() != null && getConfig().getDevice()) {
				input.add(new ComplexElementImpl("device", (ComplexType) BeanResolver.getInstance().resolve(Device.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			if (getConfig().getToken() != null && getConfig().getToken()) {
				input.add(new ComplexElementImpl("token", (ComplexType) BeanResolver.getInstance().resolve(Token.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			
			if (getConfiguration().getResponseHeaders() != null && !getConfiguration().getResponseHeaders().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getResponseHeaders().split("[\\s,]+"));
				for (int i = 0; i < names.size(); i++) {
					names.set(i, RESTUtils.headerToField(names.get(i)));
				}
				List<String> available = removeUnused(responseHeader, names);
				for (String name : names) {
					if (!available.contains(name)) {
						responseHeader.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), responseHeader));
					}
				}
			}
			else {
				removeAll(responseHeader);
			}
			
			required = true;
			hasAny = false;
			for (Element<?> child : TypeUtils.getAllChildren(responseHeader)) {
				hasAny = true;
				Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
				if (property != null && property.getValue() == 0) {
					required = false;
					break;
				}
			}
			if (hasAny) {
				output.add(new ComplexElementImpl("header", responseHeader, output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			
			if (getConfig().isCache()) {
				Structure cache = new Structure();
				cache.setName("cache");
				cache.add(new SimpleElementImpl<Date>("lastModified", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Date.class), cache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				cache.add(new SimpleElementImpl<String>("etag", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), cache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				cache.add(new SimpleElementImpl<Boolean>("mustRevalidate", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class), cache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				output.add(new ComplexElementImpl("cache", cache, output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			if ((parent != null && parent.isOutputAsStream()) || (getConfiguration().getOutputAsStream() != null && getConfiguration().getOutputAsStream())) {
				output.add(new SimpleElementImpl<InputStream>("content", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(InputStream.class), output));
				Structure meta = new Structure();
				meta.setName("meta");
				meta.add(new SimpleElementImpl<String>("contentType", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				meta.add(new SimpleElementImpl<Long>("contentLength", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				meta.add(new SimpleElementImpl<String>("fileName", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				output.add(new ComplexElementImpl("meta", meta, output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			else if (getConfiguration().getOutput() != null) {
				output.add(new ComplexElementImpl("content", (ComplexType) getConfiguration().getOutput(), output));
			}
			else if (parent != null && parent.getResponseBody() != null) {
				output.add(new ComplexElementImpl("content", (ComplexType) parent.getResponseBody(), output));
			}
			if (getConfig().isWebApplicationId()) {
				input.add(new SimpleElementImpl<String>("webApplicationId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input));
			}
			if (getConfig().isGeoPosition()) {
				input.add(new ComplexElementImpl("geoPosition", (ComplexType) BeanResolver.getInstance().resolve(Coordinate.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			if (getConfig().isRequest()) {
				input.add(new ComplexElementImpl("request", (ComplexType) BeanResolver.getInstance().resolve(HTTPRequest.class), input));
			}
			if (getConfig().isSource()) {
				input.add(new ComplexElementImpl("source", (ComplexType) BeanResolver.getInstance().resolve(Source.class), input));
			}
			if (getConfig().isDomain()) {
				input.add(new SimpleElementImpl<String>("domain", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input));
			}
			if (getConfig().isOrigin()) {
				input.add(new SimpleElementImpl<String>("origin", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input));
			}
			this.input = input;
			this.output = output;
		}
		catch (IOException e) {
			logger.error("Can not rebuild interface for: " + getId(), e);
		}
	}

	private static List<String> removeAll(Structure structure) {
		return removeUnused(structure, new ArrayList<String>());
	}
	
	private static List<String> removeUnused(Structure structure, List<String> names) {
		List<String> available = new ArrayList<String>();
		List<Element<?>> allChildren = new ArrayList<Element<?>>(TypeUtils.getAllChildren(structure));
		for (Element<?> child : allChildren) {
			if (!names.contains(child.getName())) {
				structure.remove(child);
			}
			else {
				available.add(child.getName());
			}
		}
		return available;
	}

	@Override
	public Structure getQueryParameters() {
		if (query == null) {
			query = new Structure();
			query.setName("query");
		}
		return query;
	}
	public void setQueryParameters(Structure query) {
		this.query = query;
	}

	@Override
	public Structure getRequestHeaderParameters() {
		if (header == null) {
			header = new Structure();
			header.setName("header");
		}
		return header;
	}
	public void setRequestHeaderParameters(Structure header) {
		this.header = header;
	}

	public Structure getSession() {
		if (session == null) {
			session = new Structure();
			session.setName("session");
		}
		return session;
	}

	public void setSession(Structure session) {
		this.session = session;
	}

	public Structure getCookie() {
		if (cookie == null) {
			cookie = new Structure();
			cookie.setName("cookie");
		}
		return cookie;
	}

	public void setCookie(Structure cookie) {
		this.cookie = cookie;
	}

	@Override
	public Structure getPathParameters() {
		if (path == null) {
			path = new Structure();
			path.setName("path");
		}
		return path;
	}

	public void setPathParameters(Structure path) {
		this.path = path;
	}

	@Override
	public Structure getResponseHeaderParameters() {
		if (responseHeader == null) {
			responseHeader = new Structure();
			responseHeader.setName("responseHeader");
		}
		return responseHeader;
	}
	public void setResponseHeaderParameters(Structure responseHeader) {
		this.responseHeader = responseHeader;
	}

	@Override
	public String getMethod() {
		RESTInterface parent = getParent() instanceof RESTInterface ? (RESTInterface) getParent() : null;
		String method = getConfig().getMethod() == null ? null : getConfig().getMethod().name();
		if (method == null && parent != null) {
			method = parent.getMethod();
		}
		return method;
	}

	@Override
	public boolean isInputAsStream() {
		return getConfig().getInputAsStream() != null && getConfig().getInputAsStream();
	}

	@Override
	public boolean isOutputAsStream() {
		return getConfig().getOutputAsStream() != null && getConfig().getOutputAsStream();
	}

	@Override
	public String getContentType() {
		return getConfig().getPreferredResponseType() == null ? "application/json" : getConfig().getPreferredResponseType().getMimeType();
	}

	@Override
	public String getPath() {
		RESTInterface parent = getParent() instanceof RESTInterface ? (RESTInterface) getParent() : null;
		String path = getConfig().getPath();
		// if we have a parent path, append it
		if (parent != null && parent.getPath() != null) {
			if (path == null || path.trim().isEmpty()) {
				path = parent.getPath();
			}
			else {
				path = parent.getPath().replace("[/]+$", "") + "/" + path.replace("^[/]+", "");
			}
		}
		return path;
	}

	@Override
	public ComplexType getRequestBody() {
		return (ComplexType) getConfig().getInput();
	}

	@Override
	public ComplexType getResponseBody() {
		return (ComplexType) getConfig().getOutput();
	}

}
