package com.redshape.generators.jpa.mojo;

import com.redshape.generators.jpa.AbstractGeneratorMojo;
import com.redshape.generators.jpa.utils.Commons;
import com.sun.codemodel.*;
import com.thoughtworks.qdox.model.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cyril on 8/28/13.
 */
@Mojo( name = "gen-dto")
public class GenDtoMojo extends AbstractGeneratorMojo {

    private static final String DTO_EXCLUDE_ANNOTATION_CLASS_NAME = "com.redshape.generators.annotations.dto.DtoExclude";
    private static final String DTO_INCLUDE_ANNOTATION_CLASS_NAME = "com.redshape.generators.annotations.dto.DtoInclude";
    private static final String DTO_EXTENDS_ANNOTATION_CLASS_NAME = "com.redshape.generators.annotations.dto.DtoExtend";
    private static final String DTO_METHOD_ANNOTATION_CLASS_NAME = "com.redshape.generators.annotations.dto.DtoMethod";

    @Parameter( property = "dtoSuffix", required = true, defaultValue = "dto" )
    private String dtoSuffix = "dto";

    @Parameter( property = "dtoInterfaceClass", defaultValue = "java.io.Serializable")
    private String dtoInterfaceClass = "java.io.Serializable";

    @Parameter( property = "generateMethods", defaultValue = "true" )
    private Boolean generateMethods = true;

    public GenDtoMojo() {
        super("DTO generator");
    }

