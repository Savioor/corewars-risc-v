package il.co.codeguru.corewars8086.gui;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import il.co.codeguru.corewars8086.cpu.riscv.CpuStateRiscV;
import il.co.codeguru.corewars8086.cpu.x86.CpuState;
import il.co.codeguru.corewars8086.memory.MemoryEventListener;
import il.co.codeguru.corewars8086.jsadd.Format;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemoryImpl;
import il.co.codeguru.corewars8086.war.Competition;
import il.co.codeguru.corewars8086.war.CompetitionEventListener;
import il.co.codeguru.corewars8086.war.War;

import il.co.codeguru.corewars8086.gui.widgets.*;
import il.co.codeguru.corewars8086.war.Warrior;

import java.util.HashMap;

public class CpuFrame  implements CompetitionEventListener, MemoryEventListener {
	
	//private War currentWar;
	private CompetitionWindow m_mainwnd;
	private String m_currentWarriorLabel = null;
	private int m_currentWarriorIndex = -1; // faster to use index than label during debug
	
	private Competition competition;
	private int m_base = 16;


	private RegisterField[] registers;
	private RegisterField regF, regPc;
	
	private FlagFields flagOF,flagDF,flagIF,flagTF,
						flagSF,flagZF,flagAF,flagPF,
						flagCF;

    private HTMLElement cpuPanel;

    public MemRegionView stackView;
	public MemRegionView sharedMemView;

    public void setVisible(boolean v) {
        if (v)
            cpuPanel.style.display = "";
        else
            cpuPanel.style.display = "none";
    }

	public void setSelectedPlayer(String playerLabel, boolean isDebugMode) {
		m_currentWarriorLabel = playerLabel;
		m_currentWarriorIndex = -1; // invalidate

		if (isDebugMode) {
			// need to do this first so that reading the registers would put this ss:sp in the right place
			initMemRegions(false);
			updateFields();
		}
	}

	public int regChanged_callback(String name, String value)
	{
		War currentWar = competition.getCurrentWar();
		if (currentWar == null)
			return 1;

		CpuStateRiscV state = currentWar.getWarriorByLabel(m_currentWarriorLabel).getCpuState();

		int v;

		value = value.trim();
		if (value.length() > 2 && value.charAt(0) == '0' && value.charAt(1) == 'x')
			value = value.substring(2);

		try {
			if (m_base == 10)
				v = Integer.parseInt(value, 10);
			else
				v = Integer.parseInt(value, 16);
		}
		catch(NumberFormatException e) {
			m_mainwnd.errorPreventsStep(true);
			return (m_base == 10) ? -2000000 : -1000000;
		}
		if (v < 0 || v > 0xffff) {
			m_mainwnd.errorPreventsStep(true);
			return -3000000;
		}
		m_mainwnd.errorPreventsStep(false);


		switch(name) {
			case "PC": state.setPc(v); changedCSIP(); break;
			case "Energy": state.setEnergy((short)v); break;
			case "Flags": state.setFlags((short)v); updateFlagBoxes(state); break;

			default:
				state.setReg(Integer.valueOf(name), v);
		}

		// reeval watch - might change depending on the register that just changed
		m_stateAccess.state = state;
		for (WatchEntry entry : m_watches.values()) {
			entry.evalAndDisplay();
		}

		return v;
	}

	public void changedCSIP() {
		m_mainwnd.m_codeEditor.onEndRound(); // redraw the ip indicator

		m_mainwnd.battleFrame.onEndRound(); // make it redraw ip pointers.

	}

	public void onMemoryWrite(RealModeAddress address, byte value){
		for (WatchEntry entry : m_watches.values()) {
			entry.evalAndDisplay();
		}
	}

	public void onWriteState(MemoryEventListener.EWriteState state)
	{}

	public void updateFlagBoxes(CpuState state) {
		flagOF.setValue( state.getOverflowFlag());
		flagDF.setValue( state.getDirectionFlag() );
		flagIF.setValue( state.getInterruptFlag() );
		flagTF.setValue( state.getTrapFlag() );
		flagSF.setValue( state.getSignFlag() );
		flagZF.setValue( state.getZeroFlag() );
		flagAF.setValue( state.getAuxFlag() );
		flagPF.setValue( state.getParityFlag() );
		flagCF.setValue( state.getCarryFlag() );
	}

