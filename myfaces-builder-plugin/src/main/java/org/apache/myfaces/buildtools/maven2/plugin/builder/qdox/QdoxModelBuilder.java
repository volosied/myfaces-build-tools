package org.apache.myfaces.buildtools.maven2.plugin.builder.qdox;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.myfaces.buildtools.maven2.plugin.builder.ModelBuilder;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.ClassMeta;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.ComponentMeta;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.ConverterMeta;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.MethodSignatureMeta;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.Model;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.PropertyMeta;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.ValidatorMeta;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.AbstractJavaEntity;
import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.DocletTag;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.Type;

/**
 * An implementation of the ModelBuilder interface that uses the Qdox java
 * source-parsing library to scan a list of specified source directories for
 * java files.
 * <p>
 * The java source files found can use either java15 annotations or doclet
 * annotations to indicate what data should be added to the Model.
 */
public class QdoxModelBuilder implements ModelBuilder
{
    private final Log log = LogFactory.getLog(QdoxModelBuilder.class);

    private static final String DOC_CONVERTER = "JSFConverter";
    private static final String DOC_VALIDATOR = "JSFValidator";
    private static final String DOC_COMPONENT = "JSFComponent";
    private static final String DOC_RENDERER = "JSFRenderer";
    private static final String DOC_RENDERKIT = "JSFRenderkit";

    private static final String DOC_PROPERTY = "JSFProperty";   
    
    //This property is used in special cases where properties 
    //does not have methods defined on component class, like binding
    //in jsf 1.1 (in 1.2 has component counterpart). In fact, all
    //properties must be defined with JSFProperty
    private static final String DOC_JSP_PROPERTY = "JSFJspProperty";

    private static final String ANNOTATION_BASE = "org.apache.myfaces.buildtools.annotation";

    private static class JavaClassComparator implements Comparator
    {
        public int compare(Object arg0, Object arg1)
        {
            JavaClass c0 = (JavaClass) arg0;
            JavaClass c1 = (JavaClass) arg1;

            return (c0.getName().compareTo(c1.getName()));
        }
    }

    /**
     * Scan the source tree for doc-annotations, and build Model objects
     * containing info extracted from the doc-annotation attributes and
     * introspected info about the item the annotation is attached to.
     */
    public void buildModel(Model model, MavenProject project)
            throws MojoExecutionException
    {
        buildModel(model, project.getCompileSourceRoots());
    }

    /**
     * Scan the source tree for doc-annotations, and build Model objects
     * containing info extracted from the doc-annotation attributes and
     * introspected info about the item the annotation is attached to.
     */
    public void buildModel(Model model, List sourceDirs)
            throws MojoExecutionException
    {
        JavaDocBuilder builder = new JavaDocBuilder();

        // need a File object representing the original source tree
        for (Iterator i = sourceDirs.iterator(); i.hasNext();)
        {
            String srcDir = (String) i.next();
            builder.addSourceTree(new File(srcDir));
        }

        JavaClass[] classes = builder.getClasses();

        // Sort the class array so that they are processed in a
        // predictable order, regardless of how the source scanning
        // returned them.
        Arrays.sort(classes, new JavaClassComparator());
        Set processedClasses = new HashSet();
        for (int i = 0; i < classes.length; ++i)
        {
            JavaClass clazz = classes[i];
            processClass(processedClasses, clazz, model);
        }
        
        for (Iterator it = model.getComponents().iterator(); it.hasNext();)
        {
            ComponentMeta component = (ComponentMeta) it.next();
            component.setModelId(model.getModelId());
        }
        for (Iterator it = model.getConverters().iterator(); it.hasNext();)
        {
            ConverterMeta converter = (ConverterMeta) it.next();
            converter.setModelId(model.getModelId());
        }
        for (Iterator it = model.getValidators().iterator(); it.hasNext();)
        {
            ValidatorMeta validator = (ValidatorMeta) it.next();
            validator.setModelId(model.getModelId());
        }
    }

