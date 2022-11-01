package io.openems.edge.meter.linkray.jsonrpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;

public class LinkrayMeter {

	/**
	 * Factory.
	 * 
	 * @param j the {@link JsonElement}
	 * @return the {@link LinkrayMeter}
	 * @throws OpenemsNamedException on error
	 */
	public static LinkrayMeter fromJson(JsonElement j) throws OpenemsNamedException {
		var meterId = JsonUtils.getAsString(j, "meterId");

		return new LinkrayMeter(meterId);
	}


	private final String meterId;

	private LinkrayMeter(String meterId) {
		this.meterId = meterId;
	}

	@Override
	public String toString() {
		return "[meterId=" + this.meterId + "]";
	}

	/**
	 * Converts to {@link JsonObject}.
	 * 
	 * @return the {@link JsonObject}
	 */
	public JsonObject toJson() {
		return JsonUtils.buildJsonObject() //
				.addProperty("meterId", this.meterId) //
				.build();
	}

	public String getMeterId() {
		return this.meterId;
	}

}