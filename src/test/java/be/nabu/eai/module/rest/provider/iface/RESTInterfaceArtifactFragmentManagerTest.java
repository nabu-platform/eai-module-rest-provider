package be.nabu.eai.module.rest.provider.iface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.resources.memory.MemoryDirectory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;

public class RESTInterfaceArtifactFragmentManagerTest {

	private static final String FRAGMENT = "<restInterface>\n"
		+ "\t<query>\n"
		+ "\t\t<field minOccurs=\"0\" name=\"limit\" type=\"java.lang.Long\"/>\n"
		+ "\t\t<field minOccurs=\"0\" name=\"offset\" type=\"java.lang.Long\"/>\n"
		+ "\t\t<field maxOccurs=\"unbounded\" minOccurs=\"0\" name=\"orderBy\" type=\"java.lang.String\"/>\n"
		+ "\t\t<field maxOccurs=\"unbounded\" minOccurs=\"0\" name=\"organisationId\" type=\"java.util.UUID\"/>\n"
		+ "\t\t<field minOccurs=\"0\" name=\"hasTto\" type=\"java.lang.Boolean\"/>\n"
		+ "\t\t<field minOccurs=\"0\" name=\"hasAr\" type=\"java.lang.Boolean\"/>\n"
		+ "\t\t<field minOccurs=\"0\" name=\"hasPoa\" type=\"java.lang.Boolean\"/>\n"
		+ "\t\t<field minOccurs=\"0\" name=\"hasBebatAction\" type=\"java.lang.Boolean\"/>\n"
		+ "\t</query>\n"
		+ "</restInterface>";

	@Test
	public void embeddedQueryParametersArePersistedAndAddedToServiceInput() throws Exception {
		MemoryDirectory repositoryRoot = new MemoryDirectory("repository");
		MemoryDirectory mavenRoot = new MemoryDirectory("maven");
		EAIResourceRepository repository = new EAIResourceRepository(repositoryRoot, mavenRoot);
		RepositoryEntry root = new RepositoryEntry(repository, repositoryRoot, null, "/");
		RepositoryEntry entry = root.createNode("test", new RESTInterfaceManager(), false);
		RESTInterfaceArtifact artifact = new RESTInterfaceArtifact("test", entry.getContainer(), repository);
		RESTInterfaceArtifactFragmentManager manager = new RESTInterfaceArtifactFragmentManager();

		List<Validation<?>> validations = manager.applyFragment(entry, artifact, "rest-interface.xml", null, FRAGMENT);

		assertNoErrors(validations);
		assertEquals("limit,offset,orderBy,organisationId,hasTto,hasAr,hasPoa,hasBebatAction", artifact.getConfig().getQueryParameters());
		RESTInterfaceArtifact reloaded = new RESTInterfaceManager().load(entry, new ArrayList<Validation<?>>());
		assertEquals("limit,offset,orderBy,organisationId,hasTto,hasAr,hasPoa,hasBebatAction", reloaded.getConfig().getQueryParameters());
		assertField(reloaded.getQueryParameters(), "limit", Long.class, 1);
		assertField(reloaded.getQueryParameters(), "offset", Long.class, 1);
		assertField(reloaded.getQueryParameters(), "orderBy", String.class, 0);
		assertField(reloaded.getQueryParameters(), "organisationId", java.util.UUID.class, 0);
		assertField(reloaded.getQueryParameters(), "hasTto", Boolean.class, 1);
		assertField(reloaded.getQueryParameters(), "hasAr", Boolean.class, 1);
		assertField(reloaded.getQueryParameters(), "hasPoa", Boolean.class, 1);
		assertField(reloaded.getQueryParameters(), "hasBebatAction", Boolean.class, 1);

		ComplexType input = reloaded.getInputDefinition();
		assertNotNull(TypeUtils.getChild(input, "query"));
		assertNotNull(TypeUtils.getChild(input, "query/limit"));
		assertNotNull(TypeUtils.getChild(input, "query/organisationId"));
	}

	private void assertField(ComplexType type, String name, Class<?> instanceClass, int maxOccurs) {
		Element<?> field = TypeUtils.getChild(type, name);
		assertNotNull(name, field);
		assertTrue(name, field.getType() instanceof SimpleType);
		assertEquals(name, instanceClass, ((SimpleType<?>) field.getType()).getInstanceClass());
		assertEquals(name, Integer.valueOf(maxOccurs), ValueUtils.getValue(MaxOccursProperty.getInstance(), field.getProperties()));
	}

	private void assertNoErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			assertFalse(validation.getMessage(), validation.getSeverity() == ValidationMessage.Severity.ERROR);
		}
	}
}
