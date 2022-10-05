package io.openems.edge.meter.simulated;

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
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.MeterType;
import io.openems.edge.meter.api.SymmetricMeter;

@Designate(ocd = Config.class, factory = true) 
@Component(
		name = "Meter.Simulated", 
		immediate = true, 
		configurationPolicy = ConfigurationPolicy.REQUIRE 
)
public class MeterSimulatedImpl extends AbstractOpenemsModbusComponent 
	implements MeterSimulated, SymmetricMeter, OpenemsComponent, ModbusComponent { 

	private Config config = null;

	public MeterSimulatedImpl() {
		super(
				OpenemsComponent.ChannelId.values(), //
				SymmetricMeter.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				MeterSimulated.ChannelId.values() //
		);
	}

	@Reference
	protected ConfigurationAdmin cm; 

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus); 
	}

	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsException { 
		if(super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm, "Modbus", config.modbus_id())) {
			return;
		}
		this.config = config;
	}

	@Deactivate
	protected void deactivate() { 
		super.deactivate();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() throws OpenemsException { 
		return new ModbusProtocol(this, 
				new FC3ReadRegistersTask(1000, Priority.HIGH, 
						m(SymmetricMeter.ChannelId.ACTIVE_POWER, new FloatDoublewordElement(1000)))); 
	}

	@Override
	public MeterType getMeterType() { 
		return this.config.type();
	}

	@Override
	public String debugLog() { 
		return "L:" + this.getActivePower().asString();
	}
}