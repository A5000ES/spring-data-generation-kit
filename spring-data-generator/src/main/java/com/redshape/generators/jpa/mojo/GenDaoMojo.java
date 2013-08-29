package com.redshape.generators.jpa.mojo;

import com.redshape.generators.jpa.AbstractGeneratorMojo;
import com.redshape.generators.jpa.utils.Commons;
import com.sun.codemodel.*;
import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.JavaClass;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.*;

/**
 * Created by cyril on 8/28/13.
 */
@Mojo( name = "gen-dao", defaultPhase = LifecyclePhase.GENERATE_SOURCES )
public class GenDaoMojo extends AbstractGeneratorMojo {

    public static final String JPA_REPOSITORY_CLASS_NAME
            = "org.springframework.data.jpa.repository.JpaRepository";

    public static final String JPA_REPOSITORY_ANNOTATION_CLASS_NAME
            = "org.springframework.stereotype.Repository";
    public static final String PARAM_ANNOTATION_CLASS_NAME
            = "org.springframework.data.repository.query.Param";
    public static final String QUERY_ANNOTATION_CLASS_NAME
            = "org.springframework.data.jpa.repository.Query";
    public static final String MODIFYING_ANNOTATION_CLASS_NAME
            = "org.springframework.data.jpa.repository.Modifying";
    public static final String TRANSACTIONAL_ANNOTATION_CLASS_NAME
            = "org.springframework.transaction.annotation.Transactional";

    private static final String NATIVE_QUERIES_ANNOTATION_CLASS_NAME
            = "com.redshape.generators.annotations.NativeQueries";
    private static final String NATIVE_QUERY_ANNOTATION_CLASS_NAME
            = "com.redshape.generators.annotations.NativeQuery";
    private static final String CONVENTIONAL_QUERIES_ANNOTATION_CLASS_NAME
            = "com.redshape.generators.annotations.ConventionalQueries";
    private static final String CONVENTIONAL_QUERY_ANNOTATION_CLASS_NAME
            = "com.redshape.generators.annotations.ConventionalQuery";
    private static final String PAGE_CLASS_NAME
            = "org.springframework.data.domain.Page";
    private static final String PAGEABLE_CLASS_NAME
            = "org.springframework.data.domain.Page";
    private static final String SORT_CLASS_NAME
            = "org.springframework.data.domain.Sort";

    private final Map<String, String> cache = new HashMap<String, String>();

    @Parameter( property = "daoSuffix", defaultValue = "dao")
    private String daoSuffix = "dao";

    public GenDaoMojo() {
        super("Spring Data repositories generator");
    }

    @Override
    protected boolean isSupported(JavaClass entityClass) {
        return isJpaEntity(entityClass);
    }

    @Override
    protected void generateClass(JavaClass entityClazz) throws MojoExecutionException {
        synchronized (entityClazz.getFullyQualifiedName().intern()) {
            if ( cache.containsKey(entityClazz.getFullyQualifiedName()) ) {
                getLog().info("Skipping already processed class " + entityClazz.getFullyQualifiedName() );
                return;
            }

            try {
                JDefinedClass definedClass = defineDaoClass(entityClazz);
                generateQueryMethods(entityClazz, definedClass);
            } catch (JClassAlreadyExistsException e) {
                throw new MojoExecutionException( e.getMessage(), e );
            }
        }
    }

    protected JDefinedClass defineDaoClass( JavaClass entityClazz )
            throws MojoExecutionException, JClassAlreadyExistsException {
        String entityIdClassName = detectIdKeyType(entityClazz);
        if ( entityIdClassName == null && !entityClazz.isAbstract() ) {
            throw new MojoExecutionException("No @Id field found for class: "
                    + entityClazz.getFullyQualifiedName() );
        }

        boolean rootRepository = true;
        String superClassName = JPA_REPOSITORY_CLASS_NAME;
        if ( isJpaEntity( entityClazz.getSuperJavaClass() ) ) {
            generateClass( entityClazz.getSuperJavaClass() );
            superClassName = cache.get( entityClazz.getSuperClass().getFullyQualifiedName() );
            rootRepository = false;
        }

        JDefinedClass daoClass = defineInterface( entityClazz.getFullyQualifiedName(), daoSuffix );
        cache.put( entityClazz.getFullyQualifiedName(), daoClass.fullName() );

        JClass repositoryClass;

        JClass repositoryTypeNarrow = codeModel.ref( entityClazz.getFullyQualifiedName() );
        if ( hasDescendants( entityClazz ) ) {
            daoClass.generify("T", codeModel.ref( entityClazz.getFullyQualifiedName() ) );
            repositoryTypeNarrow = codeModel.ref("T");
        }

        repositoryClass = codeModel.ref(superClassName);
        if ( rootRepository ) {
            repositoryClass = repositoryClass.narrow( repositoryTypeNarrow,
                    codeModel.ref( entityIdClassName ) );
        } else {
            repositoryClass = repositoryClass.narrow( repositoryTypeNarrow );
        }

        daoClass._implements( repositoryClass );

        daoClass.annotate( codeModel.ref(JPA_REPOSITORY_ANNOTATION_CLASS_NAME) );

        return daoClass;
    }

