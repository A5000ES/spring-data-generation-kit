package com.redshape.generators.jpa.mojo;

import com.redshape.generators.jpa.AbstractGeneratorMojo;
import com.redshape.generators.jpa.utils.Commons;
import com.sun.codemodel.*;
import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by cyril on 8/28/13.
 */
@Mojo( name = "gen-jpa-converter")
public class GenJpaToDtoConverterMojo extends AbstractGeneratorMojo {

    private static final String CONVERTER_CLASS_NAME = "DtoConversionService";
    private static final String CONVERTER_METHOD_NAME = "convertToDto";
    private static final String DTO_INCLUDE_ANNOTATION_CLASS_NAME = "com.redshape.generators.annotations.dto.DtoInclude";
    private static final String DTO_EXCLUDE_ANNOTATION_CLASS_NAME = "com.redshape.generators.annotations.dto.DtoExclude";
    private static final String METHODS_CACHE_FIELD_NAME = "METHODS";
    private static final String CONVERSATION_METHOD_NOT_FOUND_EXCEPTION = "Conversion method not found: %s";
    private static final String LIST_CONVERTER_METHOD_NAME = "convertToDtoList";

    @Parameter( property = "convertersPackage", required = true )
    private String convertersPackage;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private JDefinedClass converterClazz;
    private JFieldVar cacheField;

    public GenJpaToDtoConverterMojo() {
        super("JPA to DTO conversion services generator", "", "", "");
    }

