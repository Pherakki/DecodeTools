package de.phoenixstaffel.decodetools.res;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import de.phoenixstaffel.decodetools.Main;
import de.phoenixstaffel.decodetools.Utils;
import de.phoenixstaffel.decodetools.dataminer.Access;
import de.phoenixstaffel.decodetools.dataminer.FileAccess;

public class ResFile {
    private ResPayload root;
    
    public ResFile(Access source) {
        int dataStart = ResPayload.Payload.valueOf(null, source.readLong(0)).getDataStart(source);
        root = ResPayload.craft(source, dataStart, null, -1, null);
    }
    
    public ResPayload getRoot() {
        return root;
    }
    
    public void repack(File file) {
        file.delete();
        if (!file.exists())
            try {
                file.createNewFile();
            }
            catch (IOException e1) {
                Main.LOGGER.log(Level.WARNING, "Exception while writing new .res file.", e1);
            }
        
        try (Access dest = new FileAccess(file); IResData data = new ResData()) {
            root.writeKCAP(dest, data);
            dest.setPosition(Utils.getPadded(root.getSizeOfRoot(), 0x80));
            dest.writeByteArray(data.getStream().toByteArray());
        }
        catch (IOException e) {
            Main.LOGGER.log(Level.WARNING, "Exception while writing new .res file.", e);
        }
    }
}