    protected void generateQueryMethods(JavaClass entityClazz, JDefinedClass daoClazz) {
        List<QuerySpec> querySpecList = new ArrayList<QuerySpec>();
        for ( Annotation annotation : entityClazz.getAnnotations() ) {
            if ( !isSupportedAnnotation(annotation.getType().getFullyQualifiedName()) ) {
                continue;
            }

            querySpecList.addAll(processAnnotation(annotation));
        }

        JClass _entityClazz = codeModel.ref( entityClazz.getFullyQualifiedName() );
        for ( QuerySpec spec : querySpecList ) {
            generateQueryMethod( _entityClazz, daoClazz, spec, false, false);

            if ( spec.isPageable ) {
                generateQueryMethod(_entityClazz, daoClazz, spec, true, false);
            }

            if ( spec.isSortable ) {
                generateQueryMethod( _entityClazz, daoClazz, spec, true, false );
            }

            if ( spec.isPageable && spec.isSortable ) {
                generateQueryMethod( _entityClazz, daoClazz, spec, true, true );
            }
        }
    }

    protected void generateQueryMethod(JClass entityClazz, JDefinedClass daoClazz, QuerySpec spec,
                                       boolean appendPagerParam,
                                       boolean appendSortingParam ) {
        JClass returnType = entityClazz;
        if ( appendPagerParam ) {
            returnType = codeModel.ref( PAGE_CLASS_NAME )
                    .narrow( entityClazz );
        } else {
            if ( spec.resultType != null ) {
                returnType = codeModel.ref( spec.resultType );
            } else if ( spec.isCollection ) {
                returnType = codeModel.ref(List.class.getCanonicalName())
                        .narrow( entityClazz );
            }
        }

        JMethod method = daoClazz.method(JMod.PUBLIC, returnType, spec.name );

        JAnnotationUse queryAnnotation = method.annotate( codeModel.ref(QUERY_ANNOTATION_CLASS_NAME) );
        if ( spec.isNative ) {
            queryAnnotation.param("value", spec.value);
        }

        if ( spec.isModifying ) {
            method.annotate( codeModel.ref(MODIFYING_ANNOTATION_CLASS_NAME) );
        }

        if ( spec.isTransactional ) {
            method.annotate( codeModel.ref(TRANSACTIONAL_ANNOTATION_CLASS_NAME) );
        }

        Set<String> processedParaNames = new HashSet<String>();
        for ( QuerySpecParam param : spec.parameters ) {
            if ( param.name == null ) {
                param.name = selectNonConflictingName(spec.parameters, "param");
            }

            if ( processedParaNames.contains(param.name) ) {
                param.name = selectNonConflictingName(spec.parameters, param.name);
            } else {
                processedParaNames.add( param.name );
            }

            JVar methodParam = method.param( codeModel.ref( param.isArray ? param.type + "[]" : param.type),
                    param.name );
            JAnnotationUse paramAnnotation = methodParam.annotate( codeModel.ref(PARAM_ANNOTATION_CLASS_NAME) );
            paramAnnotation.param("value", param.name );
        }

        if ( appendPagerParam ) {
            method.param( codeModel.ref(PAGEABLE_CLASS_NAME),
                    selectNonConflictingName( spec.parameters, "pageable" ) );
        }

        if ( appendSortingParam ) {
            method.param( codeModel.ref(SORT_CLASS_NAME),
                    selectNonConflictingName( spec.parameters, "sort" ) );
        }
    }

    protected List<QuerySpec> processAnnotation( Annotation annotation ) {
        List<QuerySpec> querySpec = new ArrayList<QuerySpec>();
        if ( isAnnotationsList(annotation.getType().getFullyQualifiedName()) ) {
            Object value = annotation.getNamedParameter("value");
            if ( value instanceof List ) {
                for ( Annotation childAnnotation : (List<Annotation>) value) {
                    querySpec.add( createQuerySpec( childAnnotation ) );
                }
            } else {
               querySpec.add(createQuerySpec((Annotation) value));
            }
        } else {
            querySpec.add( createQuerySpec(annotation) );
        }

        return querySpec;
    }

