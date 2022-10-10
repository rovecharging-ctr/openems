package io.openems.edge.meter.linkray;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.JsonUtils;
import io.openems.common.worker.AbstractCycleWorker;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.meter.linkray.MeterLinkray.ChannelId;

public class LinkrayWorker extends AbstractCycleWorker {

	private static final int LAST_READING_TOO_OLD_SECONDS = 30;

	private final Logger log = LoggerFactory.getLogger(LinkrayWorker.class);
	private final MeterLinkray parent;
	private final LinkrayApiClient apiClient;
	private final Config config;

	/**
	 * Holds the internal Linkray meterId.
	 */
	private String meterId = null;

	public LinkrayWorker(MeterLinkray parent, LinkrayApiClient apiClient, Config config) {
		this.parent = parent;
		this.apiClient = apiClient;
		this.config = config;
	}

	@Override
	protected void forever() throws OpenemsNamedException, InterruptedException {
		
		var reading = this.apiClient.getData();
		Integer current = null;


		try {
			var values = JsonUtils.getAsJsonObject(reading, "data");
			current = JsonUtils.getAsOptionalInt(values, "chargecurrent").orElse(null);
			
			this.parent.logInfo(log, "Current: " + current.toString());


		} catch (OpenemsException e) {
			this.parent.logError(this.log, "REST-Api failed: " + e.getMessage());

		}
	}

	/**
	 * Validates that we have valid MeterId.
	 *
	 * @throws OpenemsNamedException on invalid MeterId.
	 */

}
