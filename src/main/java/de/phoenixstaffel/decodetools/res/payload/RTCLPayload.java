package de.phoenixstaffel.decodetools.res.payload;

import de.phoenixstaffel.decodetools.dataminer.Access;
import de.phoenixstaffel.decodetools.res.IResData;
import de.phoenixstaffel.decodetools.res.ResPayload;

public class RTCLPayload extends ResPayload {
    private String name;
    private int[] data;
    
    public RTCLPayload(Access source, int dataStart, KCAPPayload parent, int size, String name) {
        super(parent);
        this.name = name;
        
        data = new int[size / 4];
        
        for (int i = 0; i < data.length; i++)
            data[i] = source.readInteger();
    }
    
    @Override
    public int getSize() {
        return data.length * 4;
    }
    
    @Override
    public int getAlignment() {
        return 0x10;
    }
    
    @Override
    public Payload getType() {
        return Payload.RTCL;
    }
    
    @Override
    public void writeKCAP(Access dest, IResData dataStream) {
        for (int i : data)
            dest.writeInteger(i);
    }
}
