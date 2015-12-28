package com.falcon.server.server.core;

import java.util.ArrayList;
import java.util.List;

import com.falcon.server.server.Pipeline;
import com.falcon.server.server.Valve;

public class FalconPipeline implements Pipeline {

	public List<Valve> valves = new ArrayList<Valve>();

	public void addValve(Valve valve) {
		valves.add(valve);
	}

	public Valve getBasic() {
		return null;
	}

	public Valve getFirst() {
		return valves.get(0);
	}

	public Valve[] getValves() {
		return valves.toArray(new Valve[0]);
	}

	public void removeValve(Valve valve) {
		// TODO Auto-generated method stub

	}

	public void setBasic(Valve valve) {
		// TODO Auto-generated method stub

	}

	public void addValves(Valve[] vs) {

		for (Valve valve : vs) {
			valves.add(valve);
		}
	}

}
