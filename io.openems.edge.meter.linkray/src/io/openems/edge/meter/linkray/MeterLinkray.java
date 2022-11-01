package io.openems.edge.meter.linkray;

import java.util.concurrent.CompletableFuture;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;

import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.session.Role;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.jsonapi.JsonApi;
import io.openems.edge.common.user.User;
import io.openems.edge.meter.api.AsymmetricMeter;
import io.openems.edge.meter.api.MeterType;
import io.openems.edge.meter.api.SymmetricMeter;
import io.openems.edge.meter.linkray.jsonrpc.GetDataRequest;
import io.openems.edge.meter.linkray.jsonrpc.GetDataResponse;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Linkray", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
})
public class MeterLinkray extends AbstractOpenemsComponent
		implements SymmetricMeter, AsymmetricMeter, OpenemsComponent, EventHandler, JsonApi {

	private MeterType meterType = MeterType.PRODUCTION;

	private LinkrayApiClient apiClient = null;
	private LinkrayWorker worker = null;

	public MeterLinkray() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricMeter.ChannelId.values(), //
				AsymmetricMeter.ChannelId.values(), //
				ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		this.meterType = config.type();

		super.activate(context, config.id(), config.alias(), config.enabled());

		if (config.enabled()) {
			this.apiClient = new LinkrayApiClient(config.url(), config.apiKey());

			this.worker = new LinkrayWorker(this, this.apiClient, config);
			this.worker.activate(config.id());
			this.worker.triggerNextRun();
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();

		if (this.worker != null) {
			this.worker.deactivate();
		}
	}

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/*
		 * Raw values from Linkray API
		 */

		LRVOLTAGE(Doc.of(OpenemsType.DOUBLE)),
		TOTAMPS(Doc.of(OpenemsType.DOUBLE)),
		CHARGECURRENT(Doc.of(OpenemsType.DOUBLE)),
		AVAILABLEPERCHARGER(Doc.of(OpenemsType.DOUBLE));


		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
			this.worker.triggerNextRun();
			break;
		}
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

	@Override
	protected void logInfo(Logger log, String message) {
		super.logInfo(log, message);
	}

	@Override
	protected void logError(Logger log, String message) {
		super.logError(log, message);
	}

	@Override
	public CompletableFuture<? extends JsonrpcResponseSuccess> handleJsonrpcRequest(User user, JsonrpcRequest request)
			throws OpenemsNamedException {
		user.assertRoleIsAtLeast("handleJsonrpcRequest", Role.GUEST);

		try {
			switch (request.getMethod()) {

			case GetDataRequest.METHOD:
				return this.handleGetDataRequest(user, GetDataRequest.from(request));

			default:
				throw OpenemsError.JSONRPC_UNHANDLED_METHOD.exception(request.getMethod());
			}
		} catch (OpenemsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OpenemsNamedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Handles a GetDataRequest.
	 *
	 * <p>
	 * See {@link LinkrayApiClient#getMeters()}
	 *
	 * @param user    the User
	 * @param request the GetMetersRequest
	 * @return the Future JSON-RPC Response
	 * @throws OpenemsNamedException on error
	 * @throws InterruptedException on error
	 */
	private CompletableFuture<JsonrpcResponseSuccess> handleGetDataRequest(User user, GetDataRequest request)
			throws OpenemsNamedException, InterruptedException {
		var meters = this.apiClient.getData();
		var response = new GetDataResponse(request.getId(), meters);
		return CompletableFuture.completedFuture(response);
	}
}
