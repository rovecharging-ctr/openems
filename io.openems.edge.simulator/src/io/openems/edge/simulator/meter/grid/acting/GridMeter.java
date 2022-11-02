package io.openems.edge.simulator.meter.grid.acting;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.DoubleWriteChannel;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.meter.api.AsymmetricMeter;
import io.openems.edge.meter.api.MeterType;
import io.openems.edge.meter.api.SymmetricMeter;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(name = "Simulator.GridMeter.Acting", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				"type=GRID" //
		})
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, //
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE, //
		EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE //
})
public class GridMeter extends AbstractOpenemsModbusComponent implements SymmetricMeter, AsymmetricMeter,
		OpenemsComponent, TimedataProvider, EventHandler, ModbusComponent, ModbusSlave {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		SIMULATED_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_WRITE) //
				.unit(Unit.WATT)),
		VOLTS_A_N(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT)),
		VOLTS_B_N(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT)),
		VOLTS_C_N(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT)),
		VOLTS_A_B(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT)),
		VOLTS_B_C(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT)),
		VOLTS_C_A(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT)),
		AMPS_A(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.AMPERE)),
		AMPS_B(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.AMPERE)),
		AMPS_C(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.AMPERE)),
		WATTS_3PH_TOTAL(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.WATT)),
		VARS_3PH_TOTAL(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT_AMPERE_REACTIVE)),
		VAS_3PH_TOTAL(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT_AMPERE)),
		POWER_FACTOR(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.NONE)),
		FREQUENCY_HZ(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.HERTZ)),
		NEUTRAL(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.AMPERE)),
		WATTS_A(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.WATT)),
		WATTS_B(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.WATT)),
		WATTS_C(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.WATT)),
		VARS_A(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT_AMPERE_REACTIVE)),
		VARS_B(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT_AMPERE_REACTIVE)),
		VARS_C(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT_AMPERE_REACTIVE)),
		VA_A(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT_AMPERE)),
		VA_B(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT_AMPERE)),
		VA_C(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.VOLT_AMPERE)),
		PF_A(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.NONE)),
		PF_B(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.NONE)),
		PF_C(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.NONE)),
		SYM_COMP_MAG_0(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.NONE)),
		SYM_COMP_MAG_P(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.NONE)),
		SYM_COMP_MAG_M(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_WRITE).unit(Unit.NONE));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	@Reference
	protected ComponentManager componentManager;

	@Reference
	protected ConfigurationAdmin cm;
	
	@Reference
	protected SymmetricMeter linkrayMeter;
	
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected SimulatorDatasource datasource;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE)
	private volatile List<ManagedSymmetricEss> symmetricEsss = new CopyOnWriteArrayList<>();

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	private final CalculateEnergyFromPower calculateProductionEnergy = new CalculateEnergyFromPower(this,
			SymmetricMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY);
	private final CalculateEnergyFromPower calculateConsumptionEnergy = new CalculateEnergyFromPower(this,
			SymmetricMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY);

	@Activate
	void activate(ComponentContext context, Config config) throws IOException, OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm, "Modbus",
				config.modbus_id());

		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "linkray", config.linkray_id())) {
			
			try {
				this.linkrayMeter = this.componentManager.getComponent(config.linkray_id());
			} catch (OpenemsNamedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return;
		}

	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	public GridMeter() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricMeter.ChannelId.values(), //
				AsymmetricMeter.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), ChannelId.values() //
		);
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.GRID;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
			this.updateChannels();
			break;
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.calculateEnergy();
			break;

		case EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE:
			try {
				this.run();
			} catch (OpenemsError.OpenemsNamedException e) {
				throw new RuntimeException(e);
			}
			break;
		}
	}

	private void updateChannels() {
		/*
		 * get and store Simulated Active Power
		 */
		
		var activePowerValue = this.linkrayMeter.getActivePowerChannel().getNextValue().get();
		var currentPerPhase = activePowerValue / 480.0 / 3 ;
		
		this.channel(ChannelId.VOLTS_A_N).setNextValue(277.0);
		this.channel(ChannelId.VOLTS_B_N).setNextValue(277.0);
		this.channel(ChannelId.VOLTS_C_N).setNextValue(277.0);
		this.channel(ChannelId.VOLTS_A_B).setNextValue(480.0);
		this.channel(ChannelId.VOLTS_B_C).setNextValue(480.0);
		this.channel(ChannelId.VOLTS_C_A).setNextValue(480.0);
		
		this.channel(ChannelId.AMPS_A).setNextValue(currentPerPhase);
		this.channel(ChannelId.AMPS_B).setNextValue(currentPerPhase);
		this.channel(ChannelId.AMPS_C).setNextValue(currentPerPhase);
		this.channel(ChannelId.WATTS_3PH_TOTAL).setNextValue(activePowerValue);

		this.channel(ChannelId.VARS_3PH_TOTAL).setNextValue(0);
		this.channel(ChannelId.VAS_3PH_TOTAL).setNextValue(activePowerValue);
		this.channel(ChannelId.POWER_FACTOR).setNextValue(1.0);
		this.channel(ChannelId.FREQUENCY_HZ).setNextValue(60.0);

		this.channel(ChannelId.NEUTRAL).setNextValue(0.0);
		this.channel(ChannelId.WATTS_A).setNextValue(activePowerValue / 3.0);
		this.channel(ChannelId.WATTS_B).setNextValue(activePowerValue / 3.0);
		this.channel(ChannelId.WATTS_C).setNextValue(activePowerValue / 3.0);

		this.channel(ChannelId.VARS_A).setNextValue(0.0);
		this.channel(ChannelId.VARS_B).setNextValue(0.0);
		this.channel(ChannelId.VARS_C).setNextValue(0.0);
		
		this.channel(ChannelId.VA_A).setNextValue(activePowerValue / 3.0);
		this.channel(ChannelId.VA_B).setNextValue(activePowerValue / 3.0);
		this.channel(ChannelId.VA_C).setNextValue(activePowerValue / 3.0);

		this.channel(ChannelId.PF_A).setNextValue(1.0);
		this.channel(ChannelId.PF_B).setNextValue(1.0);
		this.channel(ChannelId.PF_C).setNextValue(1.0);

		this.channel(ChannelId.SYM_COMP_MAG_0).setNextValue(0.0);
		this.channel(ChannelId.SYM_COMP_MAG_P).setNextValue(0.0);
		this.channel(ChannelId.SYM_COMP_MAG_M).setNextValue(0.0);
		
		/*
		 * Calculate Active Power
		 */
		var activePower = activePowerValue;
		for (ManagedSymmetricEss ess : this.symmetricEsss) {
			activePower = TypeUtils.subtract(activePower, ess.getActivePower().get());
		}

		this._setActivePower(activePower);
		var activePowerByThree = TypeUtils.divide(activePower, 3);
		this._setActivePowerL1(activePowerByThree);
		this._setActivePowerL2(activePowerByThree);
		this._setActivePowerL3(activePowerByThree);

	}

	private void run() throws OpenemsError.OpenemsNamedException {
		/*
		 * get and store Simulated Active Power
		 */

		var activePower = this.linkrayMeter.getActivePowerChannel();
		IntegerWriteChannel activePowerCh = this.channel(ChannelId.SIMULATED_ACTIVE_POWER);
		activePowerCh.setNextWriteValue(activePower.getNextValue().get());

		var volts_a = this.channel(ChannelId.VOLTS_A_N);
		DoubleWriteChannel volts_a_n_ch= this.channel(ChannelId.VOLTS_A_N);
		volts_a_n_ch.setNextWriteValue((Double) volts_a.getNextValue().get());

		var volts_b = this.channel(ChannelId.VOLTS_B_N);
		DoubleWriteChannel volts_b_n_ch= this.channel(ChannelId.VOLTS_B_N);
		volts_b_n_ch.setNextWriteValue((Double) volts_b.getNextValue().get());
		
		var volts_c = this.channel(ChannelId.VOLTS_C_N);
		DoubleWriteChannel volts_c_n_ch= this.channel(ChannelId.VOLTS_C_N);
		volts_c_n_ch.setNextWriteValue((Double) volts_c.getNextValue().get());
		
		var volts_a_b = this.channel(ChannelId.VOLTS_A_B);
		DoubleWriteChannel volts_a_b_ch= this.channel(ChannelId.VOLTS_A_B);
		volts_a_b_ch.setNextWriteValue((Double) volts_a_b.getNextValue().get());
		
		var volts_b_c = this.channel(ChannelId.VOLTS_B_C);
		DoubleWriteChannel volts_b_c_ch= this.channel(ChannelId.VOLTS_B_C);
		volts_b_c_ch.setNextWriteValue((Double) volts_b_c.getNextValue().get());
		
		var volts_c_a = this.channel(ChannelId.VOLTS_C_A);
		DoubleWriteChannel volts_c_a_ch= this.channel(ChannelId.VOLTS_C_A);
		volts_c_a_ch.setNextWriteValue((Double) volts_c_a.getNextValue().get());
		
		var amps_a = this.channel(ChannelId.AMPS_A);
		DoubleWriteChannel amps_a_ch = this.channel(ChannelId.AMPS_A);
		amps_a_ch.setNextWriteValue((Double) amps_a.getNextValue().get());

		var amps_b = this.channel(ChannelId.AMPS_B);
		DoubleWriteChannel amps_b_ch = this.channel(ChannelId.AMPS_B);
		amps_b_ch.setNextWriteValue((Double) amps_b.getNextValue().get());

		var amps_c = this.channel(ChannelId.AMPS_C);
		DoubleWriteChannel amps_c_ch = this.channel(ChannelId.AMPS_C);
		amps_c_ch.setNextWriteValue((Double) amps_c.getNextValue().get());
		
		var watts_tot = this.channel(ChannelId.WATTS_3PH_TOTAL);
		DoubleWriteChannel watts_tot_ch= this.channel(ChannelId.WATTS_3PH_TOTAL);
		watts_tot_ch.setNextWriteValue((Double) watts_tot.getNextValue().get());

		var vars_tot = this.channel(ChannelId.VARS_3PH_TOTAL);
		DoubleWriteChannel vars_tot_ch= this.channel(ChannelId.VARS_3PH_TOTAL);
		vars_tot_ch.setNextWriteValue((Double) vars_tot.getNextValue().get());
		
		var vas_tot = this.channel(ChannelId.VAS_3PH_TOTAL);
		DoubleWriteChannel vas_tot_ch= this.channel(ChannelId.VAS_3PH_TOTAL);
		vas_tot_ch.setNextWriteValue((Double) vas_tot.getNextValue().get());
		
		var pf = this.channel(ChannelId.POWER_FACTOR);
		DoubleWriteChannel pf_ch= this.channel(ChannelId.POWER_FACTOR);
		pf_ch.setNextWriteValue((Double) pf.getNextValue().get());
		
		var freq = this.channel(ChannelId.FREQUENCY_HZ);
		DoubleWriteChannel freq_ch= this.channel(ChannelId.FREQUENCY_HZ);
		freq_ch.setNextWriteValue((Double) freq.getNextValue().get());

		var neutral = this.channel(ChannelId.NEUTRAL);
		DoubleWriteChannel neutral_ch= this.channel(ChannelId.NEUTRAL);
		neutral_ch.setNextWriteValue((Double) neutral.getNextValue().get());

		var watts_a = this.channel(ChannelId.WATTS_A);
		DoubleWriteChannel watts_a_ch = this.channel(ChannelId.WATTS_A);
		watts_a_ch.setNextWriteValue((Double) watts_a.getNextValue().get());

		var watts_b = this.channel(ChannelId.WATTS_B);
		DoubleWriteChannel watts_b_ch = this.channel(ChannelId.WATTS_B);
		watts_b_ch.setNextWriteValue((Double) watts_b.getNextValue().get());

		var watts_c = this.channel(ChannelId.WATTS_C);
		DoubleWriteChannel watts_c_ch = this.channel(ChannelId.WATTS_C);
		watts_c_ch.setNextWriteValue((Double) watts_c.getNextValue().get());

		var vars_a = this.channel(ChannelId.VARS_A);
		DoubleWriteChannel vars_a_ch = this.channel(ChannelId.VARS_A);
		vars_a_ch.setNextWriteValue((Double) vars_a.getNextValue().get());

		var vars_b = this.channel(ChannelId.VARS_B);
		DoubleWriteChannel vars_b_ch = this.channel(ChannelId.VARS_B);
		vars_b_ch.setNextWriteValue((Double) vars_b.getNextValue().get());

		var vars_c = this.channel(ChannelId.VARS_C);
		DoubleWriteChannel vars_c_ch = this.channel(ChannelId.VARS_C);
		vars_c_ch.setNextWriteValue((Double) vars_c.getNextValue().get());
		
		
		var va_a = this.channel(ChannelId.VA_A);
		DoubleWriteChannel va_a_ch = this.channel(ChannelId.VA_A);
		va_a_ch.setNextWriteValue((Double) va_a.getNextValue().get());

		var va_b = this.channel(ChannelId.VA_B);
		DoubleWriteChannel va_b_ch = this.channel(ChannelId.VA_B);
		va_b_ch.setNextWriteValue((Double) va_b.getNextValue().get());

		var va_c = this.channel(ChannelId.VA_C);
		DoubleWriteChannel va_c_ch = this.channel(ChannelId.VA_C);
		va_c_ch.setNextWriteValue((Double) va_c.getNextValue().get());
		
		var pf_a = this.channel(ChannelId.PF_A);
		DoubleWriteChannel pf_a_ch = this.channel(ChannelId.PF_A);
		pf_a_ch.setNextWriteValue((Double) pf_a.getNextValue().get());

		var pf_b = this.channel(ChannelId.PF_B);
		DoubleWriteChannel pf_b_ch = this.channel(ChannelId.PF_B);
		pf_b_ch.setNextWriteValue((Double) pf_b.getNextValue().get());

		var pf_c = this.channel(ChannelId.PF_C);
		DoubleWriteChannel pf_c_ch = this.channel(ChannelId.PF_C);
		pf_c_ch.setNextWriteValue((Double) pf_c.getNextValue().get());

		var scm_c = this.channel(ChannelId.SYM_COMP_MAG_0);
		DoubleWriteChannel scm_c_ch = this.channel(ChannelId.SYM_COMP_MAG_0);
		scm_c_ch.setNextWriteValue((Double) scm_c.getNextValue().get());
		
		var scm_p = this.channel(ChannelId.SYM_COMP_MAG_P);
		DoubleWriteChannel scm_p_ch = this.channel(ChannelId.SYM_COMP_MAG_P);
		scm_p_ch.setNextWriteValue((Double) scm_p.getNextValue().get());
		
		var scm_m = this.channel(ChannelId.SYM_COMP_MAG_M);
		DoubleWriteChannel scm_m_ch = this.channel(ChannelId.SYM_COMP_MAG_M);
		scm_m_ch.setNextWriteValue((Double) scm_m.getNextValue().get());
		
	}

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() throws OpenemsException {
		/*
		 * We are using the FLOAT registers from the modbus table, because they are all
		 * reachable within one ReadMultipleRegistersRequest.
		 */
		var modbusProtocol = new ModbusProtocol(this, //
//				new FC3ReadRegistersTask(1000, Priority.HIGH,
//						m(ChannelId.SIMULATED_ACTIVE_POWER, new FloatDoublewordElement(1000)))

				new FC16WriteRegistersTask(999, //

						m(ChannelId.VOLTS_A_N, new FloatDoublewordElement(999)),
						m(ChannelId.VOLTS_B_N, new FloatDoublewordElement(1001)),
						m(ChannelId.VOLTS_C_N, new FloatDoublewordElement(1003)),
						m(ChannelId.VOLTS_A_B, new FloatDoublewordElement(1005)),
						m(ChannelId.VOLTS_B_C, new FloatDoublewordElement(1007)),
						m(ChannelId.VOLTS_C_A, new FloatDoublewordElement(1009)),
						m(ChannelId.AMPS_A, new FloatDoublewordElement(1011)),
						m(ChannelId.AMPS_B, new FloatDoublewordElement(1013)),
						m(ChannelId.AMPS_C, new FloatDoublewordElement(1015)),
						m(ChannelId.WATTS_3PH_TOTAL, new FloatDoublewordElement(1017)),
						m(ChannelId.VARS_3PH_TOTAL, new FloatDoublewordElement(1019)),
						m(ChannelId.VAS_3PH_TOTAL, new FloatDoublewordElement(1021)),
						m(ChannelId.POWER_FACTOR, new FloatDoublewordElement(1023)),
						m(ChannelId.FREQUENCY_HZ, new FloatDoublewordElement(1025)),
						m(ChannelId.NEUTRAL, new FloatDoublewordElement(1027)),
						m(ChannelId.WATTS_A, new FloatDoublewordElement(1029)),
						m(ChannelId.WATTS_B, new FloatDoublewordElement(1031)),
						m(ChannelId.WATTS_C, new FloatDoublewordElement(1033)),
						m(ChannelId.VARS_A, new FloatDoublewordElement(1035)),
						m(ChannelId.VARS_B, new FloatDoublewordElement(1037)),
						m(ChannelId.VARS_C, new FloatDoublewordElement(1039)),
						m(ChannelId.VA_A, new FloatDoublewordElement(1041)),
						m(ChannelId.VA_B, new FloatDoublewordElement(1043)),
						m(ChannelId.VA_C, new FloatDoublewordElement(1045)),
						m(ChannelId.PF_A, new FloatDoublewordElement(1047)),
						m(ChannelId.PF_B, new FloatDoublewordElement(1049)),
						m(ChannelId.PF_C, new FloatDoublewordElement(1051)),
						m(ChannelId.SYM_COMP_MAG_0, new FloatDoublewordElement(1053)),
						m(ChannelId.SYM_COMP_MAG_P, new FloatDoublewordElement(1055)),
						m(ChannelId.SYM_COMP_MAG_M, new FloatDoublewordElement(1057))
						)
		);

		return modbusProtocol;
	}

	@Override
	public String debugLog() {
		return this.getActivePower().asString();
	}

	/**
	 * Calculate the Energy values from ActivePower.
	 */
	private void calculateEnergy() {
		// Calculate Energy
		var activePower = this.getActivePower().get();
		if (activePower == null) {
			// Not available
			this.calculateProductionEnergy.update(null);
			this.calculateConsumptionEnergy.update(null);
		} else if (activePower > 0) {
			// Buy-From-Grid
			this.calculateProductionEnergy.update(activePower);
			this.calculateConsumptionEnergy.update(0);
		} else {
			// Sell-To-Grid
			this.calculateProductionEnergy.update(0);
			this.calculateConsumptionEnergy.update(activePower * -1);
		}
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(AccessMode.READ_WRITE), //
				SymmetricMeter.getModbusSlaveNatureTable(AccessMode.READ_WRITE), //
				AsymmetricMeter.getModbusSlaveNatureTable(AccessMode.READ_WRITE), //
				ModbusSlaveNatureTable.of(GridMeter.class, AccessMode.READ_WRITE, 100) //
						.build());
	}
}
