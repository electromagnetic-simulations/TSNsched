package com.tsnsched.core.components;
//TSNsched uses the Z3 theorem solver to generate traffic schedules for Time Sensitive Networking (TSN)
//
//    Copyright (C) 2021  Aellison Cassimiro
//    
//    TSNsched is licensed under the GNU GPL version 3 or later:
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//    
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <https://www.gnu.org/licenses/>.


import java.io.Serializable;
import java.util.ArrayList;

import com.microsoft.z3.*;

import com.tsnsched.core.schedule_generator.SlotArrangementMode;

/**
 * [Class]: Cycle
 * [Usage]: Contains all properties of a TSN cycle.
 * After the specification of its properties through
 * user input, the toZ3 method can be used to convert
 * the values to z3 variables and query the unknown 
 * values. 
 * 
 * There is no direct reference from a cycle to 
 * its time slots. The user must use a priority from a flow
 * to reference the time window of a cycle. This happens
 * because of the generation of z3 variables. 
 * 
 * For example, if I want to know the duration of the time slot 
 * reserved for the priority 3, it most likely will return a value
 * different from the actual time slot that a flow is making use. 
 * This happens due to the way that z3 variables are generated. A flow
 * fragment can have a priority 3 on this cycle, but its variable 
 * name will be "flowNfragmentMpriority". Even if z3 says that this 
 * variable 3, the reference to the cycle duration will be called 
 * "cycleXSlotflowNfragmentMpriorityDuration", which is clearly different
 * from "cycleXSlot3Duration".
 * 
 * To make this work, every flow that has the same time window has the 
 * same priority value. And this value is limited to a maximum value 
 * (numOfSlots). So, to access the slot start and duration of a certain
 * priority, a flow fragment from that priority must be retrieved.
 * 
 * This also deals with the problem of having unused priorities,
 * which can end up causing problems due to constraints of guard band
 * and such.
 * 
 */
public class Cycle implements Serializable {
	private static final long serialVersionUID = 1L;
	private String portName = "";
	private String name = "";

    private boolean wrapTransmission = false;

	static int instanceCounter = 0;
	static int cycleInstanceCounter = 0;
	private int instance;
	
	private double upperBoundCycleTime;
    private double lowerBoundCycleTime;
    private double firstCycleStart;
    private double maximumSlotDuration;

    private double cycleDuration;
    private double cycleStart = -1;

    private transient ArrayList<ArrayList<RealExpr>> slotStartZ3 = new ArrayList<ArrayList<RealExpr>>();
	private transient ArrayList<ArrayList<RealExpr>> slotDurationZ3 = new ArrayList<ArrayList<RealExpr>>();
    
    private ArrayList<Integer> slotsUsed = new ArrayList<Integer>();
    private ArrayList<ArrayList<Double>> slotStart = new ArrayList<ArrayList<Double>>();
    private ArrayList<ArrayList<Double>> slotDuration = new ArrayList<ArrayList<Double>>();
    
    private transient RealExpr cycleDurationZ3;
    private transient RealExpr firstCycleStartZ3;
    private transient RealExpr maximumSlotDurationZ3;
    private int numOfPrts = 8;
    
    private int numOfSlots = 1;
    
    SlotArrangementMode slotArrangementMode = SlotArrangementMode.AGRESSIVEDESCENT;

	private ArrayList<Integer> numOfSlotsPerPrt;

	/**
     * [Method]: Cycle
     * [Usage]: Overloaded method of this class. Will create 
     * an object setting up the minimum and maximum cycle time,
     * the first cycle start and the maximum duration of a 
     * priority slot. Other constructors either are deprecated 
     * or set parameters that will be used in future works.
     * 
     * 
     * @param upperBoundCycleTime   Maximum size of the cycle
     * @param lowerBoundCycleTime   Minimum size of the cycle
     * @param maximumSlotDuration   Every priority slot should have up this time units
     */
    public Cycle(double upperBoundCycleTime, 
                 double lowerBoundCycleTime, 
                 double maximumSlotDuration) {
    	
    	instanceCounter++;        
        this.instance = instanceCounter;
        this.name = "cycle" + Integer.toString(instanceCounter);
        
        this.upperBoundCycleTime = upperBoundCycleTime;
        this.lowerBoundCycleTime = lowerBoundCycleTime;
        this.maximumSlotDuration = maximumSlotDuration;
        this.firstCycleStart = 0;
    }
    
    
    public Cycle(double maximumSlotDuration) {
        instanceCounter++;        
        this.instance = instanceCounter;
        this.name = "cycle" + Integer.toString(instanceCounter);
        
        this.maximumSlotDuration = maximumSlotDuration;
        this.firstCycleStart = 0;
    }
    
    
    /**
     * [Method]: Cycle
     * [Usage]: Overloaded method of this class. Will create 
     * an object setting up the minimum and maximum cycle time,
     * the first cycle start and the maximum duration of a 
     * priority slot.
     * 
     * 
     * @param upperBoundCycleTime   Maximum size of the cycle
     * @param lowerBoundCycleTime   Minimum size of the cycle
     * @param firstCycleStart       Where the first cycle should start
     * @param maximumSlotDuration   Every priority slot should have up this time units
     */
    public Cycle(double upperBoundCycleTime, 
                 double lowerBoundCycleTime, 
                 double firstCycleStart,
                 double maximumSlotDuration) {
    	
    	instanceCounter++;        
        this.instance = instanceCounter;
        this.name = "cycle" + Integer.toString(instanceCounter);
    	
        this.upperBoundCycleTime = upperBoundCycleTime;
        this.lowerBoundCycleTime = lowerBoundCycleTime;
        this.firstCycleStart = firstCycleStart;
        this.maximumSlotDuration = maximumSlotDuration;
    }
    