    /**
     * Set the parentClassName and interfaceClassNames properties of the
     * provided modelItem object.
     */
    private void processClass(Set processedClasses, JavaClass clazz, Model model)
            throws MojoExecutionException
    {
        if (processedClasses.contains(clazz))
        {
            return;
        }

        // first, check that the parent type and all interfaces have been
        // processed.
        JavaClass parentClazz = clazz.getSuperJavaClass();
        if (parentClazz != null)
        {
            processClass(processedClasses, parentClazz, model);
        }

        JavaClass[] classes = clazz.getImplementedInterfaces();
        for (int i = 0; i < classes.length; ++i)
        {
            JavaClass iclazz = classes[i];
            processClass(processedClasses, iclazz, model);
        }

        // ok, now we can mark this class as processed.
        processedClasses.add(clazz);

        log.info("processed class:" + clazz.getName());

        DocletTag tag;
        Annotation anno;

        // converters
        tag = clazz.getTagByName(DOC_CONVERTER, false);
        if (tag != null)
        {
            Map props = tag.getNamedParameterMap();
            processConverter(props, tag.getContext(), clazz, model);
        }
        anno = getAnnotation(clazz, DOC_CONVERTER);
        if (anno != null)
        {
            Map props = anno.getNamedParameterMap();
            processConverter(props, anno.getContext(), clazz, model);
        }

        // validators
        tag = clazz.getTagByName(DOC_VALIDATOR, false);
        if (tag != null)
        {
            Map props = tag.getNamedParameterMap();
            processValidator(props, tag.getContext(), clazz, model);
        }
        anno = getAnnotation(clazz, DOC_VALIDATOR);
        if (anno != null)
        {
            Map props = anno.getNamedParameterMap();
            processValidator(props, anno.getContext(), clazz, model);
        }

        // components
        tag = clazz.getTagByName(DOC_COMPONENT, false);
        if (tag != null)
        {
            Map props = tag.getNamedParameterMap();
            processComponent(props, tag.getContext(), clazz, model);
        }
        anno = getAnnotation(clazz, DOC_COMPONENT);
        if (anno != null)
        {
            Map props = anno.getNamedParameterMap();
            processComponent(props, anno.getContext(), clazz, model);
        }

    }

    private Annotation getAnnotation(AbstractJavaEntity entity, String annoName)
    {
        Annotation[] annos = entity.getAnnotations();
        if (annos == null)
        {
            return null;
        }
        // String wanted = ANNOTATION_BASE + "." + annoName;
        for (int i = 0; i < annos.length; ++i)
        {
            Annotation thisAnno = annos[i];
            // Ideally, here we would check whether the fully-qualified name of
            // the annotation
            // class matches ANNOTATION_BASE + "." + annoName. However it
            // appears that qdox 1.6.3
            // does not correctly expand @Foo using the class import statements;
            // method
            // Annotation.getType.getJavaClass.getFullyQualifiedName still just
            // returns the short
            // class name. So for now, just check for the short name.
            String thisAnnoName = thisAnno.getType().getJavaClass().getName();
            if (thisAnnoName.equals(annoName))
            {
                return thisAnno;
            }
        }
        return null;
    }

    /**
     * Remove all leading whitespace and a quotemark if it exists.
     * <p>
     * Qdox comments like <code>foo val= "bar"</code> return a value with
     * leading whitespace and quotes, so remove them.
     */
    private String clean(Object srcObj)
    {
        if (srcObj == null)
        {
            return null;
        }

        String src = srcObj.toString();
        int start = 0;
        int end = src.length();

        while (start <= end)
        {
            char c = src.charAt(start);
            if (!Character.isWhitespace(c) && (c != '"'))
            {
                break;
            }
            ++start;
        }
        while (end >= start)
        {
            char c = src.charAt(end - 1);
            if (!Character.isWhitespace(c) && (c != '"'))
            {
                break;
            }
            --end;
        }
        return src.substring(start, end);
    }

    /**
     * Get the named attribute from a doc-annotation.
     * 
     * Param clazz is the class the annotation is attached to; only used when
     * reporting errors.
     */
    private String getString(JavaClass clazz, String key, Map map, String dflt)
    {
        String val = clean(map.get(key));
        if (val != null)
        {
            return val;
        }
        else
        {
            return dflt;
        }
    }

    /**
     * Get the named attribute from a doc-annotation and convert to a boolean.
     * 
     * Param clazz is the class the annotation is attached to; only used when
     * reporting errors.
     */
    private Boolean getBoolean(JavaClass clazz, String key, Map map,
            Boolean dflt)
    {
        String val = clean(map.get(key));
        if (val == null)
        {
            return dflt;
        }
        // TODO: report problem if the value does not look like "true" or
        // "false",
        // rather than silently converting it to false.
        return Boolean.valueOf(val);
    }