    protected QuerySpec createQuerySpec( Annotation annotation ) {
        QuerySpec spec = new QuerySpec();
        spec.name = normalizeAnnotationValue(
                Commons.select((String) annotation.getNamedParameter("name"), "") );
        spec.isNative = isNativeQuery(annotation);
        spec.resultType = normalizeAnnotationValue(
                Commons.select((String) annotation.getNamedParameter("resultType"), "") )
                    .replace(".class", "");
        if ( spec.resultType.isEmpty() ) {
            spec.resultType = null;
        }
        if ( spec.isNative ) {
            spec.value = normalizeAnnotationValue(
                Commons.select( (String) annotation.getNamedParameter("value"), "" )
            );
        }

        spec.isModifying = Boolean.valueOf(
                Commons.select( (String) annotation.getNamedParameter("isModifying"), "false") ) ;
        spec.isSortable = Boolean.valueOf(
                Commons.select( (String) annotation.getNamedParameter("isSortable"), "false") ) ;
        spec.isTransactional = Boolean.valueOf(
                Commons.select((String) annotation.getNamedParameter("isTransactional"), "false"));
        spec.isCollection = Boolean.valueOf(
                Commons.select((String) annotation.getNamedParameter("isCollection"), "true"));
        spec.isPageable = Boolean.valueOf(
                Commons.select((String) annotation.getNamedParameter("isPageable"), "false"));

        Object parametersValue = Commons.select( annotation.getNamedParameter("parameters"),
                new ArrayList<Annotation>() );
        if ( parametersValue instanceof List ) {
            for ( Annotation parameterAnnotation : (List<Annotation>) parametersValue ) {
                spec.parameters.add( createQuerySpecParam(parameterAnnotation) );
            }
        } else {
            spec.parameters.add( createQuerySpecParam((Annotation) parametersValue) );
        }

        return spec;
    }

    protected QuerySpecParam createQuerySpecParam( Annotation annotation ) {
        QuerySpecParam result = new QuerySpecParam();
        result.isArray = Boolean.valueOf(Commons.select((String) annotation.getNamedParameter("isArray"), "false"));
        result.name = normalizeAnnotationValue(Commons.select((String) annotation.getNamedParameter("value"), ""));
        result.type = normalizeAnnotationValue(
                Commons.select((String) annotation.getNamedParameter("type"), "") ).replace(".class", "");

        return result;
    }

    protected boolean isNativeQuery( Annotation annotation ) {
        return annotation.getType().getJavaClass().isA(NATIVE_QUERY_ANNOTATION_CLASS_NAME);
    }

    protected boolean isAnnotationsList( String annotationClassName  ) {
        return annotationClassName.equals( NATIVE_QUERIES_ANNOTATION_CLASS_NAME )
                || annotationClassName.equals( CONVENTIONAL_QUERIES_ANNOTATION_CLASS_NAME );
    }

    protected boolean isSupportedAnnotation( String annotationClassName ) {
        return annotationClassName.equals(NATIVE_QUERIES_ANNOTATION_CLASS_NAME)
                || annotationClassName.equals(NATIVE_QUERY_ANNOTATION_CLASS_NAME)
                || annotationClassName.equals(CONVENTIONAL_QUERIES_ANNOTATION_CLASS_NAME)
                || annotationClassName.equals(CONVENTIONAL_QUERY_ANNOTATION_CLASS_NAME);
    }

    protected String selectNonConflictingName( List<QuerySpecParam> parameters, String name ) {
        int i = 0;
        boolean conflicting = false;
        do {
            for ( QuerySpecParam param : parameters ) {
                if ( param.name.equals(name) ) {
                    conflicting = true;
                    break;
                }
            }

            if ( conflicting ) {
                name += i++;
                conflicting = false;
            }
        } while ( conflicting );

        return name;
    }

    @Override
    protected String prepareClassName(String name, String suffix) {
        String className = super.prepareClassName(name, suffix);
        className = className + "DAO";
        return className;
    }

    public class QuerySpec {
        boolean isCollection;
        boolean isNative;
        boolean isPageable;
        boolean isTransactional;
        boolean isSortable;
        boolean isModifying;

        String name;
        String value;
        String resultType;

        List<QuerySpecParam> parameters = new ArrayList<QuerySpecParam>();
    }

    public class QuerySpecParam {
        boolean isArray;
        String name;
        String type;
    }

}
