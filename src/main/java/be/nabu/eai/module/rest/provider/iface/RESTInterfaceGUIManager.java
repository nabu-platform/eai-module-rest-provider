package be.nabu.eai.module.rest.provider.iface;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class RESTInterfaceGUIManager extends BaseJAXBGUIManager<RESTInterfaceConfiguration, RESTInterfaceArtifact> {

	public RESTInterfaceGUIManager() {
		super("REST Interface", RESTInterfaceArtifact.class, new RESTInterfaceManager(), RESTInterfaceConfiguration.class);
	}

	@Override
	public String getCategory() {
		return "Protocols";
	}
	
	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected RESTInterfaceArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		return new RESTInterfaceArtifact(entry.getName(), entry, entry.getRepository());
	}

}