    /**
     * [Method]: Cycle
     * [Usage]: Overloaded method of this class. Will create 
     * an object setting up the minimum and maximum cycle time,
     * the first cycle start and the maximum duration of a 
     * priority slot. These properties must be given as z3 
     * variables.
     * 
     * 
     * @param upperBoundCycleTimeZ3   Maximum size of the cycle
     * @param lowerBoundCycleTimeZ3   Minimum size of the cycle
     * @param firstCycleStartZ3       Where the first cycle should start
     * @param maximumSlotDurationZ3   Every priority slot should have up this time units
     */    
    public Cycle(RealExpr upperBoundCycleTimeZ3, 
                 RealExpr lowerBoundCycleTimeZ3, 
                 RealExpr firstCycleStartZ3,
                 RealExpr maximumSlotDurationZ3) {
    	instanceCounter++;        
        this.instance = instanceCounter;
        this.name = "cycle" + Integer.toString(instanceCounter);
        
        // this.upperBoundCycleTimeZ3 = upperBoundCycleTimeZ3;
        // this.lowerBoundCycleTimeZ3 = lowerBoundCycleTimeZ3;
        this.firstCycleStartZ3 = firstCycleStartZ3;
        //this.guardBandSizeZ3 = guardBandSize;
        this.maximumSlotDurationZ3 = maximumSlotDurationZ3;
    }
    
    /**
     * [Method]: toZ3
     * [Usage]: After setting all the numeric input values of the class,
     * generates the z3 equivalent of these values and creates any extra
     * variable needed.
     * 
     * @param ctx      Context variable containing the z3 environment used
     */
    public void toZ3(Context ctx) {
        //instanceCounter++;
        
        this.cycleDurationZ3 = ctx.mkRealConst("cycle" + Integer.toString(instance) + "Duration");
        this.firstCycleStartZ3 = ctx.mkRealConst("cycle" + Integer.toString(instance) + "Start");
        // this.firstCycleStartZ3 = ctx.mkReal(Double.toString(0));
        // this.firstCycleStartZ3 = ctx.mkReal(Double.toString(firstCycleStart));
        this.maximumSlotDurationZ3 = ctx.mkReal(Double.toString(maximumSlotDuration));
        
        this.slotStartZ3 = new ArrayList<ArrayList<RealExpr>>();
        this.slotDurationZ3 = new ArrayList<ArrayList<RealExpr>>();
        
        for(int i = 0; i < this.numOfPrts; i++) {
        	this.slotStartZ3.add(new ArrayList<RealExpr>());
        	this.slotDurationZ3.add(new ArrayList<RealExpr>());
        	for(int j = 0; j < this.numOfSlots; j++) {
        		this.slotStartZ3.get(i).add(ctx.mkRealConst(this.name + "prt" + (i+1) + "slot" + (j+1) + "start"));
        	}
        	for(int j = 0; j < this.numOfSlots; j++) {
        		this.slotDurationZ3.get(i).add(ctx.mkRealConst(this.name + "prt" + (i+1) + "slot" + (j+1) + "duration"));
        	}
        }
    }
    
    
    /**
     * [Method]: cycleStartZ3
     * [Usage]: Returns the time of the start of a cycle
     * specified by its index. The index is given as a z3 
     * variable
     * 
     * @param ctx       Context containing the z3 environment
     * @param index     Index of the desired cycle
     * @return          Z3 variable containing the cycle start time
     */
    public RealExpr cycleStartZ3(Context ctx, RealExpr index){
        return (RealExpr) ctx.mkITE( 
                ctx.mkGe(index, ctx.mkInt(1)),
                ctx.mkAdd(
                        firstCycleStartZ3,
                        ctx.mkMul(cycleDurationZ3, index)
                        ), 
                firstCycleStartZ3);

     }
    
