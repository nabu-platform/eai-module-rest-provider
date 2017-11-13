package be.nabu.eai.module.rest.provider.iface;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.Validation;

public class RESTInterfaceManager extends JAXBArtifactManager<RESTInterfaceConfiguration, RESTInterfaceArtifact> {

	public RESTInterfaceManager() {
		super(RESTInterfaceArtifact.class);
	}

	@Override
	protected RESTInterfaceArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new RESTInterfaceArtifact(id, container, repository);
	}

	@Override
	public RESTInterfaceArtifact load(ResourceEntry entry, List<Validation<?>> messages) throws IOException, ParseException {
		RESTInterfaceArtifact artifact = super.load(entry, messages);
		artifact.setQuery(loadIfExists(entry, "input-query.xml"));
		artifact.setCookie(loadIfExists(entry, "input-cookie.xml"));
		artifact.setSession(loadIfExists(entry, "input-session.xml"));
		artifact.setHeader(loadIfExists(entry, "input-header.xml"));
		artifact.setPath(loadIfExists(entry, "input-path.xml"));
		artifact.setResponseHeader(loadIfExists(entry, "input-response-header.xml"));
		return artifact;
	}
	
	private static Structure loadIfExists(ResourceEntry entry, String name) {
		Resource child = entry.getContainer().getChild(name);
		if (child instanceof ReadableResource) {
			try {
				return StructureManager.parse(entry, name);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	@Override
	public List<Validation<?>> save(ResourceEntry entry, RESTInterfaceArtifact artifact) throws IOException {
		List<Validation<?>> messages = super.save(entry, artifact);
		StructureManager.format(entry, artifact.getQuery(), "input-query.xml");
		StructureManager.format(entry, artifact.getCookie(), "input-cookie.xml");
		StructureManager.format(entry, artifact.getSession(), "input-session.xml");
		StructureManager.format(entry, artifact.getHeader(), "input-header.xml");
		StructureManager.format(entry, artifact.getPath(), "input-path.xml");
		StructureManager.format(entry, artifact.getResponseHeader(), "input-response-header.xml");
		return messages;
	}

}