    /**
     * Set the parentClassName and interfaceClassNames properties of the
     * provided modelItem object.
     */
    private void initAncestry(Model model, JavaClass clazz, ClassMeta modelItem)
    {
        // self
        modelItem.setClassName(clazz.getName());

        // parent
        JavaClass parentClazz = clazz.getSuperJavaClass();
        while (parentClazz != null)
        {
            ComponentMeta parentComponent = model
                    .findComponentByClassName(parentClazz.getName());
            if (parentComponent != null)
            {
                modelItem.setParentClassName(parentComponent.getClassName());
                break;
            }
            parentClazz = parentClazz.getSuperJavaClass();
        }

        // interfaces
        JavaClass[] classes = clazz.getImplementedInterfaces();
        List ifaceNames = new ArrayList();
        for (int i = 0; i < classes.length; ++i)
        {
            JavaClass iclazz = classes[i];

            ComponentMeta ifaceComponent = model
                    .findComponentByClassName(iclazz.getName());
            if (ifaceComponent != null)
            {
                ifaceNames.add(ifaceComponent.getClassName());
            }
        }
        modelItem.setInterfaceClassNames(ifaceNames);
    }

    private void processConverter(Map props, AbstractJavaEntity ctx,
            JavaClass clazz, Model model)
    {
        String longDescription = clazz.getComment();
        String descDflt = getFirstSentence(longDescription);
        if ((descDflt == null) || (descDflt.length() < 2))
        {
            descDflt = "no description";
        }
        String shortDescription = getString(clazz, "desc", props, descDflt);

        String converterIdDflt = null;
        JavaField fieldConverterId = clazz
                .getFieldByName("CONVERTER_ID");
        if (fieldConverterId != null)
        {
            String value = fieldConverterId.getInitializationExpression();
            converterIdDflt = clean(value.substring(value.indexOf('"')));
        }        
        String converterId = getString(clazz, "id", props, converterIdDflt);
        String converterClass = getString(clazz, "class", props, clazz
                .getFullyQualifiedName());

        ConverterMeta converter = new ConverterMeta();
        converter.setClassName(converterClass);
        converter.setConverterId(converterId);
        converter.setDescription(shortDescription);
        converter.setLongDescription(longDescription);
        model.addConverter(converter);
    }

    private void processValidator(Map props, AbstractJavaEntity ctx,
            JavaClass clazz, Model model)
    {
        String longDescription = clazz.getComment();
        String descDflt = getFirstSentence(longDescription);
        if ((descDflt == null) || (descDflt.length() < 2))
        {
            descDflt = "no description";
        }
        String shortDescription = getString(clazz, "desc", props, descDflt);

        String validatorIdDflt = null;
        JavaField fieldConverterId = clazz
                .getFieldByName("VALIDATOR_ID");
        if (fieldConverterId != null)
        {
            String value = fieldConverterId.getInitializationExpression();
            validatorIdDflt = clean(value.substring(value.indexOf('"')));
        }        
        String validatorId = getString(clazz, "id", props, validatorIdDflt);
        String validatorClass = getString(clazz, "class", props, clazz
                .getFullyQualifiedName());        

        ValidatorMeta validator = new ValidatorMeta();
        validator.setClassName(validatorClass);
        validator.setValidatorId(validatorId);
        validator.setDescription(shortDescription);
        validator.setLongDescription(longDescription);
        model.addValidator(validator);
    }

