package org.mayoclinic.modeling.xsd;

import ca.uhn.fhir.model.dstu2.composite.ElementDefinitionDt;
import ca.uhn.fhir.model.dstu2.resource.StructureDefinition;
import ca.uhn.fhir.model.dstu2.valueset.ConformanceResourceStatusEnum;
import ca.uhn.fhir.model.dstu2.valueset.ContactPointSystemEnum;
import ca.uhn.fhir.model.dstu2.valueset.StructureDefinitionKindEnum;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import ca.uhn.fhir.model.primitive.MarkdownDt;
import org.apache.ws.commons.schema.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Bryn on 12/1/2015.
 */
public class XsdImporter {
    private final XmlSchema schema;
    private final Map<String, StructureDefinition> definitions;
    private final Map<String, StructureDefinition> fhirDefinitions;
    private final Map<String, StructureDefinition> xsdTypeMap;
    private final Map<String, String> namespaces;
    private final String modelName;

    public static Iterable<StructureDefinition> fromSchema(XmlSchema schema, String modelName, List<StructureDefinition> fhirTypes) {
        XsdImporter importer = new XsdImporter(schema, modelName, fhirTypes);
        return importer.definitions.values();
    }

    public XsdImporter(XmlSchema schema, String modelName, List<StructureDefinition> fhirTypes) {
        this.schema = schema;
        this.fhirDefinitions = new HashMap<>();
        for (StructureDefinition sd : fhirTypes) {
            fhirDefinitions.put(sd.getName(), sd);
        }

        this.xsdTypeMap = new HashMap<>();
        loadXsdTypeMap();


        this.definitions = new HashMap<>();
        this.namespaces = new HashMap<>();
        this.modelName = modelName;
        this.namespaces.put(this.schema.getTargetNamespace(), this.modelName);

        this.generate();
    }

    private void loadXsdTypeMap() {
        xsdTypeMap.put("anyType", fhirDefinitions.get("Element")); // Not sure this is right...
        xsdTypeMap.put("anySimpleType", fhirDefinitions.get("Element"));
        xsdTypeMap.put("duration", fhirDefinitions.get("Duration"));
        xsdTypeMap.put("dateTime", fhirDefinitions.get("dateTime"));
        xsdTypeMap.put("gYear", fhirDefinitions.get("date"));
        xsdTypeMap.put("gYearMonth", fhirDefinitions.get("date"));
        xsdTypeMap.put("date", fhirDefinitions.get("date"));
        xsdTypeMap.put("time", fhirDefinitions.get("time"));
        xsdTypeMap.put("decimal", fhirDefinitions.get("decimal"));
        xsdTypeMap.put("double", fhirDefinitions.get("decimal"));
        xsdTypeMap.put("boolean", fhirDefinitions.get("boolean"));
        xsdTypeMap.put("base64Binary", fhirDefinitions.get("base64Binary"));
        xsdTypeMap.put("string", fhirDefinitions.get("string"));
        xsdTypeMap.put("anyURI", fhirDefinitions.get("uri"));
        xsdTypeMap.put("NCName", fhirDefinitions.get("string")); // Should this be code?
        xsdTypeMap.put("nonNegativeInteger", fhirDefinitions.get("unsignedInt"));
        xsdTypeMap.put("positiveInteger", fhirDefinitions.get("positiveInt"));
    }

    private void generate() {
        for (XmlSchemaType schemaType : schema.getSchemaTypes().values()) {
            resolveDefinition(schemaType);
        }
    }

    private String getQualifiedTypeName(QName schemaTypeName, Map<String, String> namespaces) {
        if (schemaTypeName == null) {
            throw new IllegalArgumentException("schemaTypeName is null");
        }

        String namespaceName = namespaces.get(schemaTypeName.getNamespaceURI());
        if (namespaceName == null) {
            // TODO: Import different namespaces into different models? Support mapping here?
            namespaceName = this.modelName;
            namespaces.put(schemaTypeName.getNamespaceURI(), namespaceName);
//            namespaceName = schemaTypeName.getPrefix(); // Doesn't always work, but should be okay for a fallback position...
//            if (namespaceName != null && !namespaceName.isEmpty()) {
//                namespaces.put(schemaTypeName.getNamespaceURI(), namespaceName);
//            }
        }

        if (namespaceName != null && !namespaceName.isEmpty()) {
            return namespaceName + '.' + schemaTypeName.getLocalPart().replace('-', '_');
        }

        return schemaTypeName.getLocalPart().replace('-', '_');
    }