    /**
     * [Method]: cycleStartZ3
     * [Usage]: Returns the time of the start of a cycle
     * specified by its index. The index is given as integer
     * 
     * @param ctx       Context containing the z3 environment
     * @param auxIndex     Index of the desired cycle
     * @return          Z3 variable containing the cycle start time
     */
    public RealExpr cycleStartZ3(Context ctx, int auxIndex){
        RealExpr index = ctx.mkReal(auxIndex);
        
        return (RealExpr) ctx.mkITE( 
                ctx.mkGe(index, ctx.mkInt(1)), 
                ctx.mkAdd(
                        firstCycleStartZ3,
                        ctx.mkMul(cycleDurationZ3, index)
                        ), 
                firstCycleStartZ3);

     }
    
    
    /**
     * [Method]: addSlotUsed
     * [Usage]: After generating the schedule, the z3 values are
     * converted to doubles and integers. The used slots are now 
     * placed on a arrayList, and so are the slot start and duration.
     * 
     * @param prt           Priority of the slot to be added
     * @param sStart        Slot start of the slot to be added
     * @param sDuration     Slot duration of the slot to be added
     */
    public void addSlotUsed(int prt, ArrayList<Double> sStart, ArrayList<Double> sDuration) {
        
        if(!this.slotsUsed.contains(prt)) {
            this.slotsUsed.add(prt);
            this.slotStart.add(sStart);
            this.slotDuration.add(sDuration);
        }
        
    }
    
    
    /**
     * [Method]: loadZ3
     * [Usage]: From the loaded primitive values of the class
     * obtained in the deserialization process, initialize the
     * z3 variables.
     * 
     * @param ctx		Context object of z3
     * @param solver	Solver object to add constraints
     */
    public void loadZ3(Context ctx, Solver solver) {
    	solver.add(
			ctx.mkEq(
				this.cycleDurationZ3,
				ctx.mkReal(Double.toString(this.cycleDuration))
			)
		);
    	
    	solver.add(
			ctx.mkEq(
				this.firstCycleStartZ3,
				ctx.mkReal(Double.toString(this.firstCycleStart))
			)
		);
    	    	

    	for(int prt : this.getSlotsUsed()) {
    		
    		// Where are the slot duration per priority instantiated? Must do it before loading
    		
    		for(int slotIndex = 0; slotIndex < this.numOfSlots; slotIndex++) {
    			solver.add(
					ctx.mkEq(
						this.slotStartZ3.get(prt).get(slotIndex),
						ctx.mkReal(Double.toString(this.slotStart.get(this.slotsUsed.indexOf(prt)).get(slotIndex)))	
					)
				);
    		}
    		
    		/*
    		for(int slotIndex = 0; slotIndex < this.numOfSlots; slotIndex++) {
    			solver.add(
					ctx.mkEq(
						this.slotDurationZ3.get(prt).get(slotIndex),
						ctx.mkReal(Double.toString(this.slotDuration.get(prt).get(slotIndex)))	
					)
				);
    		}
    		*/
    	}
    	
    	
    	
    }
    
    /*
     *  GETTERS AND SETTERS
     */
    
    
    public double getUpperBoundCycleTime() {
        return upperBoundCycleTime;
    }

    public void setUpperBoundCycleTime(double upperBoundCycleTime) {
        this.upperBoundCycleTime = upperBoundCycleTime;
    }

    public double getLowerBoundCycleTime() {
        return lowerBoundCycleTime;
    }

    public void setLowerBoundCycleTime(double lowerBoundCycleTime) {
        this.lowerBoundCycleTime = lowerBoundCycleTime;
    }

    public double getCycleDuration() {
        return cycleDuration;
    }

    public double getCycleStart() {
        return cycleStart;
    }

    public void useTransmissionWrapping(){
        this.wrapTransmission = true;
    }

    public boolean getWrapTransmission() {
        return wrapTransmission;
    }

    public void setWrapTransmission(boolean wrapTransmission) {
        this.wrapTransmission = wrapTransmission;
    }

    public void setCycleStart(double cycleStart) {
        this.cycleStart = cycleStart;
    }
    
    public void setCycleDuration(double cycleDuration) {
        this.cycleDuration = cycleDuration;
    }

    public double getFirstCycleStart() {
        return firstCycleStart;
    }

    public void setFirstCycleStart(double firstCycleStart) {
        this.firstCycleStart = firstCycleStart;
    }

    public double getMaximumSlotDuration() {
        return maximumSlotDuration;
    }

    public void setMaximumSlotDuration(double maximumSlotDuration) {
        this.maximumSlotDuration = maximumSlotDuration;
    }

    
    public RealExpr slotStartZ3(Context ctx, IntExpr prt, IntExpr index) {
        return ctx.mkRealConst(this.name + "priority" + prt.toString() + "slot" + index.toString() + "Start");
    }
    
