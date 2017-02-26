package de.phoenixstaffel.decodetools.gui;

import java.util.Observer;
import java.util.logging.Logger;

import javax.swing.JPanel;

public abstract class EditorPanel extends JPanel implements Observer {
    private static final long serialVersionUID = -4112706371340135417L;
    
    static final Logger log = Logger.getLogger("DecodeTool");
    
    private EditorModel model;
    
    public EditorPanel(EditorModel model) {
        setModel(model);
    }
    
    public void setModel(EditorModel model) {
        if (this.model != null)
            this.model.deleteObserver(this);
        this.model = model;
        if (this.model != null)
            this.model.addObserver(this);
    }
    
    public EditorModel getModel() {
        return model;
    }
}