    protected void init() throws JClassAlreadyExistsException {
        if ( !initialized.compareAndSet(false, true) ) {
            return;
        }

        this.converterClazz = codeModel._package(convertersPackage)
                ._class( JMod.PUBLIC, CONVERTER_CLASS_NAME );
    }
    @Override
    protected void generateClass(JavaClass entityClazz) throws MojoExecutionException {
        if ( entityClazz.isAbstract() ) {
            getLog().info("Skipping abstract class " + entityClazz.getFullyQualifiedName() );
            return;
        }

        generateConverter(entityClazz);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            init();
            generateTemplateConvertMethod(converterClazz);
            generateTemplateListConvertMethod(converterClazz);

            super.execute();
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    protected void generateConverter( JavaClass entityClazz ) {
        JClass entityClazzModel = codeModel.ref( entityClazz.getFullyQualifiedName() );
        JClass dtoRef = codeModel.ref(
            prepareClassName(
                dtoPackage,
                entityClazz.getFullyQualifiedName(),
                DTO_GENERATOR_PREFIX,
                DTO_GENERATOR_SUFFIX,
                DTO_GENERATOR_POSTFIX
            )
        );

        JMethod converterMethod = converterClazz.method(JMod.PUBLIC,
                codeModel.ref(prepareClassName(dtoPackage, entityClazz.getFullyQualifiedName(),
                        DTO_GENERATOR_PREFIX, DTO_GENERATOR_SUFFIX, DTO_GENERATOR_POSTFIX)),
                CONVERTER_METHOD_NAME);

        JVar converterMethodParam = converterMethod.param( entityClazzModel, "value" );

        JBlock block = converterMethod.body();
        JVar dtoInstance = block.decl( dtoRef, "result", JExpr._new(dtoRef) );

        for ( JavaField field : collectAllFields(entityClazz) ) {
            if ( field.isStatic() && skipStaticFields ) {
                continue;
            }

            boolean isConvertible = isConvertibleField(field);
            if ( !isConvertible && !isSimpleType(field.getType().getJavaClass()) ) {
                continue;
            }

            JExpression valueExpr;
            String fieldName;
            String getterName;
            String setterName;
            if ( isConvertible ) {
                boolean dtoAggregationType = false;
                for ( Annotation annotation : field.getAnnotations() ) {
                    if ( !annotation.getType().getFullyQualifiedName().equals( DTO_INCLUDE_ANNOTATION_CLASS_NAME ) ) {
                        continue;
                    }

                    String aggregationType = Commons.select((String) annotation.getNamedParameter("value"),
                            "AggregationType.DTO");
                    dtoAggregationType = aggregationType.equals("AggregationType.DTO");
                }

                if ( dtoAggregationType ) {
                    fieldName = field.getName();

                    JClass resultType;
                    if ( isCollectionType( field.getType().getJavaClass() ) ) {
                        if ( isListType( field.getType().getJavaClass() ) ) {
                            resultType = codeModel.ref( List.class );
                        } else if ( isSetType( field.getType().getJavaClass() ) ) {
                            resultType = codeModel.ref(Set.class);
                        } else {
                            resultType = codeModel.ref(Collection.class);
                        }

                        resultType = resultType.narrow(
                            codeModel.ref(
                                prepareClassName( dtoPackage,
                                        field.getType().getActualTypeArguments()[0].getFullyQualifiedName(),
                                        DTO_GENERATOR_PREFIX, DTO_GENERATOR_SUFFIX, DTO_GENERATOR_POSTFIX )
                            )
                        );
                    } else {
                        resultType = codeModel.ref(
                                prepareClassName(dtoPackage, field.getType().getFullyQualifiedName(),
                                        DTO_GENERATOR_PREFIX, DTO_GENERATOR_SUFFIX, DTO_GENERATOR_POSTFIX)
                        );
                    }

                    valueExpr = JExpr.cast(
                        resultType,
                        JExpr._this().invoke("convertToDto").arg(
                            converterMethodParam.invoke( getterName = generateGetterName(field.getName()) )
                        )
                    );
                } else {
                    fieldName = field.getName() + "Id";
                    valueExpr = converterMethodParam.invoke( getterName = generateGetterName( field.getName() ) )
                            .invoke( generateGetterName("id") );
                }
            } else {
                fieldName = field.getName();
                valueExpr = converterMethodParam.invoke( getterName = generateGetterName(fieldName) );
            }

            setterName = generateSetterName(fieldName);
            if ( !isMethodExists( setterName, dtoRef.fullName() ) ||
                    !isMethodExists(getterName, entityClazz.getFullyQualifiedName()) ) {
                getLog().debug("Skipping field of " + entityClazz.getFullyQualifiedName()
                        + " with no setter and/or getter '" + getterName + "'...");
                continue;
            }

            block.add(
                dtoInstance.invoke( setterName ).arg( valueExpr )
            );
        }

        block._return(dtoInstance);
    }

    /**
     * @param converterClazz
     */
    protected void generateTemplateListConvertMethod( JDefinedClass converterClazz ) {
        JMethod method = converterClazz.method(
            JMod.PUBLIC,
            codeModel.ref(List.class).narrow(Object.class),
            LIST_CONVERTER_METHOD_NAME
        );

        JVar methodParam =
                method.param( codeModel.ref(Collection.class).narrow(Object.class), "records" );

        JBlock methodBody = method.body();
        methodBody._if( methodParam.eq( JExpr._null() ) )
                ._then()
                    ._throw(
                        JExpr._new( codeModel.ref( IllegalStateException.class ) )
                        .arg("<null>") );

        JVar result = methodBody.decl( codeModel.ref(List.class), "result")
                .init( JExpr._new( codeModel.ref(ArrayList.class) ) );
        JForEach recordsIterator = methodBody.forEach( codeModel.ref(Object.class), "record", methodParam );
        recordsIterator.body()
                ._if( recordsIterator.var().eq( JExpr._null() ) )
                    ._then()
                        ._continue();
        recordsIterator.body()
            .invoke( result, "add" )
                .arg(
                    JExpr._this()
                         .invoke(CONVERTER_METHOD_NAME)
                         .arg( recordsIterator.var() )
                );

        methodBody._return( result );
    }

    protected void generateTemplateConvertMethod( JDefinedClass converterClazz ) {
        defineConverterMethodsCache(converterClazz);

        JMethod method = converterClazz.method(JMod.PUBLIC, codeModel.ref("T"), "convertToDto");
        JTypeVar typeVar = method.generify("T");

        JVar methodParam = method.param( codeModel.ref(Object.class), "value" );

        method.body()._if( methodParam.eq( JExpr._null() ) )
            ._then()
                ._return( JExpr._null() );

        JType collectionType = codeModel.ref(Collection.class);

        method.body()._if( methodParam._instanceof( collectionType ) )
            ._then()
                ._return(
                    JExpr.cast( typeVar,
                        JExpr._this().invoke("convertToDtoList")
                            .arg(JExpr.cast(collectionType, methodParam)) )
                );

        JVar methodDeclaration = method.body().decl(
                codeModel.ref(Method.class),
                "method",
                cacheField.invoke("get").arg( methodParam.invoke("getClass") ) );
        method.body()._if(
            methodDeclaration.eq( JExpr._null() ) )
                ._then()._throw(
                    JExpr._new( codeModel.ref(IllegalStateException.class) )
                        .arg( String.format(CONVERSATION_METHOD_NOT_FOUND_EXCEPTION,
                                methodParam.invoke("getClass").invoke("getCanonicalName") ) )
                );

        JTryBlock convertBlock = method.body()._try();
        convertBlock.body()
                ._return(
                    JExpr.cast(
                            typeVar,
                            methodDeclaration.invoke("invoke")
                                    .arg(JExpr._this())
                                    .arg(JExpr.newArray(codeModel.ref(Object.class)).add(methodParam))
                    )
                );

        JCatchBlock covertBlockCatch = convertBlock._catch( codeModel.ref(Exception.class) );
        JVar param = covertBlockCatch.param("e");
        covertBlockCatch.body()
            ._throw(
                JExpr._new( codeModel.ref(IllegalStateException.class) )
                    .arg( param.invoke("getMessage") )
                    .arg( param )
            );

    }

    protected void defineConverterMethodsCache( JDefinedClass converterClazz ) {
        JClass methodType = codeModel.ref(Method.class);
        this.cacheField = converterClazz.field(JMod.STATIC | JMod.FINAL | JMod.PRIVATE,
            codeModel.ref(Map.class).narrow(codeModel.ref(Class.class), methodType),
            METHODS_CACHE_FIELD_NAME,
            JExpr._new(
                codeModel.ref(HashMap.class).narrow(codeModel.ref(Class.class), codeModel.ref(Method.class))
            )
        );

        JForEach initLoop = converterClazz.init()
                .forEach( methodType, "method", converterClazz.staticRef("class").invoke("getMethods") );
        initLoop.body()
            ._if(initLoop.var().invoke("equals").arg(CONVERTER_METHOD_NAME))
                ._then()._continue();

        JInvocation parameterTypes = initLoop.var().invoke("getParameterTypes");
        initLoop.body()
            ._if(
                    JOp.cor(
                            parameterTypes.ref("length").eq(JExpr.lit(0)),
                            JOp.eq(
                                    parameterTypes.component(JExpr.lit(0)),
                                    codeModel.ref(Object.class).staticRef("class")
                            )
                    )
            )
                ._then()._continue();

        initLoop.body()
            .invoke( cacheField, "put")
                .arg( parameterTypes.component( JExpr.lit(0) ) )
                .arg( initLoop.var() );
    }

    protected boolean isConvertibleField( JavaField field ) {
        boolean isComplex = !this.isSimpleType(field.getType().getJavaClass());
        boolean isIncluded = false;
        boolean isExcluded = false;
        for ( Annotation annotation : field.getAnnotations() ) {
            String annotationTypeName = annotation.getType().getFullyQualifiedName();
            if ( annotationTypeName.equals(DTO_INCLUDE_ANNOTATION_CLASS_NAME) ) {
                isIncluded = true;
            } else if ( annotationTypeName.equals(DTO_EXCLUDE_ANNOTATION_CLASS_NAME) ) {
                isExcluded = true;
            }
        }

        return isComplex && isIncluded && !isExcluded;
    }

    @Override
    protected boolean isSupported(JavaClass entityClass) {
        return isJpaEntity(entityClass);
    }
}