	public void flagChanged_callback(String name, boolean v)
	{
		War currentWar = competition.getCurrentWar();
		if (currentWar == null)
			return;

		CpuState state = currentWar.getWarriorByLabel(m_currentWarriorLabel).getCpuState();

		switch(name) {
		case "OF": state.setOverflowFlag(v); break;
		case "DF": state.setDirectionFlag(v); break;
		case "IF": state.setInterruptFlag(v); break;
		case "TF": state.setTrapFlag(v); break;
		case "SF": state.setSignFlag(v); break;
		case "ZF": state.setZeroFlag(v); break;
		case "AF": state.setAuxFlag(v); break;
		case "PF": state.setParityFlag(v); break;
		case "CF": state.setCarryFlag(v); break;
		}
		regF.setValue( state.getFlags());
	}

	public CpuFrame(Competition c, CompetitionWindow mainwnd)
	{
		exportMethods();
		m_mainwnd = mainwnd;

		this.competition = c;

        cpuPanel = (HTMLElement) DomGlobal.document.getElementById("cpuPanel");

        registers = new RegisterField[32];
		for(int i=0; i<32;i++)
		{
			registers[i] = new RegisterField(Integer.toString(i), this);
		}
		regPc = new RegisterField("PC", this);
		regF = new RegisterField("Flags", this);
		
		//Flags
		
		flagOF = new FlagFields("OF", this);
		flagDF = new FlagFields("DF", this);
		flagIF = new FlagFields("IF", this);
		flagTF = new FlagFields("TF", this);
		flagSF = new FlagFields("SF", this);
		flagZF = new FlagFields("ZF", this);
		flagAF = new FlagFields("AF", this);
		flagPF = new FlagFields("PF", this);
		flagCF = new FlagFields("CF", this);

		stackView = new MemRegionView("stackList", "mk");
		sharedMemView = new MemRegionView("sharedMemList", "mh");

		m_parser.m_stateAccess = m_stateAccess;
	}

	public native void exportMethods() /*-{
        var that = this
        $wnd.j_setRegistersBase = $entry(function(b) { that.@il.co.codeguru.corewars8086.gui.CpuFrame::j_setRegistersBase(I)(b) });
        $wnd.j_watchTextChanged = $entry(function(s,i) { return that.@il.co.codeguru.corewars8086.gui.CpuFrame::j_watchTextChanged(Ljava/lang/String;I)(s,i) });
        $wnd.j_addWatch = $entry(function(i) { return that.@il.co.codeguru.corewars8086.gui.CpuFrame::j_addWatch(I)(i) });
        $wnd.j_delWatch = $entry(function(i) { return that.@il.co.codeguru.corewars8086.gui.CpuFrame::j_delWatch(I)(i) });

	}-*/;

	public void j_setRegistersBase(int base) {
		m_base = base;
		for(RegisterField reg : registers)
		{
			reg.setBase(base);
		}
		regF.setBase(base);
		// setBase already updates the value if that's ok

        for (WatchEntry entry : m_watches.values()) {
            entry.base = base;
            entry.evalAndDisplay();
        }
	}


	
	public void updateFields(){
		War currentWar = competition.getCurrentWar();
		if (currentWar == null)
			return;
		if (m_currentWarriorIndex == -1) {
			m_currentWarriorIndex = currentWar.getWarriorByLabel(m_currentWarriorLabel).m_myIndex;
		}

		//CpuState state = currentWar.getWarrior(dropMenu.getSelectedIndex()).getCpuState();
		CpuStateRiscV state = currentWar.getWarrior(m_currentWarriorIndex).getCpuState();

		for(int i=0;i<32;i++)
		{
			registers[i].setValue(state.getReg(i));
		}
		regPc.setValue(state.getPc());
		regF.setValue(state.getFlags());
		
		updateFlagBoxes(state);
		stackView.moveToLine(RealModeAddress.linearAddress(state.getSS(), state.getSP()));

		// update watches;
		m_stateAccess.state = state;
		for (WatchEntry entry : m_watches.values()) {
            entry.evalAndDisplay();
		}

	}

	private static class StateAccess implements ExpressionParser.IStateAccess {
		public CpuStateRiscV state;
		public RealModeMemoryImpl memory;

		@Override
		public short getRegisterValue(String name) throws Exception{
		    if (state == null) {
		        throw new Exception("invalid state");
            }
            try {
				switch (name) {
					case "ENERGY":
						return state.getEnergy();
					case "FLAGS":
						return state.getFlags();
					default:
						return (short) state.getReg(Integer.valueOf(name));
				}
			}
			catch(IndexOutOfBoundsException e) {
				throw new RuntimeException("unknown register name " + name); // should not happen since we check before
			}
		}

