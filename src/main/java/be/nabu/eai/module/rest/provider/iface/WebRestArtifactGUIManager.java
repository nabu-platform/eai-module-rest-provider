package be.nabu.eai.module.rest.provider.iface;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class WebRestArtifactGUIManager extends BaseJAXBGUIManager<WebRestArtifactConfiguration, WebRestArtifact> {

	public WebRestArtifactGUIManager() {
		super("REST Interface", WebRestArtifact.class, new WebRestArtifactManager(), WebRestArtifactConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected WebRestArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		return new WebRestArtifact(entry.getName(), entry, entry.getRepository());
	}

}