    @Override
    protected void generateClass(JavaClass entityClazz) throws MojoExecutionException {
        try {
            JDefinedClass dtoClazz = defineClass(entityClazz.getFullyQualifiedName(),
                    dtoSuffix, entityClazz.isAbstract());

            if ( isJpaEntity( entityClazz.getSuperJavaClass() ) ) {
                dtoClazz._extends(codeModel.ref(entityClazz.getSuperJavaClass().getFullyQualifiedName()));
            } else {
                dtoClazz._implements( codeModel.ref(dtoInterfaceClass) );
            }

            generateClassFields(dtoClazz, entityClazz);
            processClassAnnotations(dtoClazz, entityClazz);
            processClassMethods(dtoClazz, entityClazz);
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    protected void processClassMethods( JDefinedClass dtoClazz, JavaClass entityClazz ) {
        for (JavaMethod method : entityClazz.getMethods() ) {
            if ( !hasAnnotation(method, DTO_METHOD_ANNOTATION_CLASS_NAME) ) {
                continue;
            }

            generateDtoMethod( dtoClazz, entityClazz, method );
        }
    }

    protected void generateDtoMethod( JDefinedClass dtoClazz, JavaClass entityClazz, JavaMethod method ) {
        JType returnType = convertType( method.getReturnType().getJavaClass(), dtoSuffix );
        if ( returnType == null ) {
            getLog().error("Inconvertible method " + method.getName() + " return type");
            return;
        }

        boolean skip = false;
        Map<String, JType> parameters = new HashMap<String, JType>();
        for ( JavaParameter parameter : method.getParameters() ) {
            JType parameterType = convertType( parameter.getType().getJavaClass(), dtoSuffix );
            if ( parameterType == null ) {
                getLog().error("Inconvertible method " + method.getName() + " parameter " + parameter.getName() );
                skip = true;
                break;
            }

            parameters.put( parameter.getName(), parameterType );
        }

        if ( skip ) {
            return;
        }

        int flags = 0;
        if ( method.isPublic() ) {
            flags |= JMod.PUBLIC;
        }

        if ( method.isAbstract() ) {
            flags |= JMod.ABSTRACT;
        } else if ( method.isFinal() ) {
            flags |= JMod.FINAL;
        }

        if ( method.isStatic() ) {
            flags |= JMod.STATIC;
        }

        JMethod dtoMethod = dtoClazz.method( flags, returnType, method.getName() );
        for ( Map.Entry<String, JType> parameterEntry : parameters.entrySet() ) {
            dtoMethod.param( parameterEntry.getValue(), parameterEntry.getKey() );
        }

        dtoMethod.body().block().directStatement( method.getSourceCode() );
    }
    
    protected void processClassAnnotations( JDefinedClass dtoClazz, JavaClass entityClazz ) {
        for ( Annotation annotation : entityClazz.getAnnotations() ) {
            String annotationTypeName = annotation.getType().getFullyQualifiedName();
            if ( annotationTypeName.equals( DTO_EXTENDS_ANNOTATION_CLASS_NAME) ) {
                processExtendsAnnotation( dtoClazz, entityClazz, annotation );
            }
        }
    }

    protected void processExtendsAnnotation( JDefinedClass dtoClazz, JavaClass entityClazz, Annotation extendsAnnotation ) {
        Object value = extendsAnnotation.getNamedParameter("value");
        if ( value == null ) {
            return;
        }

        if ( value instanceof List ) {
            for ( Annotation parameterAnnotation : (List<Annotation>) value ) {
                processExtendsParameterAnnotation(dtoClazz, parameterAnnotation);
            }
        } else {
            processExtendsParameterAnnotation( dtoClazz, (Annotation) value );
        }
    }

    protected void processExtendsParameterAnnotation( JDefinedClass dtoClazz,Annotation annotation ) {
        String fieldName = normalizeAnnotationValue(
                (String) annotation.getNamedParameter("value"));
        String fieldType = normalizeAnnotationValue(
                (String) annotation.getNamedParameter("type") ).replace(".class", "");
        _generateClassField( dtoClazz, JMod.PRIVATE, codeModel.ref(fieldType), fieldName );
    }

    protected void generateClassFields( JDefinedClass dtoClazz, JavaClass entityClazz ) {
        for ( JavaField field : entityClazz.getFields() ) {
            generateClassField( dtoClazz, entityClazz, field );
        }
    }

    protected void generateClassField( JDefinedClass dtoClazz, JavaClass entityClass, JavaField field ) {
        String fieldName = field.getName();
        String aggregationType = "ID";

        JType fieldType = codeModel.ref( field.getType().getFullyQualifiedName() );
        JType realType = fieldType;

        boolean ignoreField = false;
        boolean isComplexType = isSimpleType( field.getType().getJavaClass() );
        boolean isInclude = false;
        for ( Annotation annotation : field.getAnnotations() ) {
            String annotationTypeName = annotation.getType().getFullyQualifiedName();
            if ( annotationTypeName.equals(DTO_EXCLUDE_ANNOTATION_CLASS_NAME) ) {
                ignoreField = true;
                break;
            } else if ( isJpaRelationType(annotationTypeName) ) {
                isComplexType = true;
                if ( annotation.getNamedParameter("targetType") != null ) {
                    realType = codeModel.ref(
                        normalizeAnnotationValue(
                            (String)annotation.getNamedParameter("targetType")
                        ).replace(".class", "")
                    );
                }
            } else if ( annotationTypeName.equals( DTO_INCLUDE_ANNOTATION_CLASS_NAME ) ) {
                isInclude = true;
                if ( annotation.getNamedParameter("value") != null ) {
                    aggregationType = normalizeAnnotationValue( (String) annotation.getNamedParameter("value") );
                }
            }
        }

        if ( ignoreField
                || ( isComplexType && !isInclude) ) {
            return;
        }

        if ( isComplexType ) {
            if ( aggregationType.equals("AggregationType.ID") ) {
                fieldName += "Id";
            }
        }

        int flags = JMod.PRIVATE;
        if ( field.isTransient() ) {
            flags |= JMod.TRANSIENT;
        }

        _generateClassField( dtoClazz, flags, Commons.select(realType, fieldType), fieldName );
    }

    private void _generateClassField( JDefinedClass dtoClazz, int flags, JType type, String fieldName ) {
        JFieldVar clazzField = dtoClazz.field(flags, type, fieldName );
        generateAccessors(dtoClazz, clazzField);
    }

    @Override
    protected String prepareClassName(String name, String suffix) {
        String className = super.prepareClassName(name, suffix);
        className += "DTO";
        return className;
    }

    @Override
    protected boolean isSupported(JavaClass entityClass) {
        return isJpaEntity(entityClass);
    }
}
