package com.redshape.generators.jpa;

import com.redshape.generators.jpa.utils.StringUtils;
import com.sun.codemodel.*;
import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.AbstractBaseJavaEntity;
import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public abstract class AbstractGeneratorMojo extends AbstractMojo {

    public static final String ENTITY_ANNOTATION_CLASS_NAME
            = "javax.persistence.Entity";
    public static final String ID_ANNOTATION_CLASS_NAME
            = "javax.persistence.Id";
    public static final String SERVICE_ANNOTATION_CLASS_NAME
            = "org.springframework.stereotype.Service";
    private static final String WELCOM_MESSAGE
            = "----------------------------   Generator: %s  --------------------------------------------";

    private static final String ONE_TO_MANY_ANNOTATION_CLASS_NAME = "javax.persistence.OneToMany";
    private static final String ONE_TO_ONE_ANNOTATION_CLASS_NAME = "javax.persistence.OneToOne";
    private static final String MANY_TO_ONE_ANNOTATION_CLASS_NAME = "javax.persistence.ManyToOne";
    private static final String MANY_TO_MANY_ANNOTATION_CLASS_NAME = "javax.persistence.ManyToMany";

    protected final JCodeModel codeModel;
    protected JavaDocBuilder classMetaBuilder;

    @Parameter( property = "outputPath", required = true, defaultValue = "target/")
    private String outputPath = "target/";

    @Parameter( property = "entityPattern", required = true )
    private String entityPattern = "";

    @Parameter( property = "sourceRoot", defaultValue = "src/main/java" )
    private String sourceRoot = "src/main/java";

    private final AtomicBoolean classMetaBuilderCreated = new AtomicBoolean();

    private final String generatorName;

    protected AbstractGeneratorMojo( String generatorName ) {
        super();

        this.generatorName = generatorName;
        this.codeModel = new JCodeModel();
    }

    protected JavaDocBuilder getClassMetaBuilder() {
        if ( classMetaBuilderCreated.compareAndSet(false, true) ) {
            try {
                this.classMetaBuilder = createJavaDocBuilder();
            } catch (MojoExecutionException e) {
                throw new IllegalStateException( e.getMessage(), e );
            }
        }

        return this.classMetaBuilder;
    }

    protected String[] findClasses( String sourceRoot, String classPattern ) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( sourceRoot );
        scanner.setIncludes( new String[] { classPattern } );

        scanner.scan();

        String[] sources = scanner.getIncludedFiles();
        if ( sources.length == 0 )
        {
            getLog().info("No source entities is suitable to be processed");
            return new String[0];
        }

        return sources;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info( String.format( WELCOM_MESSAGE, generatorName ) );
        getLog().info("Looking for classes matching '" + entityPattern + "' pattern in " + sourceRoot );
        String[] classes = findClasses( sourceRoot, entityPattern );

        int processed = 0;
        for ( String className : classes ) {
            JavaClass entityClass = getClassMetaBuilder().getClassByName( pathToName(className) );
            if ( isSupported(entityClass) ) {
                getLog().info("Processing class " + entityClass.getFullyQualifiedName() );
                generateClass(entityClass);
                processed += 1;
            }
        }

        getLog().info( processed + " classes has been processed...");

        getLog().info( "Flushing code model contents..." );
        writeClasses();
    }

    protected void writeClasses() throws MojoExecutionException {
        File outputDirectory = new File(outputPath);
        if ( !outputDirectory.exists() ) {
            throw new MojoExecutionException("Non-exists output path specified...");
        }

        if ( !outputDirectory.isDirectory() ) {
            throw new MojoExecutionException("Output path must be a directory type!");
        }

        try {
            codeModel.build( outputDirectory, (PrintStream) null );
        } catch ( IOException e ) {
            throw new MojoExecutionException("Failed to save code model contents...", e );
        }
    }

    private String pathToName( String path ) {
        return path.replaceAll(Pattern.quote(File.separator), ".").replace(".java", "").trim();
    }

    protected boolean hasDescendants( JavaClass clazz ) {
        boolean result = false;
        for ( JavaClass classItem : getClassMetaBuilder().getClasses() ) {
            if ( !classItem.isA( clazz )
                    || clazz.equals(classItem)
                    || classItem.getFullyQualifiedName().equals(Object.class.getCanonicalName()) ) {
                continue;
            }

            result = true;
            break;
        }

        return result;
    }

    protected boolean isJpaRelationType(String annotationTypeName) {
        return annotationTypeName.equals(MANY_TO_ONE_ANNOTATION_CLASS_NAME)
                || annotationTypeName.equals(MANY_TO_MANY_ANNOTATION_CLASS_NAME)
                || annotationTypeName.equals(ONE_TO_MANY_ANNOTATION_CLASS_NAME)
                || annotationTypeName.equals(ONE_TO_ONE_ANNOTATION_CLASS_NAME);
    }

    protected boolean isSimpleType( JavaClass type ) {
        try {
            JType.parse(codeModel, type.getFullyQualifiedName());
            return true;
        } catch ( IllegalArgumentException e ) {
            return type.getFullyQualifiedName().startsWith("java.lang");
        }
    }

    protected boolean isDtoType( JavaClass type ) {
        String typeName = type.getFullyQualifiedName();

        boolean result = typeName.endsWith("DTO");
        if ( result ) {
            return result;
        }

        int bIndex = typeName.lastIndexOf(".");
        if ( bIndex > 0 ) {
            result = typeName.substring(0, bIndex ).endsWith("dto");
        }

        return result;
    }

    protected boolean isJpaEntity(JavaClass clazz) {
        return hasAnnotation(clazz, ENTITY_ANNOTATION_CLASS_NAME );
    }

    protected boolean hasAnnotation(AbstractBaseJavaEntity clazz, String className ) {
        return hasAnnotation( clazz, className, false );
    }

    protected boolean hasAnnotation(AbstractBaseJavaEntity clazz, String className, boolean checkParent ) {
        boolean result = false;
        for ( Annotation annotation : clazz.getAnnotations() ) {
            if ( !annotation.getType().getFullyQualifiedName().equals(className) ) {
                continue;
            }

            result = true;
            break;
        }

        return result;
    }

    protected JType convertType( JavaClass originalType, String generatorSuffix ) {
        JType returnType = null;
        if ( isSimpleType( originalType ) || isDtoType(originalType) ) {
            returnType = codeModel.ref( originalType.getFullyQualifiedName() );
        } else if ( isJpaEntity( originalType ) ) {
            returnType = codeModel.ref(
                prepareClassName(originalType.getFullyQualifiedName(), generatorSuffix)
            );
        }

        return returnType;
    }

    protected String detectIdKeyType( JavaClass entityClass ) {
        String result = null;

        for ( JavaField field : entityClass.getFields() ) {
            boolean isId = false;
            for (Annotation annotation : field.getAnnotations()) {
                if ( !annotation.getType().getFullyQualifiedName().equals(ID_ANNOTATION_CLASS_NAME) ) {
                    continue;
                }

                isId = true;
                break;
            }

            if ( isId ) {
                result = field.getType().getFullyQualifiedName();
                break;
            }
        }

        if ( result == null && entityClass.getSuperClass() != null
                && !entityClass.getSuperJavaClass().getFullyQualifiedName().equals(
                Object.class.getCanonicalName()) ) {
            result = detectIdKeyType( entityClass.getSuperJavaClass() );
        }

        return result;
    }

    protected void generateAccessors(JDefinedClass clazz, JFieldVar clazzField) {
        generateSetter( clazz, clazzField);
        generateGetter( clazz, clazzField);
    }

    protected void generateSetter( JDefinedClass clazz, JFieldVar clazzField ) {
        JMethod setterMethod = clazz.method(JMod.PUBLIC, JType.parse(codeModel, "void"),
                "set" + StringUtils.ucfirst( clazzField.name() ) );
        JVar valueVar = setterMethod.param( clazzField.type(), "value" );
        setterMethod.body().assign( JExpr.refthis( clazzField.name() ), valueVar);
    }

    protected void generateGetter( JDefinedClass clazz, JFieldVar clazzField ) {
        JMethod getterMethod = clazz.method(JMod.PUBLIC, clazzField.type(),
                "get" + StringUtils.ucfirst(clazzField.name()));
        getterMethod.body()._return( JExpr.refthis( clazzField.name() ) );
    }

    protected JDefinedClass defineEnum( String className, String daoSuffix )
            throws JClassAlreadyExistsException {
        return _define(className, daoSuffix, true, ClassType.ENUM);
    }

    protected JDefinedClass defineAnnotation( String className, String daoSuffix )
            throws JClassAlreadyExistsException {
        return _define(className, daoSuffix, true, ClassType.ANNOTATION_TYPE_DECL);
    }

    protected JDefinedClass defineInterface( String className, String daoSuffix )
            throws JClassAlreadyExistsException {
        return _define(className, daoSuffix, true, ClassType.INTERFACE);
    }

    protected JDefinedClass defineClass( String className, String daoSuffix, boolean isAbstract )
        throws JClassAlreadyExistsException {
        return _define(className, daoSuffix, isAbstract, ClassType.CLASS);
    }

    private JDefinedClass _define( String className, String daoSuffix, boolean isAbstract, ClassType type)
            throws JClassAlreadyExistsException {
        String preparedClassName = prepareClassName( className, daoSuffix );
        int bIndex = preparedClassName.lastIndexOf( "." );
        String packagePart = preparedClassName.substring( 0, bIndex );
        String classPart = preparedClassName.substring( bIndex + 1 );

        int flags = JMod.PUBLIC;
        if ( isAbstract && type.equals(ClassType.CLASS) ) {
            flags |= JMod.ABSTRACT;
        }

        return codeModel._package( packagePart )
                ._class( flags, classPart, type );
    }

    protected String prepareClassName( String name, String suffix ) {
        if ( suffix == null ) {
            throw new IllegalArgumentException( "<null>" );
        }

        int bIndex = name.lastIndexOf( "." );
        return name.substring( 0, bIndex  )
                + "." + suffix + "." + name.substring( bIndex + 1 );
    }

    protected abstract boolean isSupported( JavaClass entityClass );

    protected abstract void generateClass( JavaClass entityClass ) throws MojoExecutionException;
}