    private String unqualify(String name) {
        int qualifierIndex = name.indexOf('.');
        if (qualifierIndex >= 0) {
            return name.substring(qualifierIndex + 1);
        }

        return name;
    }

    private String getDocumentation(XmlSchemaAnnotation annotation) {
        if (annotation != null) {
            for (XmlSchemaAnnotationItem i : annotation.getItems()) {
                if (i instanceof XmlSchemaDocumentation) {
                    StringBuilder sb = new StringBuilder();
                    NodeList nl = ((XmlSchemaDocumentation)i).getMarkup();
                    for (int idx = 0; idx < nl.getLength(); idx++) {
                        sb.append(nl.item(idx).getTextContent());
                    }
                    return sb.toString();
                }
            }
        }

        return "TODO: Description";
    }

    private StructureDefinition resolveDefinition(QName schemaTypeName) {
        if (schemaTypeName == null) {
            return null;
        }

        // TODO: Better story for mapping in general...
        if (schemaTypeName.getNamespaceURI().equals("http://www.w3.org/2001/XMLSchema")) {
            StructureDefinition sd = xsdTypeMap.get(schemaTypeName.getLocalPart());
            if (sd != null) {
                return sd;
            }
            else {
                throw new IllegalArgumentException(String.format("Could not determine FHIR mapping for xsd type %s.", schemaTypeName.toString()));
            }
        }


        XmlSchemaType schemaType = schema.getTypeByName(schemaTypeName);
        if (schemaType == null) {
            // TODO: Mapping to existing definitions? (FHIR base types...)
            throw new IllegalArgumentException(String.format("Could not resolve type name %s.", schemaTypeName.toString()));
        }

        return resolveDefinition(schemaType);
    }

    private StructureDefinition resolveDefinition(XmlSchemaType schemaType) {
        if (schemaType instanceof XmlSchemaSimpleType) {
            return resolveSimpleType((XmlSchemaSimpleType)schemaType);
        }

        if (schemaType instanceof XmlSchemaComplexType) {
            return resolveComplexType((XmlSchemaComplexType)schemaType);
        }

        return null;
    }

    private StructureDefinition createStructureDefinition(XmlSchemaType schemaType) {
        String qualifiedTypeName = getQualifiedTypeName(schemaType.getQName(), namespaces);
        StructureDefinition definition = new StructureDefinition();
        definition.setId(unqualify(qualifiedTypeName));
        definition.setUrl(schemaType.getQName().toString());
        definition.setName(unqualify(qualifiedTypeName));
        definition.setDisplay(qualifiedTypeName);
        definition.setStatus(ConformanceResourceStatusEnum.DRAFT);
        definition.setPublisher("TODO: Publisher");
        definition.addContact().setName("TODO: Contact").addTelecom().setSystem(ContactPointSystemEnum.URL).setValue("http://hl7.org/fhir");
        definition.setDate(DateTimeDt.withCurrentTime());
        definition.setAbstract(false);

        // Get the documentation annotation
        definition.setDescription(getDocumentation(schemaType.getAnnotation()));

        // TODO: Set FhirVersion?
        //definition.setFhirVersion();

        // TODO: Mappings?

        return definition;
    }