		@Override
		public int getIdentifierValue(String name) throws Exception {
		    throw new Exception("unknown identifier " + name);
		}

		@Override
		public int getMemory(int addr, int seg, int size) throws Exception {
			short sseg = (short)seg;
			if (seg == -1)
				sseg = state.getDS();
			int linaddr = RealModeAddress.linearAddress(sseg, (short)addr);
			if (size == 1)
				return memory.readByte(linaddr) & 0xff;
			else
				return memory.read16Bit(new RealModeAddress(linaddr)) & 0xffff;
		}

	}

	class WatchEntry {
		public ExpressionParser.INode root;
		public HTMLElement resultElem;
		boolean isValid = false;
        int base = 16;

        public void setResult(int v) {
            String sv;
            if (base == 16)
                sv = Format.hex4(v);
            else
                sv = Integer.toString(v);
            setResult(sv);
        }

		public void setResult(String v) {
            Format.setInnerText(resultElem, v);
            resultElem.title = v; // tooltip also shows the same text in case it is obscured
        }

        public void evalAndDisplay() {
            if (!isValid)
                return;
            try {
                int res = root.eval();
                setResult(res);
            } catch (Exception e) {
                Console.log("watch error: " + e.getMessage());
                setResult("Error: " + e.getMessage());
            }
        }
	}

	private HashMap<Integer, WatchEntry> m_watches = new HashMap<>();
	private StateAccess m_stateAccess = new StateAccess();
	private ExpressionParser m_parser = new ExpressionParser();

	void j_addWatch(int watchId) {
        WatchEntry entry = new WatchEntry();
        m_watches.put(watchId, entry);
        entry.resultElem = (HTMLElement)DomGlobal.document.getElementById("watch" + Integer.toString(watchId) + "_val" );
        assert entry.resultElem != null : "did not find watch result element";
        Console.debug("Watchs: " + Integer.toString(m_watches.size()));
    }

    void j_delWatch(int watchId) {
        m_watches.remove(watchId);
    }

    private boolean onlySpaces(String s) {
		//return s.chars().anyMatch(c -> c != ' ');
	    for(int i = 0; i < s.length(); ++i) {
	        char c = s.charAt(i);
	        if (c != ' ')
	            return false;
        }
	    return true;
    }

    // returns true if string is not empty or only spaces
	boolean j_watchTextChanged(String s, int watchId)
    {
        WatchEntry entry = m_watches.get(watchId);
        assert entry != null : "did not find watch";
        if (onlySpaces(s)) {
            entry.isValid = false;
            entry.setResult("");
            return false;
        }

        entry.isValid = false;
		try {
			entry.root = m_parser.eval(s);
			entry.isValid = true;
		}
		catch (Exception e) { // might be an exception from ast eval which doesn't make the watch not valid
			Console.debug("Watch parse error: " + e.getMessage());
            entry.setResult("Error: " + e.getMessage());
		}
		entry.evalAndDisplay();

		return true;
	}


	// set the mem regions with the correct address region and values
	// force if we must reread the memory in a new battle (don't keep the old one but it may have the same regions)
	void initMemRegions(boolean force)
	{
		War currentWar = competition.getCurrentWar();
		if (currentWar == null)
			return;

		Warrior warrior = currentWar.getWarriorByLabel(m_currentWarriorLabel);

		stackView.initMemRegion(warrior.m_stackWritableRegion, currentWar.getMemory(), force);
		sharedMemView.initMemRegion(warrior.m_sharedWritableRegion, currentWar.getMemory(), force);

		m_stateAccess.memory = currentWar.getMemory();
	}

	@Override
	public void onWarPreStartClear() {}


	@Override
	public void onWarStart() {
		m_currentWarriorIndex = -1; // invalidate

		initMemRegions(true);

	}

	@Override
	public void onWarEnd(int reason, String winners, boolean inDebug) {
		//m_currentWarriorIndex = -1;
	}

	@Override
	public void onRound(int round) {
	}

	@Override
	public void onWarriorBirth(Warrior w) {
	}
	@Override
	public void onPaused() {}
	@Override
	public void onNoneAlive() {}


	@Override
	public void onWarriorDeath(Warrior warrior, String reason) {
	}
	@Override
	public void onCompetitionStart() {
	}
	@Override
	public void onCompetitionEnd() {
	}

	@Override
	public void onEndRound() {
		this.updateFields();
	}

}
