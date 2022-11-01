package io.openems.edge.meter.linkray;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.meter.api.MeterType;

@ObjectClassDefinition(//
		name = "Meter Linkray", //
		description = "Implements the Linkray smart meter.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "linkray0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Meter-Type", description = "What is measured by this Meter?")
	MeterType type() default MeterType.GRID;

	@AttributeDefinition(name = "url", description = "e.g. https://192.168.68.122")
	String url() default "http://127.0.0.1:18080";
	@AttributeDefinition(name = "API Key", description = "API key required to read the Linkray")
	String apiKey() default "";

	String webconsole_configurationFactory_nameHint() default "Linkray [{id}]";
}