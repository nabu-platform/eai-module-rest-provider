package be.nabu.eai.module.rest.provider.iface;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;

public class RESTInterfaceArtifact extends JAXBArtifact<RESTInterfaceConfiguration> implements DefinedServiceInterface {

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
		return null;
	}
	
	private void rebuildInterface() {
		// reuse references so everything gets auto-updated
		Structure input = this.input == null ? new Structure() : RESTUtils.clean(this.input);
		Structure output = this.output == null ? new Structure() : RESTUtils.clean(this.output);
		Structure query = getQuery();
		Structure header = getHeader();
		Structure session = getSession();
		Structure cookie = getCookie();
		Structure path = getPath();
		Structure responseHeader = getResponseHeader();
		try {
			if (getConfiguration().getConfigurationType() != null) {
				input.add(new ComplexElementImpl("configuration", (ComplexType) getConfiguration().getConfigurationType(), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			if (getConfiguration().getQueryParameters() != null && !getConfiguration().getQueryParameters().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getQueryParameters().split("[\\s,]+"));
				List<String> available = removeUnused(query, names);
				for (String name : names) {
					if (!available.contains(name)) {
						query.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), query, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				}
				boolean required = false;
				for (Element<?> child : query) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					if (property == null || property.getValue() > 0) {
						required = true;
						break;
					}
				}
				input.add(new ComplexElementImpl("query", query, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			else {
				removeAll(query);
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
				boolean required = true;
				for (Element<?> child : header) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					if (property != null && property.getValue() == 0) {
						required = false;
						break;
					}
				}
				input.add(new ComplexElementImpl("header", header, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			else {
				removeAll(header);
			}
			if (getConfiguration().getSessionParameters() != null && !getConfiguration().getSessionParameters().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getSessionParameters().split("[\\s,]+"));
				List<String> available = removeUnused(session, names);
				for (String name : names) {
					if (!available.contains(name)) {
						session.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), session, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				}
				boolean required = true;
				for (Element<?> child : session) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					if (property != null && property.getValue() == 0) {
						required = false;
						break;
					}
				}
				input.add(new ComplexElementImpl("session", session, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			else {
				removeAll(session);
			}
			if (getConfiguration().getCookieParameters() != null && !getConfiguration().getCookieParameters().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getCookieParameters().split("[\\s,]+"));
				List<String> available = removeUnused(cookie, names);
				for (String name : names) {
					if (!available.contains(name)) {
						cookie.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), cookie, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				}
				boolean required = true;
				for (Element<?> child : cookie) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					if (property != null && property.getValue() == 0) {
						required = false;
						break;
					}
				}
				input.add(new ComplexElementImpl("cookie", cookie, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			else {
				removeAll(cookie);
			}
			if (getConfiguration().getPath() != null) {
				List<String> names = GlueListener.analyzePath(getConfiguration().getPath()).getParameters();
				List<String> available = removeUnused(path, names);
				for (String name : names) {
					if (!available.contains(name)) {
						path.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), path));
					}
				}
				if (path.iterator().hasNext()) {
					input.add(new ComplexElementImpl("path", path, input));
				}
			}
			else {
				removeAll(path);
			}
			if (getConfiguration().getInputAsStream() != null && getConfiguration().getInputAsStream()) {
				input.add(new SimpleElementImpl<InputStream>("content", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(InputStream.class), input));
			}
			else if (getConfiguration().getInput() != null) {
				input.add(new ComplexElementImpl("content", (ComplexType) getConfiguration().getInput(), input));
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
				output.add(new ComplexElementImpl("header", responseHeader, output));
			}
			else {
				removeAll(responseHeader);
			}
			if (getConfig().isCache()) {
				Structure cache = new Structure();
				cache.setName("cache");
				cache.add(new SimpleElementImpl<Date>("lastModified", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Date.class), cache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				cache.add(new SimpleElementImpl<String>("etag", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), cache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				cache.add(new SimpleElementImpl<Boolean>("mustRevalidate", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class), cache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				output.add(new ComplexElementImpl("cache", cache, output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			if (getConfiguration().getOutputAsStream() != null && getConfiguration().getOutputAsStream()) {
				output.add(new SimpleElementImpl<InputStream>("content", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(InputStream.class), output));
			}
			else if (getConfiguration().getOutput() != null) {
				output.add(new ComplexElementImpl("content", (ComplexType) getConfiguration().getOutput(), output));
			}
			if (getConfig().isWebApplicationId()) {
				input.add(new SimpleElementImpl<String>("webApplicationId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input));
			}
			this.input = input;
			this.output = output;
		}
		catch (IOException e) {
			logger.error("Can not rebuild interface", e);
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

	public Structure getQuery() {
		if (query == null) {
			query = new Structure();
			query.setName("query");
		}
		return query;
	}

	public void setQuery(Structure query) {
		this.query = query;
	}

	public Structure getHeader() {
		if (header == null) {
			header = new Structure();
			header.setName("header");
		}
		return header;
	}

	public void setHeader(Structure header) {
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

	public Structure getPath() {
		if (path == null) {
			path = new Structure();
			path.setName("path");
		}
		return path;
	}

	public void setPath(Structure path) {
		this.path = path;
	}

	public Structure getResponseHeader() {
		if (responseHeader == null) {
			responseHeader = new Structure();
			responseHeader.setName("responseHeader");
		}
		return responseHeader;
	}

	public void setResponseHeader(Structure responseHeader) {
		this.responseHeader = responseHeader;
	}

}