    private StructureDefinition resolveSimpleType(XmlSchemaSimpleType schemaSimpleType) {
        if (schemaSimpleType.isAnonymous()) {
            return null;
        }

        String qualifiedTypeName = getQualifiedTypeName(schemaSimpleType.getQName(), namespaces);
        String unqualifiedTypeName = unqualify(qualifiedTypeName);
        StructureDefinition definition = definitions.get(qualifiedTypeName);
        if (definition == null) {

            // create the basic definition
            definition = createStructureDefinition(schemaSimpleType);
            definitions.put(qualifiedTypeName, definition);

            // resolve the base
            if (schemaSimpleType.getContent() instanceof XmlSchemaSimpleTypeRestriction) {
                StructureDefinition baseDefinition = resolveDefinition(((XmlSchemaSimpleTypeRestriction)schemaSimpleType.getContent()).getBaseTypeName());
                if (baseDefinition != null) {
                    definition.setBase(baseDefinition.getUrl());
                }
            }

            // set the kind to logical, DataTypes cannot be defined in a logical model
            definition.setKind(StructureDefinitionKindEnum.LOGICAL_MODEL);

            // add elements?
            StructureDefinition.Snapshot snapshot = new StructureDefinition.Snapshot();
            definition.setSnapshot(snapshot);
            ElementDefinitionDt rootElement = snapshot.addElement();
            String rootPath = unqualifiedTypeName;
            rootElement.setPath(rootPath);
            rootElement.setShort(unqualifiedTypeName);
            rootElement.setMin(0);
            rootElement.setMax("*");

            // TODO: Enumerations...
            // TODO: Mapping to base FHIR types...
        }

        return definition;
    }

    private StructureDefinition resolveComplexType(XmlSchemaComplexType schemaComplexType) {
        if (schemaComplexType.isAnonymous()) {
            return null;
        }

        String qualifiedTypeName = getQualifiedTypeName(schemaComplexType.getQName(), namespaces);
        String unqualifiedTypeName = unqualify(qualifiedTypeName);
        StructureDefinition definition = definitions.get(qualifiedTypeName);
        if (definition == null) {
            definition = createStructureDefinition(schemaComplexType);
            definitions.put(qualifiedTypeName, definition);

            // resolve the base
            if (schemaComplexType.getBaseSchemaTypeName() != null) {
                StructureDefinition baseDefinition = resolveDefinition(schemaComplexType.getBaseSchemaTypeName());
                if (baseDefinition != null) {
                    definition.setBase(baseDefinition.getUrl());
                }
            }

            // set the kind
            definition.setKind(StructureDefinitionKindEnum.LOGICAL_MODEL);

            // add root element
            StructureDefinition.Snapshot snapshot = new StructureDefinition.Snapshot();
            definition.setSnapshot(snapshot);
            ElementDefinitionDt rootElement = snapshot.addElement();
            String rootPath = unqualifiedTypeName;
            rootElement.setPath(rootPath);
            rootElement.setShort(unqualifiedTypeName);
            rootElement.setMin(0);
            rootElement.setMax("*");

            // add elements
            List<XmlSchemaAttributeOrGroupRef> attributeContent;
            XmlSchemaParticle particleContent;

            if (schemaComplexType.getContentModel() != null) {
                XmlSchemaContent content = schemaComplexType.getContentModel().getContent();
                if (content instanceof XmlSchemaComplexContentRestriction) {
                    XmlSchemaComplexContentRestriction restrictionContent = (XmlSchemaComplexContentRestriction)content;
                    attributeContent = restrictionContent.getAttributes();
                    particleContent = restrictionContent.getParticle();
                }
                else if (content instanceof XmlSchemaComplexContentExtension) {
                    XmlSchemaComplexContentExtension extensionContent = (XmlSchemaComplexContentExtension)content;
                    attributeContent = extensionContent.getAttributes();
                    particleContent = extensionContent.getParticle();
                }
                // For complex types with simple content, create a new class type with a value element for the content
                else if (content instanceof XmlSchemaSimpleContentRestriction) {
                    XmlSchemaSimpleContentRestriction restrictionContent = (XmlSchemaSimpleContentRestriction)content;

                    StructureDefinition valueDefinition = resolveDefinition(restrictionContent.getBaseTypeName());
                    // TODO: add element with value of type valueDefinition
                    //ClassTypeElement valueElement = new ClassTypeElement("value", valueType, false);
                    //elements.add(valueElement);

                    attributeContent = restrictionContent.getAttributes();
                    particleContent = null;
                }
                else if (content instanceof XmlSchemaSimpleContentExtension) {
                    XmlSchemaSimpleContentExtension extensionContent = (XmlSchemaSimpleContentExtension)content;
                    attributeContent = extensionContent.getAttributes();
                    particleContent = null;

                    StructureDefinition valueDefinition = resolveDefinition(extensionContent.getBaseTypeName());
                    // TODO: add element with value of type valueDefinition
                    //ClassTypeElement valueElement = new ClassTypeElement("value", valueType, false);
                    //elements.add(valueElement);
                }
                else {
                    throw new IllegalArgumentException("Unrecognized Schema Content: " + content.toString());
                }
            }
            else {
                attributeContent = schemaComplexType.getAttributes();
                particleContent = schemaComplexType.getParticle();
            }

            for (XmlSchemaAttributeOrGroupRef attribute : attributeContent) {
                resolveDefinitionElements(attribute, definition, rootPath, snapshot);
            }

            if (particleContent != null) {
                resolveDefinitionElements(particleContent, definition, rootPath, snapshot);
            }
        }

        return definition;
    }

