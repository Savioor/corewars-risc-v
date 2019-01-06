package il.co.codeguru.corewars_riscv.memory;

import il.co.codeguru.corewars_riscv.utils.Logger;

import static il.co.codeguru.corewars_riscv.jsadd.Format.hex;

public class RestrictedMemory extends Memory{
    private Memory memory;
    private MemoryRegion[] allowedRegions;

    public RestrictedMemory(Memory raw, MemoryRegion[] allowedRegions)
    {
        this.memory = raw;
        this.allowedRegions = allowedRegions;
        setListener(raw.getListener());
    }

    private boolean isAddressAllowed(int index)
    {
        for(MemoryRegion region : allowedRegions)
        {
            if(region.isInRegion(index)){
                return true;
            }
        }
        return false;
    }

    @Override
    protected void setByte(int index, byte value) throws MemoryException {
        if(!isAddressAllowed(index)) throw new MemoryException("Write at forbidden location - at " + hex(index));
        memory.setByte(index, value);
    }

    @Override
    public byte loadByte(int index) throws MemoryException {
        if(!isAddressAllowed(index)) throw new MemoryException("Read at forbidden location - at " + hex(index));
        return memory.loadByte(index);
    }
}
