package com.redshape.generators.jpa.mojo;

import com.redshape.generators.jpa.AbstractGeneratorMojo;
import com.redshape.generators.jpa.utils.Commons;
import com.sun.codemodel.*;
import com.thoughtworks.qdox.model.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.*;

/**
 * Created by cyril on 8/28/13.
 */
@Mojo( name = "gen-dto")
public class GenDtoMojo extends AbstractGeneratorMojo {

    @Parameter( property = "dtoInterfaceClass", defaultValue = "java.io.Serializable")
    private String dtoInterfaceClass = "java.io.Serializable";

    @Parameter( property = "generateMethods", defaultValue = "true" )
    private Boolean generateMethods = true;

    public GenDtoMojo() {
        super("DTO generator", DTO_GENERATOR_PREFIX, DTO_GENERATOR_SUFFIX, DTO_GENERATOR_POSTFIX);
    }

    @Override
    protected void generateClass(JavaClass entityClazz) throws MojoExecutionException {
        try {
            JDefinedClass dtoClazz = defineClass(entityClazz.getFullyQualifiedName(), dtoPackage,
                    entityClazz.isAbstract());

            if ( entityClazz.getSuperClass() != null && isJpaEntity( entityClazz.getSuperJavaClass() ) ) {
                JClass parentClass = codeModel.ref(
                        prepareClassName(
                            dtoPackage, entityClazz.getSuperJavaClass().getFullyQualifiedName()
                        )
                    );

                Type[] actualTypeArguments = entityClazz.getSuperClass().getActualTypeArguments();
                if ( actualTypeArguments != null && actualTypeArguments.length > 0 ) {
                    for ( Type narrow : actualTypeArguments ) {
                        parentClass = parentClass.narrow(
                            codeModel.ref(
                                prepareClassName( dtoPackage, narrow.getFullyQualifiedName() )
                            )
                        );
                    }
                }

                dtoClazz = dtoClazz._extends(parentClass);
            } else {
                dtoClazz._implements(codeModel.ref(dtoInterfaceClass));
            }

            if ( entityClazz.getTypeParameters().length > 0 ) {
                for ( TypeVariable type : entityClazz.getTypeParameters() ) {
                    dtoClazz.generify(
                        type.getName(),
                        codeModel.ref( prepareClassName(dtoPackage, type.getValue() ) )
                    );
                }
            }

            generateClassFields(dtoClazz, entityClazz);
            processClassAnnotations(dtoClazz, entityClazz);
            processClassMethods(dtoClazz, entityClazz);
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    protected void processClassMethods( JDefinedClass dtoClazz, JavaClass entityClazz ) {
        if ( !generateMethods ) {
            return;
        }

        for (JavaMethod method : entityClazz.getMethods() ) {
            if ( !hasAnnotation(method, DTO_METHOD_ANNOTATION_CLASS_NAME) ) {
                continue;
            }

            getLog().info("Generating DTO method " + method.getName()
                    + " for DTO " + dtoClazz.fullName() );
            generateDtoMethod(dtoClazz, entityClazz, method);
        }
    }

    protected void generateDtoMethod( JDefinedClass dtoClazz, JavaClass entityClazz, JavaMethod method ) {
        JType returnType = convertType( entityClazz, method.getReturnType() );
        if ( returnType == null ) {
            getLog().error("Inconvertible method " + method.getName() + " return type");
            return;
        }

        boolean skip = false;
        Map<String, JType> parameters = new HashMap<String, JType>();
        for ( JavaParameter parameter : method.getParameters() ) {
            JType parameterType = convertType( entityClazz, parameter.getType() );
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

        if ( !method.isAbstract() ) {
            dtoMethod.body().block().directStatement(method.getSourceCode());
        }
    }
    
    protected void processClassAnnotations( JDefinedClass dtoClazz, JavaClass entityClazz ) {
        for ( Annotation annotation : entityClazz.getAnnotations() ) {
            if ( isA( annotation.getType().getJavaClass(),
                    DTO_EXTENDS_ANNOTATION_CLASS_NAME) ) {
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
        _generateClassField( dtoClazz, JMod.PRIVATE, codeModel.ref(fieldType), null, fieldName );
    }

    protected void generateClassFields( JDefinedClass dtoClazz, JavaClass entityClazz ) {
        for ( JavaField field : entityClazz.getFields() ) {
            if ( field.isStatic() && skipStaticFields ) {
                continue;
            }

            generateClassField( dtoClazz, entityClazz, field );
        }
    }

    protected void generateClassField( JDefinedClass dtoClazz, JavaClass entityClass, JavaField field ) {
        String fieldName = field.getName();
        String aggregationType = "ID";

        JClass fieldType = codeModel.ref(field.getType().getFullyQualifiedName() );

        JClass realType = null;

        boolean ignoreField = false;
        boolean isComplexType = !isSimpleType( field.getType().getJavaClass() );
        boolean isInclude = false;
        for ( Annotation annotation : field.getAnnotations() ) {
            if ( isA(annotation.getType().getJavaClass(), DTO_EXCLUDE_ANNOTATION_CLASS_NAME) ) {
                ignoreField = true;
                break;
            } else if ( isJpaRelationType(annotation.getType().getJavaClass()) ) {
                isComplexType = true;
                if ( annotation.getNamedParameter("targetEntity") != null ) {
                    String className = normalizeAnnotationValue(
                            (String)annotation.getNamedParameter("targetEntity") )
                        .replace(".class", "");
                    // Whe have class reference where package part has been reduced by JavaDocBuilder
                    // which happens in a cases when two classes (reference and referent) exists in a
                    // same package
                    if ( !className.contains(".") ) {
                        className = entityClass.getPackageName() + "." + className;
                    }

                    realType = codeModel.ref(
                        prepareClassName(dtoPackage, className)
                    );
                }

                if ( isCollectionType( field.getType().getJavaClass() ) )  {
                    realType = codeModel.ref(field.getType().getFullyQualifiedName())
                            .narrow( Commons.select(realType, fieldType) );
                }
            } else if ( isA( annotation.getType().getJavaClass(), DTO_INCLUDE_ANNOTATION_CLASS_NAME ) ) {
                isInclude = true;
                if ( annotation.getNamedParameter("value") != null ) {
                    aggregationType = normalizeAnnotationValue(
                            (String) annotation.getNamedParameter("value") );
                }
            }
        }

        if ( ignoreField
                || ( isComplexType && !isInclude) ) {
            return;
        }

        if ( isComplexType ) {
            if ( !isCollectionType( fieldType.fullName() ) ) {
                fieldType = codeModel.ref( prepareClassName( dtoPackage, fieldType.fullName() ) );

                if ( aggregationType.equals("AggregationType.ID") ) {
                    fieldName += "Id";
                    realType = codeModel.ref( Long.class );
                }
            } else {
                fieldType = fieldType.narrow(
                    codeModel.ref(
                        prepareClassName( dtoPackage,
                                field.getType().getActualTypeArguments()[0].getFullyQualifiedName() )
                    )
                );
            }
        }

        int flags = JMod.PRIVATE;
        if ( field.isTransient() ) {
            flags |= JMod.TRANSIENT;
        }

        if ( field.isStatic() ) {
            flags |= JMod.STATIC;
        }

        String initializeExpression = null;
        if ( field.isFinal() ) {
            flags |= JMod.FINAL;
            initializeExpression = field.getInitializationExpression();
        }

        _generateClassField( dtoClazz, flags, Commons.select(realType, fieldType), initializeExpression,
                fieldName );
    }

    private void _generateClassField( JDefinedClass dtoClazz, int flags,
                                      JClass type, String initializeExpression,
                                      String fieldName ) {
        JFieldVar clazzField = dtoClazz.field(flags, type, fieldName );

        if ( type.isInterface() ) {
            if ( isSetType(type.erasure().fullName()) ) {
                clazzField.init( JExpr._new( codeModel.ref(HashSet.class) ) );
            } else if ( isListType(type.erasure().fullName()) ) {
                clazzField.init( JExpr._new( codeModel.ref(ArrayList.class) ) );
            } else if ( isCollectionType(type.erasure().fullName()) ) {
                clazzField.init( JExpr._new( codeModel.ref(HashSet.class) ) );
            }
        } else if ( isCollectionType(type.fullName()) ) {
            clazzField.init( JExpr._new( type ) );
        }

        if ( (flags & JMod.STATIC) == 0 ) {
            generateAccessors(dtoClazz, clazzField);
        } else if ( initializeExpression != null ) {
            clazzField.init( JExpr.direct(initializeExpression) );
        }
    }

    @Override
    protected boolean isSupported(JavaClass entityClass) {
        return isJpaEntity(entityClass);
    }
}
