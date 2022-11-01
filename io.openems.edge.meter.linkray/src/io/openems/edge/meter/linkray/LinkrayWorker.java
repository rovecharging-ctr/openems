package io.openems.edge.meter.linkray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.JsonUtils;
import io.openems.common.worker.AbstractCycleWorker;
import io.openems.edge.common.channel.DoubleReadChannel;
import io.openems.edge.meter.linkray.MeterLinkray.ChannelId;

public class LinkrayWorker extends AbstractCycleWorker {

    private final Logger log = LoggerFactory.getLogger(LinkrayWorker.class);
    private final MeterLinkray parent;
    private final LinkrayApiClient apiClient;
    //private final Config config;


    public LinkrayWorker(MeterLinkray parent, LinkrayApiClient apiClient, Config config) {
        this.parent = parent;
        this.apiClient = apiClient;
        //this.config = config;
    }

    @Override
    protected void forever() throws OpenemsNamedException, InterruptedException {


        var reading = this.apiClient.getData();
        Double voltage;
        Double totamps;
        Double chargecurrent;
        Double availablepercharger;


        try {
            var values = JsonUtils.getAsJsonObject(reading, "data");
            voltage = JsonUtils.getAsDouble(values, "voltage");
            totamps = JsonUtils.getAsDouble(values, "totamps");
            chargecurrent = JsonUtils.getAsDouble(values, "chargecurrent");
            availablepercharger = JsonUtils.getAsDouble(values, "availablepercharger");

			((DoubleReadChannel) this.parent.channel(ChannelId.LRVOLTAGE)).setNextValue(voltage);
			((DoubleReadChannel) this.parent.channel(ChannelId.TOTAMPS)).setNextValue(totamps);
			((DoubleReadChannel) this.parent.channel(ChannelId.CHARGECURRENT)).setNextValue(chargecurrent);
			((DoubleReadChannel) this.parent.channel(ChannelId.AVAILABLEPERCHARGER)).setNextValue(availablepercharger);

			this.parent._setActivePower((int) Math.round(voltage * chargecurrent));

        } catch (OpenemsException e) {
            this.parent.logError(this.log, "REST-Api failed: " + e.getMessage());

        }
    }
}
