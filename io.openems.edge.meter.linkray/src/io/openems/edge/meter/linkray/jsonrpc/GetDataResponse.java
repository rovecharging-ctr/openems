package io.openems.edge.meter.linkray.jsonrpc;

import java.util.UUID;

import com.google.gson.JsonObject;

import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Response for 'getMeters'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "result": {
 *     "meters": [
 *     	 {@link LinkrayMeter#toJson()}
 *     ]
 *   }
 * }
 * </pre>
 */
public class GetDataResponse extends JsonrpcResponseSuccess {

	private final JsonObject meters;

	public GetDataResponse(JsonObject meters) {
		this(UUID.randomUUID(), meters);
	}

	public GetDataResponse(UUID id, JsonObject meters2) {
		super(id);
		this.meters = meters2;
	}

	@Override
	public JsonObject getResult() {
		return JsonUtils.buildJsonObject() //
				.add("meters", this.meters) //
				.build();
	}

}