    private void resolveDefinitionElements(XmlSchemaParticle particle, StructureDefinition definition, String rootPath, StructureDefinition.Snapshot snapshot) {
        if (particle instanceof XmlSchemaElement) {
            ElementDefinitionDt element = resolveDefinitionElement((XmlSchemaElement)particle, rootPath);
            if (element != null) {
                snapshot.addElement(element);
            }
        }
        else if (particle instanceof XmlSchemaSequence) {
            XmlSchemaSequence sequence = (XmlSchemaSequence)particle;
            for (XmlSchemaSequenceMember member : sequence.getItems()) {
                if (member instanceof XmlSchemaParticle) {
                    resolveDefinitionElements((XmlSchemaParticle)member, definition, rootPath, snapshot);
                }
            }
        }
        else if (particle instanceof XmlSchemaAll) {
            XmlSchemaAll all = (XmlSchemaAll)particle;
            for (XmlSchemaAllMember member : all.getItems()) {
                if (member instanceof XmlSchemaParticle) {
                    resolveDefinitionElements((XmlSchemaParticle)member, definition, rootPath, snapshot);
                }
            }
        }
        else if (particle instanceof XmlSchemaChoice) {
            XmlSchemaChoice choice = (XmlSchemaChoice)particle;
            for (XmlSchemaChoiceMember member : choice.getItems()) {
                if (member instanceof XmlSchemaElement) {
                    ElementDefinitionDt element = resolveDefinitionElement((XmlSchemaElement)member, rootPath);
                    if (element != null) {
                        snapshot.addElement(element);
                    }
                }
            }
        }
        else if (particle instanceof XmlSchemaGroupRef) {
            XmlSchemaGroupRef ref = (XmlSchemaGroupRef)particle;
            resolveDefinitionElements(ref.getParticle(), definition, rootPath, snapshot);
        }
    }

    private ElementDefinitionDt resolveDefinitionElement(XmlSchemaElement element, String rootPath) {
        if (element.isRef()) {
            element = element.getRef().getTarget();
        }

        StructureDefinition elementTypeDefinition = null;
        XmlSchemaType schemaType = element.getSchemaType();
        if (schemaType != null) {
            elementTypeDefinition = resolveDefinition(schemaType);
        }
        else {
            QName schemaTypeName = element.getSchemaTypeName();
            if (schemaTypeName != null) {
                elementTypeDefinition = resolveDefinition(schemaTypeName);
            }
        }

        if (elementTypeDefinition == null) {
            // TODO: This should potentially import anonymous types as element structures, not sure we have that use case right now though....
            return null;
        }

        ElementDefinitionDt elementDefinition = new ElementDefinitionDt();

        elementDefinition.setPath(String.format("%s.%s", rootPath, element.getName()));
        // TODO: elementDefinition.setShort();
        // TODO: elementDefinition.addAlias();
        elementDefinition.setName(element.getName());
        MarkdownDt md = new MarkdownDt();
        md.setValue(getDocumentation(element.getAnnotation()));
        elementDefinition.setDefinition(md);
        elementDefinition.setMin((int) element.getMinOccurs());
        if (element.getMaxOccurs() == Long.MAX_VALUE) {
            elementDefinition.setMax("*");
        }
        else {
            elementDefinition.setMax(String.valueOf(element.getMaxOccurs()));
        }

        // TODO: References
        elementDefinition.addType().setCode(elementTypeDefinition.getName());

        return elementDefinition;
    }