    public RealExpr slotStartZ3(Context ctx, int auxPrt, int auxIndex) {
        IntExpr index = ctx.mkInt(auxIndex);
        IntExpr prt = ctx.mkInt(auxPrt);
        return ctx.mkRealConst(this.name + "priority" + prt.toString() + "slot" + index.toString() + "Start");
    }
    
   
    public RealExpr slotDurationZ3(Context ctx, IntExpr prt, IntExpr index) {
        return ctx.mkRealConst(this.name + "priority" + prt.toString() + "slot" + index.toString() + "Duration");
    }
    
    public RealExpr slotDurationZ3(Context ctx, int auxPrt, int auxIndex) {
        IntExpr index = ctx.mkInt(auxIndex);
        IntExpr prt = ctx.mkInt(auxPrt);
        return ctx.mkRealConst(this.name + "priority" + prt.toString() + "slot" + index.toString() + "Duration");
    }

    public RealExpr getCycleDurationZ3() {
        return cycleDurationZ3;
    }

    public void setCycleDurationZ3(RealExpr cycleDuration) {
        this.cycleDurationZ3 = cycleDuration;
    }

    public RealExpr getFirstCycleStartZ3() {
        return firstCycleStartZ3;
    }

    public void setFirstCycleStartZ3(RealExpr firstCycleStart) {
        this.firstCycleStartZ3 = firstCycleStart;
    }
    
    public int getNumOfPrts() {
        return this.numOfPrts;
    }
    
    public void setNumOfPrts(int numOfPrts) {
        this.numOfPrts = numOfPrts;
    }

    public int getNumOfSlots(int prt) {
    	//System.out.println("          Num of slots for priority " + prt + " is: " + numOfSlotsPerPrt.get(prt));
		return numOfSlotsPerPrt.get(prt);
	}

	public void setNumOfSlots(int numOfSlots) {
		
		int currentNumOfSlots = numOfSlots;
		
		this.numOfSlotsPerPrt = new ArrayList<Integer>();
				
		switch (this.slotArrangementMode) {
			case AGRESSIVEDESCENT:
				for(int i = 0; i < this.numOfPrts; i++) {
					numOfSlotsPerPrt.add(currentNumOfSlots);
					currentNumOfSlots = currentNumOfSlots/2;
					if(currentNumOfSlots<1) {
						currentNumOfSlots=1;
					}
				}
				break;
			case EQUALDISTRIBUTION:
				for(int i = 0; i < this.numOfPrts; i++) {
					numOfSlotsPerPrt.add(numOfSlots/this.numOfPrts);
				}
				break;
			case MAXCAPACITY:
				for(int i = 0; i < this.numOfPrts; i++) {
					numOfSlotsPerPrt.add(numOfSlots);
				}
				break;
		}
		this.numOfSlots = numOfSlots;

	}
    
    public RealExpr getMaximumSlotDurationZ3() {
        return maximumSlotDurationZ3;
    }
    
    public void setMaximumSlotDurationZ3(RealExpr maximumSlotDuration) {
        this.maximumSlotDurationZ3 = maximumSlotDuration;
    }
    
    public ArrayList<Integer> getSlotsUsed (){
        return this.slotsUsed;
    }
    
    public ArrayList<ArrayList<Double>> getSlotDuration() {
        return slotDuration;
    }
    
    public double getSlotStart(int prt, int index) {
        return this.slotStart.get(this.slotsUsed.indexOf(prt)).get(index);
    }
    
    public ArrayList<Double> getSlotStartList(int prt) {
        return this.slotStart.get(this.slotsUsed.indexOf(prt));
    }
        
    public double getSlotDuration(int prt, int index) {
        return this.slotDuration.get(this.slotsUsed.indexOf(prt)).get(index);
    }
	
    public ArrayList<Double>  getSlotDurationList(int prt) {
        return this.slotDuration.get(this.slotsUsed.indexOf(prt));
    }
    
    public String getPortName() {
		return portName;
	}


	public void setPortName(String portName) {
		this.portName = portName;
	}

	public RealExpr getSlotStartZ3(int prt, int slotNum) {
		return this.slotStartZ3.get(prt).get(slotNum);
	}
	
	public String getName() {
		return name;
	}

	public void setName(String cycleName) {
		this.name = cycleName;
	}
	
	public int getInstance() {
		return instance;
	}

	public void setInstance(int instance) {
		this.instance = instance;
	}


    public SlotArrangementMode getSlotArrangementMode() {
		return slotArrangementMode;
	}

	public void setSlotArrangementMode(SlotArrangementMode slotArrangementMode) {
		this.slotArrangementMode = slotArrangementMode;
	}
	/*
	public RealExpr getSlotDurationZ3(int prt, int slotNum) {
		return this.slotDurationZ3.get(prt).get(slotNum);
	}
	*/
}