    private void processComponent(Map props, AbstractJavaEntity ctx,
            JavaClass clazz, Model model) throws MojoExecutionException
    {
        String componentTypeDflt = null;
        JavaField fieldComponentType = clazz.getFieldByName("COMPONENT_TYPE");
        if (fieldComponentType != null)
        {
            componentTypeDflt = clean(fieldComponentType
                    .getInitializationExpression());
        }

        String componentTypeFamily = null;
        JavaField fieldComponentFamily = clazz
                .getFieldByName("COMPONENT_FAMILY");
        if (fieldComponentFamily != null)
        {
            componentTypeFamily = clean(fieldComponentFamily
                    .getInitializationExpression());
        }

        String rendererTypeDflt = null;
        JavaField fieldRendererType = clazz
                .getFieldByName("DEFAULT_RENDERER_TYPE");
        if (fieldRendererType != null)
        {
            rendererTypeDflt = clean(fieldRendererType
                    .getInitializationExpression());
        }

        String componentName = getString(clazz, "name", props, clazz.getName());
        String componentClass = getString(clazz, "class", props, clazz
                .getFullyQualifiedName());
        
        String componentParentClass = getString(clazz, "parent", props, 
                clazz.getSuperJavaClass()!= null?
                        clazz.getSuperJavaClass().getFullyQualifiedName():null);
        
        if (componentParentClass != null && componentParentClass.startsWith("java.lang"))
        {
            componentParentClass = null;
        }
        
        if (componentParentClass != null)
        {
            if (componentParentClass.equals(""))
            {
                componentParentClass = null;
            }
        }
                
        String longDescription = clazz.getComment();
        String descDflt = getFirstSentence(longDescription);
        if ((descDflt == null) || (descDflt.length() < 2))
        {
            descDflt = "no description";
        }
        String shortDescription = getString(clazz, "desc", props, descDflt);

        String componentFamily = getString(clazz, "family", props,
                componentTypeFamily);
        String componentType = getString(clazz, "type", props,
                componentTypeDflt);
        String rendererType = getString(clazz, "defaultRendererType", props,
                rendererTypeDflt);
        Boolean canHaveChildren = getBoolean(clazz, "canHaveChildren", props, null);

        String tagClass = getString(clazz, "tagClass", props, null);
        String tagSuperclass = getString(clazz, "tagSuperclass", props, null);
        String tagHandler = getString(clazz, "tagHandler", props, null);

        ComponentMeta component = new ComponentMeta();
        initAncestry(model, clazz, component);
        component.setName(componentName);
        component.setClassName(componentClass);
        component.setParentClassName(componentParentClass);
        component.setDescription(shortDescription);
        component.setLongDescription(longDescription);
        component.setType(componentType);
        component.setFamily(componentFamily);
        component.setRendererType(rendererType);
        component.setChildren(canHaveChildren);

        JavaClass[] interfaces = clazz.getImplementedInterfaces();
        for (int i = 0; i < interfaces.length; ++i)
        {
            JavaClass iface = interfaces[i];
            if (iface.getName().equals("javax.faces.component.NamingContainer"))
            {
                component.setNamingContainer(Boolean.TRUE);
                break;
            }
        }

        component.setTagClass(tagClass);
        component.setTagSuperclass(tagSuperclass);
        component.setTagHandler(tagHandler);

        // Now here walk the component looking for property annotations.
        processComponentProperties(clazz, component);

        validateComponent(component);
        model.addComponent(component);
    }

    /**
     * Look for any methods on the specified class that are annotated as being
     * component properties, and add metadata about them to the model.
     */
    private void processComponentProperties(JavaClass clazz,
            ComponentMeta component)
    {
        JavaMethod[] methods = clazz.getMethods();
        for (int i = 0; i < methods.length; ++i)
        {
            JavaMethod method = methods[i];

            DocletTag tag = method.getTagByName(DOC_PROPERTY);
            if (tag != null)
            {
                Map props = tag.getNamedParameterMap();
                processComponentProperty(props, tag.getContext(), clazz,
                        method, component);
            }

            Annotation anno = getAnnotation(method, DOC_PROPERTY);
            if (anno != null)
            {
                Map props = anno.getNamedParameterMap();
                processComponentProperty(props, anno.getContext(), clazz,
                        method, component);
            }
        }
        
        DocletTag[] jspProperties = clazz.getTagsByName(DOC_JSP_PROPERTY);
        for (int i = 0; i < jspProperties.length; ++i)
        {
            //We have here only doclets, because this part is only for
            //solve problems with binding property on 1.1
            DocletTag tag = jspProperties[i];
            
            Map props = tag.getNamedParameterMap();
            processComponentJspProperty(props, tag.getContext(), clazz,
                    component);
            
        }        
    }

    private void processComponentProperty(Map props, AbstractJavaEntity ctx,
            JavaClass clazz, JavaMethod method, ComponentMeta component)
    {
        Boolean required = getBoolean(clazz, "required", props, null);
        Boolean transientProp = getBoolean(clazz, "transient", props, null);
        Boolean stateHolder = getBoolean(clazz, "stateHolder", props, null);
        Boolean literalOnly = getBoolean(clazz, "literalOnly", props, null);
        Boolean tagExcluded = getBoolean(clazz, "tagExcluded", props, null);

        String longDescription = ctx.getComment();
        String descDflt = getFirstSentence(longDescription);
        if ((descDflt == null) || (descDflt.length() < 2))
        {
            descDflt = "no description";
        }
        String shortDescription = getString(clazz, "desc", props, descDflt);
        String returnSignature = getString(clazz, "returnSignature", props, null);
        String methodSignature = getString(clazz, "methodSignature", props, null);

        Type returnType = method.getReturns();
        
        PropertyMeta p = new PropertyMeta();
        p.setName(methodToPropName(method.getName()));
        p.setClassName(returnType.toString());
        p.setRequired(required);
        p.setTransient(transientProp);
        p.setStateHolder(stateHolder);
        p.setLiteralOnly(literalOnly);
        p.setTagExcluded(tagExcluded);
        p.setDescription(shortDescription);
        p.setLongDescription(longDescription);
        
        if (returnSignature != null)
        {
            MethodSignatureMeta signature = new MethodSignatureMeta();
            signature.setReturnType(returnSignature);
            
            if (methodSignature != null)
            {
                String[] params = StringUtils.split(methodSignature,',');
                
                if (params != null)
                {
                    for (int i = 0; i < params.length; i++)
                    {
                        signature.addParameterType(params[i].trim());
                    }
                }
            }
            p.setMethodBindingSignature(signature);
        }
        
        //If the method is abstract this should be generated
        if (method.isAbstract()){
            p.setGenerated(Boolean.TRUE);
        }

        component.addProperty(p);
    }
    