    private ElementDefinitionDt resolveDefinitionElement(XmlSchemaAttribute attribute, String rootPath) {
        if (attribute.isRef()) {
            attribute = attribute.getRef().getTarget();
        }

        StructureDefinition elementTypeDefinition = null;
        XmlSchemaType schemaType = attribute.getSchemaType();
        if (schemaType != null) {
            elementTypeDefinition = resolveDefinition(schemaType);
        }
        else {
            QName schemaTypeName = attribute.getSchemaTypeName();
            if (schemaTypeName != null) {
                elementTypeDefinition = resolveDefinition(schemaTypeName);
            }
        }

        if (elementTypeDefinition == null) {
            // TODO: This should potentially import anonymous types as element structures...
            return null;
        }

        ElementDefinitionDt elementDefinition = new ElementDefinitionDt();

        elementDefinition.setPath(String.format("%s.%s", rootPath, attribute.getName()));
        // TODO: elementDefinition.setShort();
        // TODO: elementDefinition.addAlias();
        // TODO: elementDefinition.setDefinition();
        elementDefinition.setName(attribute.getName());
        switch (attribute.getUse()) {
            case NONE:
                break;
            case OPTIONAL:
                elementDefinition.setMin(0);
                elementDefinition.setMax("1");
                break;
            case PROHIBITED:
                elementDefinition.setMin(0);
                elementDefinition.setMax("0");
                break;
            case REQUIRED:
                elementDefinition.setMin(1);
                elementDefinition.setMax("1");
                break;
        }

        // TODO: References
        elementDefinition.addType().setCode(elementTypeDefinition.getName());

        return elementDefinition;
    }

    private void resolveDefinitionElements(XmlSchemaAttributeOrGroupRef attribute, StructureDefinition definition, String rootPath, StructureDefinition.Snapshot snapshot) {
        if (attribute instanceof XmlSchemaAttribute) {
            ElementDefinitionDt element = resolveDefinitionElement((XmlSchemaAttribute)attribute, rootPath);
            if (element != null) {
                snapshot.addElement(element);
            }
        }
        else if (attribute instanceof XmlSchemaAttributeGroupRef) {
            resolveDefinitionElements(((XmlSchemaAttributeGroupRef)attribute).getRef().getTarget(), definition, rootPath, snapshot);
        }
    }

    private void resolveDefinitionElements(XmlSchemaAttributeGroup attributeGroup, StructureDefinition definition, String rootPath, StructureDefinition.Snapshot snapshot) {
        for (XmlSchemaAttributeGroupMember member : attributeGroup.getAttributes()) {
            if (member instanceof XmlSchemaAttribute) {
                ElementDefinitionDt element = resolveDefinitionElement((XmlSchemaAttribute)member, rootPath);
                if (element != null) {
                    snapshot.addElement(element);
                }
            }
            else if (member instanceof XmlSchemaAttributeGroupRef) {
                resolveDefinitionElements(((XmlSchemaAttributeGroupRef)member).getRef().getTarget(), definition, rootPath, snapshot);
            }
            else if (member instanceof XmlSchemaAttributeGroup) {
                resolveDefinitionElements((XmlSchemaAttributeGroup)member, definition, rootPath, snapshot);
            }
        }
    }
}
