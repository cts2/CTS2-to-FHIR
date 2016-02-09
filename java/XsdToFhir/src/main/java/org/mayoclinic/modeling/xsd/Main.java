package org.mayoclinic.modeling.xsd;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.StructureDefinition;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;

import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Bryn on 12/1/2015.
 */
public class Main {
    private String source;
    private String dest;
    private String modelName;
    private String fhirPath;

    public static void main(String[] args) throws Exception {
        Main self = new Main();
        self.source = getParam(args, "source");
        self.dest = getParam(args, "dest");
        self.modelName = getParam(args, "modelName");
        self.fhirPath = getParam(args, "fhirPath");
        if (self.source == null || self.dest == null) {
            System.out.println("XSD to FHIR StructureDefinition Converter");
            System.out.println("This tool takes 3 parameters:");
            System.out.println("-source: XSD 1.1 XML representation of a model (required)");
            System.out.println("-dest: directory that will contain the resulting structure definitions, one for each type defined in the source XSD (required)");
            System.out.println("-modelName: name of the model being imported");
            System.out.println("-fhirPath: directory of the published Fhir specification");
        } else {
            self.execute();
        }
    }

    private static String getParam(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-"+name)) {
                return args[i+1];
            }
        }
        return null;
    }

    private void execute() throws Exception {

        // Load XSD
        File schemaFile = new File(source);
        InputStream is = new FileInputStream(schemaFile);
        XmlSchemaCollection schemaCol = new XmlSchemaCollection();
        schemaCol.setBaseUri(schemaFile.getParent());
        XmlSchema schema = schemaCol.read(new StreamSource(is));

        if (!Files.exists(Paths.get(dest))) {
            Files.createDirectory(Paths.get(dest));
        }

        List<StructureDefinition> fhirTypes = loadFhirTypes(fhirPath);

        for (StructureDefinition sd : XsdImporter.fromSchema(schema, modelName, fhirTypes, new XsdImportOptions())) {
            File destFile = new File(dest, String.format("%s.xml", sd.getId()));
            FileWriter fw = new FileWriter(destFile);
            FhirContext ctx = FhirContext.forDstu2();

            ctx.newXmlParser().setPrettyPrint(true).encodeResourceToWriter(sd, fw);
            //new XmlParser().setOutputStyle(IParser.OutputStyle.PRETTY).compose(new FileOutputStream(destFile), sd);
        }
    }

    private List<StructureDefinition> loadFhirTypes(String fhirPath) {
        List<StructureDefinition> fhirTypes = new ArrayList<StructureDefinition>();

        fhirTypes.add(loadFhirType("element"));
        fhirTypes.add(loadFhirType("instant"));
        fhirTypes.add(loadFhirType("time"));
        fhirTypes.add(loadFhirType("date"));
        fhirTypes.add(loadFhirType("dateTime"));
        fhirTypes.add(loadFhirType("decimal"));
        fhirTypes.add(loadFhirType("integer"));
        fhirTypes.add(loadFhirType("unsignedInt"));
        fhirTypes.add(loadFhirType("positiveInt"));
        fhirTypes.add(loadFhirType("boolean"));
        fhirTypes.add(loadFhirType("base64binary"));
        fhirTypes.add(loadFhirType("uri"));
        fhirTypes.add(loadFhirType("oid"));
        fhirTypes.add(loadFhirType("string"));
        fhirTypes.add(loadFhirType("code"));
        fhirTypes.add(loadFhirType("id"));
        fhirTypes.add(loadFhirType("markdown"));
        fhirTypes.add(loadFhirType("ratio"));
        fhirTypes.add(loadFhirType("quantity"));
        fhirTypes.add(loadFhirType("period"));
        fhirTypes.add(loadFhirType("range"));
        fhirTypes.add(loadFhirType("coding"));
        fhirTypes.add(loadFhirType("codeableconcept"));
        fhirTypes.add(loadFhirType("attachment"));
        fhirTypes.add(loadFhirType("humanname"));
        fhirTypes.add(loadFhirType("address"));
        fhirTypes.add(loadFhirType("contactpoint"));
        fhirTypes.add(loadFhirType("timing"));
        fhirTypes.add(loadFhirType("identifier"));
        fhirTypes.add(loadFhirType("signature"));
        fhirTypes.add(loadFhirType("annotation"));

        return fhirTypes;
    }

    private StructureDefinition loadFhirType(String typeName) {
        return loadFhirType(new File(fhirPath, String.format("%s.profile.xml", typeName)));
    }

    private StructureDefinition loadFhirType(File typeFile) {
        FileReader fr = null;
        try {
            fr = new FileReader(typeFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        FhirContext ctx = FhirContext.forDstu2();
        return ctx.newXmlParser().parseResource(StructureDefinition.class, fr);
    }
}
