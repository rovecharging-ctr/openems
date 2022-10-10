package io.openems.edge.meter.linkray.jsonrpc;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;

/**
 * Represents a JSON-RPC Request for 'getMeters'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "getMeters",
 *   "params": {}
 * }
 * </pre>
 */
public class GetDataRequest extends JsonrpcRequest {

	public static final String METHOD = "getData";

	/**
	 * Create {@link GetMetersRequest} from a template {@link JsonrpcRequest}.
	 *
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link GetMetersRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static GetDataRequest from(JsonrpcRequest r) throws OpenemsException {
		return new GetDataRequest(r);
	}

	public GetDataRequest() {
		super(METHOD);
	}

	private GetDataRequest(JsonrpcRequest request) {
		super(request, METHOD);
	}

	@Override
	public JsonObject getParams() {
		return new JsonObject();
	}

}
