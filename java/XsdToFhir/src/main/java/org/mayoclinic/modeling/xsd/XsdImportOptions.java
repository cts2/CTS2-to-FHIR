package org.mayoclinic.modeling.xsd;

/**
 * Created by Bryn on 2/5/2016.
 */
public class XsdImportOptions {
    public XsdImportOptions() {
        // By default, do not generate types for extensions of simple types
        generateSimpleTypeExtensions = false;
    }

    private boolean generateSimpleTypeExtensions;
    public boolean getGenerateSimpleTypeExtensions() {
        return generateSimpleTypeExtensions;
    }

    public void setGenerateSimpleTypeExtensions(boolean value) {
        generateSimpleTypeExtensions = value;
    }
}