    private void processComponentJspProperty(Map props, AbstractJavaEntity ctx,
            JavaClass clazz, ComponentMeta component)
    {
        Boolean required = getBoolean(clazz, "required", props, null);
        Boolean transientProp = getBoolean(clazz, "transient", props, null);
        Boolean stateHolder = getBoolean(clazz, "stateHolder", props, null);
        Boolean literalOnly = getBoolean(clazz, "literalOnly", props, null);
        Boolean tagExcluded = getBoolean(clazz, "tagExcluded", props, null);

        String longDescription = getString(clazz, "longDesc", props, null);
        
        String descDflt = longDescription;
        if ((descDflt == null) || (descDflt.length() < 2))
        {
            descDflt = "no description";
        }
        String shortDescription = getString(clazz, "desc", props, descDflt);
        String returnType = getString(clazz, "returnType", props, null);
        String name = getString(clazz, "name", props, null);
        
        PropertyMeta p = new PropertyMeta();
        p.setName(name);
        p.setClassName(returnType);
        p.setRequired(required);
        p.setTransient(transientProp);
        p.setStateHolder(stateHolder);
        p.setLiteralOnly(literalOnly);
        p.setTagExcluded(tagExcluded);
        p.setDescription(shortDescription);
        p.setLongDescription(longDescription);
        
        p.setGenerated(Boolean.FALSE);

        component.addProperty(p);
    }
    

    /**
     * Convert a method name to a property name.
     */
    static String methodToPropName(String methodName)
    {
        StringBuffer name = new StringBuffer();
        if (methodName.startsWith("get") || methodName.startsWith("set"))
        {
            name.append(methodName.substring(3));
        }
        else if (methodName.startsWith("is"))
        {
            name.append(methodName.substring(2));
        }
        else
        {
            throw new IllegalArgumentException("Invalid annotated method name "
                    + methodName);
        }

        // Handle following styles of property name
        // getfooBar --> fooBar
        // getFooBar --> fooBar
        // getURL --> url
        // getURLLocation --> urlLocation
        for (int i = 0; i < name.length(); ++i)
        {
            char c = name.charAt(i);
            if (Character.isUpperCase(c))
            {
                name.setCharAt(i, Character.toLowerCase(c));
            }
            else
            {
                if (i > 1)
                {
                    // reset the previous char to uppercase
                    c = name.charAt(i - 1);
                    name.setCharAt(i - 1, Character.toUpperCase(c));
                }
                break;
            }
        }
        return name.toString();
    }

    /**
     * Given the full javadoc for a component, extract just the "first
     * sentence".
     * <p>
     * Initially, just find the first dot, and strip out any linefeeds. Later,
     * try to handle "e.g." and similar (see javadoc algorithm for sentence
     * detection).
     */
    private String getFirstSentence(String doc)
    {
        if (doc == null)
        {
            return null;
        }

        int index = doc.indexOf('.');
        if (index == -1)
        {
            return doc;
        }
        // abc.
        return doc.substring(0, index);
    }

    private void validateComponent(ComponentMeta component)
            throws MojoExecutionException
    {
        // when name is set, this is a real component, so must have
        // desc, type, family, rendererType,
        // tagClass, tagSuperclass

        if (component.getName() == null)
        {
            return;
        }

        List badprops = new ArrayList();
        if (component.getDescription() == null)
        {
            badprops.add("description");
        }
        if (component.getType() == null)
        {
            badprops.add("type");
        }
        if (component.getFamily() == null)
        {
            badprops.add("family");
        }
        
        //Renderer is optional
        //if (component.getRendererType() == null)
        //{
        //    badprops.add("rendererType");
        //}

        if (badprops.size() > 0)
        {
            StringBuffer buf = new StringBuffer();
            for (Iterator i = badprops.iterator(); i.hasNext();)
            {
                if (buf.length() > 0)
                {
                    buf.append(",");
                }
                buf.append((String) i.next());
            }
            throw new MojoExecutionException(
                    "Missing properties on component class "
                            + component.getClassName() + ":" + buf.toString());

        }
    }
}